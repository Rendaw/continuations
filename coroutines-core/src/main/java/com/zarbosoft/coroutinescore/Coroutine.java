/*
 * Copyright (c) 2008, Matthias Mann
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.zarbosoft.coroutinescore;

import com.zarbosoft.coroutinescore.instrument.Stack;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * <p>A Coroutine is used to run a CoroutineProto.</p>
 * <p>It also provides a function to suspend a running Coroutine.</p>
 * <p>A Coroutine can be serialized if it's not running and all involved
 * classes and data types are also {@link Serializable}.</p>
 *
 * @author Matthias Mann
 */
public class Coroutine implements Runnable, Serializable {

	/**
	 * Default stack size for the data stack.
	 *
	 * @see #Coroutine(CoroutineProto, int)
	 */
	public static final int DEFAULT_STACK_SIZE = 16;

	private static final long serialVersionUID = 2783452871536981L;

	public enum State {
		/**
		 * The Coroutine has not yet been executed
		 */
		NEW,
		/**
		 * The Coroutine is currently executing
		 */
		RUNNING,
		/**
		 * The Coroutine has suspended it's execution
		 */
		SUSPENDED,
		/**
		 * The Coroutine has finished it's run method
		 *
		 * @see SuspendableRunnable#run()
		 */
		FINISHED
	}

	public final SuspendableRunnable runnable;
	private final Stack stack;
	private State state;

	/**
	 * Call from within an executing coroutine to suspend execution at that point - may not be called from outside
	 * the coroutine.
	 *
	 * @throws SuspendExecution                This exception is used for control transfer - don't catch it !
	 * @throws java.lang.IllegalStateException If not called from a Coroutine
	 */
	public static void yield() throws SuspendExecution, IllegalStateException {
		throw new Error("Calling function not instrumented");
	}

	/**
	 * Creates a new Coroutine from the given CoroutineProto. A CoroutineProto
	 * can be used in several Coroutines at the same time - but then the normal
	 * multi threading rules apply to the member state.
	 *
	 * @param runnable the CoroutineProto for the Coroutine.
	 */
	public Coroutine(final SuspendableRunnable runnable) {
		this(runnable, DEFAULT_STACK_SIZE);
	}

	/**
	 * Creates a new Coroutine from the given CoroutineProto. A CoroutineProto
	 * can be used in several Coroutines at the same time - but then the normal
	 * multi threading rules apply to the member state.
	 *
	 * @param runnable  the CoroutineProto for the Coroutine.
	 * @param stackSize the initial stack size for the data stack
	 * @throws NullPointerException     when runnable is null
	 * @throws IllegalArgumentException when stackSize is &lt;= 0
	 */
	public Coroutine(final SuspendableRunnable runnable, final int stackSize) {
		this.runnable = runnable;
		this.stack = new Stack(this, stackSize);
		this.state = State.NEW;

		if (runnable == null) {
			throw new NullPointerException("runnable");
		}
		assert isInstrumented(runnable) : "Not instrumented";
	}

	/**
	 * Returns the active Coroutine on this thread or NULL if no coroutine is running.
	 *
	 * @return the active Coroutine on this thread or NULL if no coroutine is running.
	 */
	public static Coroutine getActiveCoroutine() {
		final Stack s = Stack.getStack();
		if (s != null) {
			return s.co;
		}
		return null;
	}

	/**
	 * <p>Returns the current state of this Coroutine. May be called by the Coroutine
	 * itself but should not be called by another thread.</p>
	 * <p>The Coroutine starts in the state NEW then changes to RUNNING. From
	 * RUNNING it may change to FINISHED or SUSPENDED. SUSPENDED can only change
	 * to RUNNING by calling run() again.</p>
	 *
	 * @return The current state of this Coroutine
	 * @see #run()
	 */
	public State getState() {
		return state;
	}

	/**
	 * Start or resume the coroutine - the coroutine must be in the states NEW or SUSPENDED.  The coroutine
	 * code will execute in the current thread.  This function blocks until the coroutine is finished or suspended.
	 */
	public void run() {
		if (state != State.NEW && state != State.SUSPENDED) {
			throw new AssertionError("Coroutine is not new or suspended.");
		}
		State result = State.FINISHED;
		final Stack oldStack = Stack.getStack();
		try {
			state = State.RUNNING;
			Stack.setStack(stack);
			try {
				runnable.run();
			} catch (final SuspendExecution ex) {
				assert ex == SuspendExecution.instance;
				result = State.SUSPENDED;
				stack.resumeStack();
			}
		} finally {
			Stack.setStack(oldStack);
			state = result;
		}
	}

	private void writeObject(final java.io.ObjectOutputStream out) throws IOException {
		if (state == State.RUNNING) {
			throw new IllegalStateException("trying to serialize a running coroutine");
		}
		out.defaultWriteObject();
	}

	private boolean isInstrumented(final SuspendableRunnable proto) {
		try {
			final Class clz = Class.forName("com.zarbosoft.coroutinescore.instrument.AlreadyInstrumented");
			return proto.getClass().isAnnotationPresent(clz);
		} catch (final ClassNotFoundException ex) {
			return true;   // can't check
		} catch (final Throwable ex) {
			return true;    // it's just a check - make sure we don't fail if something goes wrong
		}
	}

	/**
	 * Extracts and rethrows SuspendExecutions that originate from inside reflect calls.  Automatically swapped in
	 * in instrumentation.
	 *
	 * @param method
	 * @param target
	 * @param arguments
	 * @return
	 * @throws IllegalAccessException
	 * @throws SuspendExecution
	 * @throws InvocationTargetException
	 */
	public static Object reflectInvoke(
			final Method method, final Object target, final Object... arguments
	) throws IllegalAccessException, SuspendExecution, InvocationTargetException {
		try {
			return method.invoke(target, arguments);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof SuspendExecution)
				throw (SuspendExecution) e.getCause();
			else
				throw e;
		}
	}
}
