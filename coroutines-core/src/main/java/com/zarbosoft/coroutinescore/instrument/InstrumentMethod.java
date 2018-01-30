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
package com.zarbosoft.coroutinescore.instrument;

import com.zarbosoft.coroutinescore.SuspendExecution;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Instrument a method to allow suspension
 *
 * @author Matthias Mann
 */
public class InstrumentMethod {

	private static final String STACK_NAME = Type.getInternalName(Stack.class);

	private final MethodDatabase db;
	private final String className;
	private final MethodNode mn;
	Map<AbstractInsnNode, Frame> invokeSpecialFrames = new HashMap<>();
	private final int lvarStack;
	private final int firstLocal;

	private final List<Suspension> suspensions = new ArrayList<>();
	private final List<TryCatchBlockNode> reflectExceptRanges = new ArrayList<>();
	private int additionalLocals;

	private boolean warnedAboutMonitors;
	private int warnedAboutBlocking;

	private static final BlockingMethod BLOCKING_METHODS[] = {
			new BlockingMethod("java/lang/Thread", "sleep", "(J)V", "(JI)V"),
			new BlockingMethod("java/lang/Thread", "join", "()V", "(J)V", "(JI)V"),
			new BlockingMethod("java/lang/Object", "wait", "()V", "(J)V", "(JI)V"),
			new BlockingMethod("java/util/concurrent/locks/Lock", "lock", "()V"),
			new BlockingMethod("java/util/concurrent/locks/Lock", "lockInterruptibly", "()V"),
	};

