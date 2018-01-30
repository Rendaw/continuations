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

/**
 * <p>A Coroutine is used to run a SuspendableRunnable.</p>
 * <p>It also provides a function to suspend a running Coroutine.</p>
 * <p>A Coroutine can be serialized if it's not running and all involved
 * classes and data types are also {@link Serializable}.</p>
 *
 * @author Matthias Mann
 */
public class Coroutine implements Runnable, Serializable {

	public static class Error extends RuntimeException {

		public Error(final String s) {
			super(s);
		}
	}

	/**
	 * Default stack size for the data stack.
	 *
	 * @see #Coroutine(SuspendableRunnable, int)
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
		throw new Error("Calling function not instrumented or yield was called with the wrong owner class.");
	}

	/**
	 * Creates a new Coroutine from the given SuspendableRunnable. A SuspendableRunnable
	 * can be used in several Coroutines at the same time - but then the normal
	 * multi threading rules apply to the member state.
	 *
	 * @param runnable the SuspendableRunnable for the Coroutine.
	 */
	public Coroutine(final SuspendableRunnable runnable) {
		this(runnable, DEFAULT_STACK_SIZE);
	}

	/**
	 * Creates a new Coroutine from the given SuspendableRunnable. A SuspendableRunnable
	 * can be used in several Coroutines at the same time - but then the normal
	 * multi threading rules apply to the member state.
	 *
	 * @param runnable  the SuspendableRunnable for the Coroutine.
	 * @param stackSize the initial stack size for the data stack
	 */
	public Coroutine(final SuspendableRunnable runnable, final int stackSize) {
		this.runnable = runnable;
		this.stack = new Stack(this, stackSize);
		this.state = State.NEW;
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
			throw new Error("Coroutine is not new or suspended.");
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
			throw new Error("Running coroutines may not be serialized");
		}
		out.defaultWriteObject();
	}
}
