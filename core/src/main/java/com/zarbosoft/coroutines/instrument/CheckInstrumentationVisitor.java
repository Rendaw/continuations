/*
 * Copyright (c) 2008-2013, Matthias Mann
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
package com.zarbosoft.coroutines.instrument;

import com.zarbosoft.coroutines.SuspendExecution;
import org.objectweb.asm.*;

/**
 * Check if a class contains suspendable methods.
 * Basicly this class checks if a method is declared to throw {@link SuspendExecution}.
 *
 * @author Matthias Mann
 */
public class CheckInstrumentationVisitor extends ClassVisitor {

	static final String EXCEPTION_NAME = Type.getInternalName(SuspendExecution.class);
	static final String EXCEPTION_DESC = Type.getDescriptor(SuspendExecution.class);

	private String className;
	private MethodDatabase.ClassEntry classEntry;
	private boolean hasSuspendable;
	private boolean alreadyInstrumented;

	public CheckInstrumentationVisitor() {
		super(Opcodes.ASM4);
	}

	public boolean needsInstrumentation() {
		return hasSuspendable;
	}

	MethodDatabase.ClassEntry getClassEntry() {
		return classEntry;
	}

	public String getName() {
		return className;
	}

	public boolean isAlreadyInstrumented() {
		return alreadyInstrumented;
	}

	@Override
	public void visit(
			final int version,
			final int access,
			final String name,
			final String signature,
			final String superName,
			final String[] interfaces
	) {
		this.className = name;
		this.classEntry = new MethodDatabase.ClassEntry(superName);
	}

	@Override
	public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
		if (desc.equals(InstrumentClass.ALREADY_INSTRUMENTED_NAME)) {
			alreadyInstrumented = true;
		}
		return null;
	}

	@Override
	public MethodVisitor visitMethod(
			final int access, final String name, final String desc, final String signature, final String[] exceptions
	) {
		final boolean suspendable = checkExceptions(exceptions);
		if (suspendable) {
			hasSuspendable = true;
			// synchronized methods can't be made suspendable
			if ((access & Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED) {
				throw new UnableToInstrumentException("synchronized method", className, name, desc);
			}
		}
		classEntry.set(name, desc, suspendable);
		return null;
	}

	public static boolean checkExceptions(final String[] exceptions) {
		if (exceptions != null) {
			for (final String ex : exceptions) {
				if (ex.equals(EXCEPTION_NAME)) {
					return true;
				}
			}
		}
		return false;
	}
}