	public InstrumentMethod(
			final MethodDatabase db, final String className, final MethodNode mn
	) {
		this.db = db;
		this.className = className;
		this.mn = mn;

		this.lvarStack = mn.maxLocals;
		this.firstLocal = ((mn.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) ? 0 : 1;
	}

	/**
	 * Meta instructions don't generate bytecode
	 *
	 * @param inst
	 * @return
	 */
	private static boolean isMetaInst(final AbstractInsnNode inst) {
		return inst instanceof LabelNode || inst instanceof LineNumberNode || inst instanceof FrameNode;
	}

	public boolean collectCodeBlocks() throws AnalyzerException {
		final Frame[] frames;
		{
			final Analyzer a = new TypeAnalyzer(db);
			try {
				frames = a.analyze(className, mn);
			} catch (final UnsupportedOperationException ex) {
				throw new AnalyzerException(null, ex.getMessage(), ex);
			}
		}

		// Check instructions, find suspending nodes
		for (int i = 0; i < mn.instructions.size(); ++i) {
			final Frame f = frames[i];
			if (f == null)
				continue; // reachable ?

			final MethodInsnNode node;
			{
				final AbstractInsnNode node1 = mn.instructions.get(i);
				if (node1.getType() != AbstractInsnNode.METHOD_INSN)
					continue;
				node = (MethodInsnNode) node1;
			}

			// Find invoke special to associate frame
			if (node.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(((MethodInsnNode) node).name)) {
				invokeSpecialFrames.put(node, f);
				continue;
			}

			// Find suspending node
			final int opcode = node.getOpcode();
			final boolean isReflectInvoke = "java/lang/reflect/Method".equals(node.owner) && "invoke".equals(node.name);
			if (isReflectInvoke ||
					db.isMethodSuspendable(node.owner,
							node.name,
							node.desc,
							opcode == INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC
					)) {
				db.log(LogLevel.DEBUG,
						"Method call at instruction %d to %s#%s%s is suspendable",
						i,
						node.owner,
						node.name,
						node.desc
				);
				suspensions.add(new Suspension(f, firstLocal, node, mn.instructions, db, isReflectInvoke));
				continue;
			}

			// Warn about blocking calls
			{
				final int blockingId = isBlockingCall(node);
				if (blockingId >= 0) {
					final int mask = 1 << blockingId;
					if (!db.isAllowBlocking()) {
						throw new UnableToInstrumentException("blocking call to " +
								node.owner +
								"#" +
								node.name +
								node.desc, className, mn.name, mn.desc);
					} else if ((warnedAboutBlocking & mask) == 0) {
						warnedAboutBlocking |= mask;
						db.log(LogLevel.WARNING,
								"Method %s#%s%s contains potentially blocking call to " +
										node.owner +
										"#" +
										node.name +
										node.desc,
								className,
								mn.name,
								mn.desc
						);
					}
				}
			}
		}
		if (suspensions.isEmpty())
			return false;

		// Split exception ranges around suspending nodes
		// Modifies the instruction + try catch block node lists so done after initial scan
		{
			final ListIterator<TryCatchBlockNode> rangeListPosition = mn.tryCatchBlocks.listIterator();
			while (rangeListPosition.hasNext()) {
				final TryCatchBlockNode range = rangeListPosition.next();
				final LabelNode originalEnd = range.end;
				AbstractInsnNode at = range.start;

				class AdjustRanges {
					TryCatchBlockNode useBlock = range;
					AbstractInsnNode start;
					int nonMetaCount = 0;

					public AdjustRanges(final AbstractInsnNode start) {
						this.start = start;
					}

					public void finishRange(final AbstractInsnNode suspension) {
						final LabelNode endLabel = new LabelNode();
						mn.instructions.insertBefore(suspension, endLabel);
						finishRange(endLabel);
						start = suspension.getNext(); // skip suspension
						nonMetaCount = 0;
					}

					public void finishRange(final LabelNode endLabel) {
						if (useBlock == null) {
							final LabelNode startLabel = new LabelNode();
							mn.instructions.insertBefore(start, startLabel);
							rangeListPosition.add(new TryCatchBlockNode(startLabel,
									endLabel,
									range.handler,
									range.type
							));
						} else {
							useBlock.end = endLabel;
							useBlock = null;
						}
					}
				}
				final AdjustRanges adjustRanges = new AdjustRanges(at);

				final Iterator<Suspension> suspendingPosition = suspensions.iterator();
				while (at != originalEnd && suspendingPosition.hasNext()) {
					final Suspension nextSuspension = suspendingPosition.next();

					// Find the suspending node
					for (; at != originalEnd; at = at.getNext()) {
						if (isMetaInst(at))
							continue;
						if (at == nextSuspension.node)
							break;
						adjustRanges.nonMetaCount += 1;
					}
					if (at != nextSuspension.node)
						break;

					if (nextSuspension.isReflective) {
						reflectExceptRanges.add(new TryCatchBlockNode(nextSuspension.reflectStart,
								nextSuspension.reflectEnd,
								nextSuspension.reflectExceptHandle,
								Type.getInternalName(InvocationTargetException.class)
						));
					}
				}
			}
		}

		return true;
	}

	private static int isBlockingCall(final MethodInsnNode ins) {
		for (int i = 0, n = BLOCKING_METHODS.length; i < n; i++) {
			if (BLOCKING_METHODS[i].match(ins)) {
				return i;
			}
		}
		return -1;
	}

	public void accept(final MethodVisitor mv) {
		db.log(LogLevel.INFO, "Instrumenting method %s%s%s", className, mn.name, mn.desc);

		mv.visitCode();

		final Label lMethodStart = new Label();
		final Label lMethodEnd = new Label();
		final Label lCatchSEE = new Label();

		// Output setup code
		mv.visitTryCatchBlock(lMethodStart, lMethodEnd, lCatchSEE, CheckInstrumentationVisitor.EXCEPTION_NAME);

		for (final TryCatchBlockNode tcb : reflectExceptRanges) {
			tcb.accept(mv);
		}
		for (final TryCatchBlockNode tcb : mn.tryCatchBlocks) {
			if (CheckInstrumentationVisitor.EXCEPTION_NAME.equals(tcb.type)) {
				throw new UnableToInstrumentException("catch for " + SuspendExecution.class.getSimpleName(),
						className,
						mn.name,
						mn.desc
				);
			}
			tcb.accept(mv);
		}

		if (mn.visibleParameterAnnotations != null) {
			dumpParameterAnnotations(mv, mn.visibleParameterAnnotations, true);
		}

		if (mn.invisibleParameterAnnotations != null) {
			dumpParameterAnnotations(mv, mn.invisibleParameterAnnotations, false);
		}

		if (mn.visibleAnnotations != null) {
			for (final Object o : mn.visibleAnnotations) {
				final AnnotationNode an = (AnnotationNode) o;
				an.accept(mv.visitAnnotation(an.desc, true));
			}
		}

		final Label lCatchAll = new Label();
		mv.visitTryCatchBlock(lMethodStart, lMethodEnd, lCatchAll, null);

		mv.visitMethodInsn(Opcodes.INVOKESTATIC, STACK_NAME, "getStack", "()L" + STACK_NAME + ";");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ASTORE, lvarStack);

		mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "nextMethodEntry", "()I");
		final Label[] lMethodCalls = new Label[suspensions.size()];
		for (int i = 0; i < suspensions.size(); ++i) {
			lMethodCalls[i] = new Label();
		}
		mv.visitTableSwitchInsn(1,
				suspensions.size(),
				lMethodStart,
				lMethodCalls
		); // 0 is the default value, so skip it

		mv.visitLabel(lMethodStart);

		// Output body + suspend/resumes
		class OutputNodesBetween {
			public void go(final AbstractInsnNode start, final AbstractInsnNode end) {
				for (AbstractInsnNode node = start; node != end; node = node.getNext()) {
					switch (node.getOpcode()) {
						case Opcodes.RETURN:
						case Opcodes.ARETURN:
						case Opcodes.IRETURN:
						case Opcodes.LRETURN:
						case Opcodes.FRETURN:
						case Opcodes.DRETURN:
							emitPopMethod(mv);
							break;

						case Opcodes.MONITORENTER:
						case Opcodes.MONITOREXIT:
							if (!db.isAllowMonitors()) {
								throw new UnableToInstrumentException("synchronisation", className, mn.name, mn.desc);
							} else if (!warnedAboutMonitors) {
								warnedAboutMonitors = true;
								db.log(LogLevel.WARNING,
										"Method %s#%s%s contains synchronisation",
										className,
										mn.name,
										mn.desc
								);
							}
							break;

						case Opcodes.INVOKESPECIAL:
							final MethodInsnNode min = (MethodInsnNode) node;
							if ("<init>".equals(min.name)) {
								final int argSize = TypeAnalyzer.getNumArguments(min.desc);
								final Frame frame = invokeSpecialFrames.get(min);
								final int stackIndex = frame.getStackSize() - argSize - 1;
								final Value thisValue = frame.getStack(stackIndex);
								if (stackIndex >= 1 &&
										isNewValue(thisValue, true) &&
										isNewValue(frame.getStack(stackIndex - 1), false)) {
									final NewValue newValue = (NewValue) thisValue;
									if (newValue.omitted) {
										emitNewAndDup(mv, frame, stackIndex, min);
									}
								} else {
									db.log(LogLevel.WARNING,
											"Expected to find a NewValue on stack index %d: %s",
											stackIndex,
											frame
									);
								}
							}
							break;
					}
					node.accept(mv);
				}
			}
		}
		final OutputNodesBetween outputNodesBetween = new OutputNodesBetween();

		AbstractInsnNode outputLast = mn.instructions.getFirst();
		for (int i = 0; i < suspensions.size(); ++i) {
			final Suspension suspension = suspensions.get(i);
			outputNodesBetween.go(outputLast, suspension.node);
			outputLast = suspension.node;

			final MethodInsnNode min = (MethodInsnNode) suspension.node;
			emitStoreState(mv, i + 1, suspension);
			if (InstrumentClass.COROUTINE_NAME.equals(min.owner) && "yield".equals(min.name)) {
				// Direct call to Coroutine.yield
				// Replace with custom instructions
				if (min.getOpcode() != Opcodes.INVOKESTATIC) {
					throw new UnableToInstrumentException("invalid call to yield()", className, mn.name, mn.desc);
				}
				mv.visitFieldInsn(Opcodes.GETSTATIC,
						STACK_NAME,
						"exception_instance_not_for_user_code",
						CheckInstrumentationVisitor.EXCEPTION_DESC
				);
				mv.visitInsn(ATHROW);
				mv.visitLabel(lMethodCalls[i]);
				emitRestoreState(mv, suspension);
				outputLast = outputLast.getNext();
			} else {
				// Suspendable method
				// Reenter the method upon resuming
				mv.visitLabel(lMethodCalls[i]);
				emitRestoreState(mv, suspension);
				outputLast = outputLast.getNext();
				if (suspension.isReflective)
					mv.visitLabel(suspension.reflectStart.getLabel());
				outputNodesBetween.go(suspension.node, outputLast);
				if (suspension.isReflective) {
					// If a reflective call, unpack SuspendException from the InocationTargetException
					mv.visitLabel(suspension.reflectEnd.getLabel());
					mv.visitJumpInsn(GOTO, suspension.reflectContinue.getLabel());
					mv.visitLabel(suspension.reflectExceptHandle.getLabel());
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(INVOKEVIRTUAL,
							Type.getInternalName(InvocationTargetException.class),
							"getCause",
							"()Ljava/lang/Throwable;",
							false
					);
					mv.visitInsn(Opcodes.DUP);
					mv.visitTypeInsn(INSTANCEOF, CheckInstrumentationVisitor.EXCEPTION_NAME);
					final LabelNode notSuspendLabel = new LabelNode();
					mv.visitJumpInsn(IFEQ, notSuspendLabel.getLabel());
					mv.visitInsn(ATHROW);
					mv.visitLabel(notSuspendLabel.getLabel());
					mv.visitInsn(Opcodes.POP);
					mv.visitInsn(ATHROW);
					mv.visitLabel(suspension.reflectContinue.getLabel());
				}
			}
		}
		outputNodesBetween.go(outputLast, null);

		mv.visitLabel(lMethodEnd);

		// Output stack cleanup
		mv.visitLabel(lCatchAll);
		emitPopMethod(mv);
		mv.visitLabel(lCatchSEE);
		mv.visitInsn(ATHROW);   // rethrow shared between catchAll and catchSSE

		if (mn.localVariables != null) {
			for (final Object o : mn.localVariables) {
				((LocalVariableNode) o).accept(mv);
			}
		}

		mv.visitMaxs(mn.maxStack + 3, mn.maxLocals + 1 + additionalLocals);
		mv.visitEnd();
	}

	private static void dumpParameterAnnotations(
			final MethodVisitor mv, final List[] parameterAnnotations, final boolean visible
	) {
		for (int i = 0; i < parameterAnnotations.length; i++) {
			if (parameterAnnotations[i] != null) {
				for (final Object o : parameterAnnotations[i]) {
					final AnnotationNode an = (AnnotationNode) o;
					an.accept(mv.visitParameterAnnotation(i, an.desc, visible));
				}
			}
		}
	}

	private static void emitConst(final MethodVisitor mv, final int value) {
		if (value >= -1 && value <= 5) {
			mv.visitInsn(Opcodes.ICONST_0 + value);
		} else if ((byte) value == value) {
			mv.visitIntInsn(Opcodes.BIPUSH, value);
		} else if ((short) value == value) {
			mv.visitIntInsn(Opcodes.SIPUSH, value);
		} else {
			mv.visitLdcInsn(value);
		}
	}

	private void emitNewAndDup(
			final MethodVisitor mv, final Frame frame, final int stackIndex, final MethodInsnNode min
	) {
		final int arguments = frame.getStackSize() - stackIndex - 1;
		int neededLocals = 0;
		for (int i = arguments; i >= 1; i--) {
			final BasicValue v = (BasicValue) frame.getStack(stackIndex + i);
			mv.visitVarInsn(v.getType().getOpcode(Opcodes.ISTORE), lvarStack + 1 + neededLocals);
			neededLocals += v.getSize();
		}
		db.log(LogLevel.DEBUG,
				"Inserting NEW & DUP for constructor call %s%s with %d arguments (%d locals)",
				min.owner,
				min.desc,
				arguments,
				neededLocals
		);
		if (additionalLocals < neededLocals) {
			additionalLocals = neededLocals;
		}
		((NewValue) frame.getStack(stackIndex - 1)).insn.accept(mv);
		((NewValue) frame.getStack(stackIndex)).insn.accept(mv);
		for (int i = 1; i <= arguments; i++) {
			final BasicValue v = (BasicValue) frame.getStack(stackIndex + i);
			neededLocals -= v.getSize();
			mv.visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), lvarStack + 1 + neededLocals);
		}
	}

	private void emitPopMethod(final MethodVisitor mv) {
		mv.visitVarInsn(ALOAD, lvarStack);
		mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "popMethod", "()V");
	}

	private void emitStoreState(final MethodVisitor mv, final int jumpTableIndex, final Suspension suspension) {
		mv.visitVarInsn(ALOAD, lvarStack);
		emitConst(mv, jumpTableIndex);
		emitConst(mv, suspension.numSlots);
		mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "pushMethodAndReserveSpace", "(II)V");

		for (int i = suspension.frame.getStackSize(); i-- > 0; ) {
			final BasicValue v = (BasicValue) suspension.frame.getStack(i);
			if (!isOmitted(v)) {
				if (!isNullType(v)) {
					final int slotIdx = suspension.stackSlotIndices[i];
					assert slotIdx >= 0 && slotIdx < suspension.numSlots;
					emitStoreValue(mv, v, lvarStack, slotIdx);
				} else {
					db.log(LogLevel.DEBUG, "NULL stack entry: type=%s size=%d", v.getType(), v.getSize());
					mv.visitInsn(Opcodes.POP);
				}
			}
		}

		for (int i = firstLocal; i < suspension.frame.getLocals(); i++) {
			final BasicValue v = (BasicValue) suspension.frame.getLocal(i);
			if (!isNullType(v)) {
				mv.visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), i);
				final int slotIdx = suspension.localSlotIndices[i];
				assert slotIdx >= 0 && slotIdx < suspension.numSlots;
				emitStoreValue(mv, v, lvarStack, slotIdx);
			}
		}
	}

	private void emitRestoreState(final MethodVisitor mv, final Suspension fi) {
		for (int i = firstLocal; i < fi.frame.getLocals(); i++) {
			final BasicValue v = (BasicValue) fi.frame.getLocal(i);
			if (!isNullType(v)) {
				final int slotIdx = fi.localSlotIndices[i];
				assert slotIdx >= 0 && slotIdx < fi.numSlots;
				emitRestoreValue(mv, v, lvarStack, slotIdx);
				mv.visitVarInsn(v.getType().getOpcode(Opcodes.ISTORE), i);
			} else if (v != BasicValue.UNINITIALIZED_VALUE) {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitVarInsn(Opcodes.ASTORE, i);
			}
		}

		for (int i = 0; i < fi.frame.getStackSize(); i++) {
			final BasicValue v = (BasicValue) fi.frame.getStack(i);
			if (!isOmitted(v)) {
				if (!isNullType(v)) {
					final int slotIdx = fi.stackSlotIndices[i];
					assert slotIdx >= 0 && slotIdx < fi.numSlots;
					emitRestoreValue(mv, v, lvarStack, slotIdx);
				} else {
					mv.visitInsn(Opcodes.ACONST_NULL);
				}
			}
		}
	}

	private void emitStoreValue(
			final MethodVisitor mv, final BasicValue v, final int lvarStack, final int idx
	) throws InternalError, IndexOutOfBoundsException {
		final String desc;

		switch (v.getType().getSort()) {
			case Type.OBJECT:
			case Type.ARRAY:
				desc = "(Ljava/lang/Object;L" + STACK_NAME + ";I)V";
				break;
			case Type.BOOLEAN:
			case Type.BYTE:
			case Type.SHORT:
			case Type.CHAR:
			case Type.INT:
				desc = "(IL" + STACK_NAME + ";I)V";
				break;
			case Type.FLOAT:
				desc = "(FL" + STACK_NAME + ";I)V";
				break;
			case Type.LONG:
				desc = "(JL" + STACK_NAME + ";I)V";
				break;
			case Type.DOUBLE:
				desc = "(DL" + STACK_NAME + ";I)V";
				break;
			default:
				throw new InternalError("Unexpected type: " + v.getType());
		}

		mv.visitVarInsn(ALOAD, lvarStack);
		emitConst(mv, idx);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, STACK_NAME, "push", desc);
	}

	private void emitRestoreValue(final MethodVisitor mv, final BasicValue v, final int lvarStack, final int idx) {
		mv.visitVarInsn(ALOAD, lvarStack);
		emitConst(mv, idx);

		switch (v.getType().getSort()) {
			case Type.OBJECT:
				final String internalName = v.getType().getInternalName();
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getObject", "(I)Ljava/lang/Object;");
				if (!internalName.equals("java/lang/Object")) {  // don't cast to Object ;)
					mv.visitTypeInsn(CHECKCAST, internalName);
				}
				break;
			case Type.ARRAY:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getObject", "(I)Ljava/lang/Object;");
				mv.visitTypeInsn(CHECKCAST, v.getType().getDescriptor());
				break;
			case Type.BYTE:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I");
				mv.visitInsn(Opcodes.I2B);
				break;
			case Type.SHORT:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I");
				mv.visitInsn(Opcodes.I2S);
				break;
			case Type.CHAR:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I");
				mv.visitInsn(Opcodes.I2C);
				break;
			case Type.BOOLEAN:
			case Type.INT:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I");
				break;
			case Type.FLOAT:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getFloat", "(I)F");
				break;
			case Type.LONG:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getLong", "(I)J");
				break;
			case Type.DOUBLE:
				mv.visitMethodInsn(INVOKEVIRTUAL, STACK_NAME, "getDouble", "(I)D");
				break;
			default:
				throw new InternalError("Unexpected type: " + v.getType());
		}
	}

	static boolean isNullType(final BasicValue v) {
		return (v == BasicValue.UNINITIALIZED_VALUE) ||
				(v.isReference() && v.getType().getInternalName().equals("null"));
	}

	static boolean isOmitted(final BasicValue v) {
		if (v instanceof NewValue) {
			return ((NewValue) v).omitted;
		}
		return false;
	}

	static boolean isNewValue(final Value v, final boolean dupped) {
		if (v instanceof NewValue) {
			return ((NewValue) v).isDupped == dupped;
		}
		return false;
	}

	static class Suspension {
		final Frame frame;
		final AbstractInsnNode node;
		final int numSlots;
		final int numObjSlots;
		final int[] localSlotIndices;
		final int[] stackSlotIndices;
		final boolean isReflective;
		LabelNode reflectStart;
		LabelNode reflectEnd;
		LabelNode reflectExceptHandle;
		LabelNode reflectContinue;

		Suspension(
				final Frame f,
				final int firstLocal,
				final AbstractInsnNode node,
				final InsnList insnList,
				final MethodDatabase db,
				final boolean isReflective
		) {
			this.frame = f;
			this.node = node;
			this.isReflective = isReflective;
			if (isReflective) {
				reflectStart = new LabelNode();
				reflectEnd = new LabelNode();
				reflectExceptHandle = new LabelNode();
				reflectContinue = new LabelNode();
			}

			int idxObj = 0;
			int idxPrim = 0;

			if (f != null) {
				stackSlotIndices = new int[f.getStackSize()];
				for (int i = 0; i < f.getStackSize(); i++) {
					final BasicValue v = (BasicValue) f.getStack(i);
					if (v instanceof NewValue) {
						final NewValue newValue = (NewValue) v;
						if (db.isDebug()) {
							db.log(
									LogLevel.DEBUG,
									"Omit value from stack idx %d at instruction %d with type %s generated by %s",
									i,
									insnList.indexOf(node),
									v,
									newValue.formatInsn()
							);
						}
						if (!newValue.omitted) {
							newValue.omitted = true;
							if (db.isDebug()) {
								// need to log index before replacing instruction
								db.log(LogLevel.DEBUG,
										"Omitting instruction %d: %s",
										insnList.indexOf(newValue.insn),
										newValue.formatInsn()
								);
							}
							insnList.set(newValue.insn, new OmittedInstruction(newValue.insn));
						}
						stackSlotIndices[i] = -666; // an invalid index ;)
					} else if (!isNullType(v)) {
						if (v.isReference()) {
							stackSlotIndices[i] = idxObj++;
						} else {
							stackSlotIndices[i] = idxPrim++;
						}
					} else {
						stackSlotIndices[i] = -666; // an invalid index ;)
					}
				}

				localSlotIndices = new int[f.getLocals()];
				for (int i = firstLocal; i < f.getLocals(); i++) {
					final BasicValue v = (BasicValue) f.getLocal(i);
					if (!isNullType(v)) {
						if (v.isReference()) {
							localSlotIndices[i] = idxObj++;
						} else {
							localSlotIndices[i] = idxPrim++;
						}
					} else {
						localSlotIndices[i] = -666; // an invalid index ;)
					}
				}
			} else {
				stackSlotIndices = null;
				localSlotIndices = null;
			}

			numSlots = Math.max(idxPrim, idxObj);
			numObjSlots = idxObj;
		}
	}

	private static class BlockingMethod {
		final String owner;
		final String name;
		final String[] descs;

		BlockingMethod(final String owner, final String name, final String... descs) {
			this.owner = owner;
			this.name = name;
			this.descs = descs;
		}

		public boolean match(final MethodInsnNode min) {
			if (owner.equals(min.owner) && name.equals(min.name)) {
				for (final String desc : descs) {
					if (desc.equals(min.desc)) {
						return true;
					}
				}
			}
			return false;
		}
	}
}
