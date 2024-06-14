package org.jcyclone.core.continuation;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.*;
import org.apache.bcel.verifier.structurals.Frame;
import org.apache.bcel.verifier.structurals.LocalVariables;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.apache.bcel.verifier.structurals.UninitializedObjectType;

import java.util.Stack;
import java.util.Vector;


/**
 * Traversal object that modifies continuable methods. Continuable needs to be
 * able to pause and resume their execution at various points: at any blocking
 * call, or continuable regular method call. Pausing execution means
 * saving both the frame and the program location into a custom state object.
 * Resuming execution means reversing the process.
 * <p/>
 * <pre>
 * Static analysis:
 *   - data flow: to determine what the frame (stack and locals) looks like
 *       at each blocking or continuable execution point
 *   - state class: create custom state class (subclass of ContinuationFrame)
 *     with fields for locals and stack slots at each execution point
 * <p/>
 * Pausing execution:
 *  - save frame (locals and stack) to state object before execution and store
 *    in additional local variable
 *  - execute method invocation
 *  - check if we are in save mode
 *    - if yes, then push frame information using Controller and return
 *      immediately (with some dummy return value, if necessary)
 * <p/>
 * Restoring execution:
 *   - (at method start) check if we are invoking this method in restore mode
 *   - if yes, pop frame information from Controller
 *   - switch on program counter
 *   - restore frame (locals and stack) slots
 *   - jump to pause point
 * </pre>
 */
class RewriterTraversalContinuableMethods extends ClassTraversal.Empty {

	/**
	 * primary rewriter.
	 */
	private Rewriter rewriter;
	/**
	 * data flow analysis object.
	 */
	private RewriterFlow flow;
	/**
	 * data flow analysis result.
	 */
	private RewriterFlow.FlowInfoMap flowinfo;
	/**
	 * class constant pool.
	 */
	private ConstantPoolGen cpg;
	/**
	 * class instruction factory.
	 */
	private InstructionFactory ifc;
	/**
	 * program location counter.
	 */
	private int pc;
	/**
	 * save/restore frames.
	 */
	private Vector frames;
	/**
	 * save/restore instruction handles.
	 */
	private Vector ihs;

	/**
	 * Create rewriter object that will transform continuable methods to CPS.
	 *
	 * @param rewriter reference to primary rewriter
	 */
	public RewriterTraversalContinuableMethods(Rewriter rewriter) {
		this.rewriter = rewriter;
		this.flow = new RewriterFlow();
	}

	/**
	 * {@inheritDoc}
	 */
	public ClassGen doClass(ClassGen cg) {
		this.cpg = cg.getConstantPool();
		this.ifc = new InstructionFactory(cg);
		return cg;
	}

	/**
	 * {@inheritDoc}
	 */
	public MethodGen doMethod(ClassGen cg, MethodGen mg) {
		// if method is continuable
		if (rewriter.isContinuable(mg) && !mg.isAbstract()) {
			Rewriter.log.fine("Processing continuable method: " + mg.getName());
			// do flow analysis of method
			flowinfo = flow.doFlow(cg, mg);
			// valid pc values >= 1
			frames = new Vector();
			frames.add(null);
			ihs = new Vector();
			ihs.add(null);
			pc = 0;
		}
		return mg;
	}

	/**
	 * {@inheritDoc}
	 */
	public MethodGen doMethodPost(ClassGen cg, MethodGen mg) {
		if (flowinfo != null) {
			// add restore code
			restoreFrame(mg, frames, ihs, ifc);
			frames = null;
			ihs = null;
			flowinfo = null;
		}
		return mg;
	}

	/**
	 * {@inheritDoc}
	 */
	public void doInstruction(ClassGen cg, MethodGen mg, InstructionHandle ih, Instruction inst) throws ClassNotFoundException {
		// if invocation instruction is a continuable method
		if (flowinfo != null && inst instanceof InvokeInstruction) {
			InvokeInstruction inv = (InvokeInstruction) inst;
			// if invocation is to continuable or blocking method
			if (rewriter.isBlocking(inv.getClassName(cpg), inv.getMethodName(cpg), inv.getReturnType(cpg), inv.getArgumentTypes(cpg)) ||
			        rewriter.isContinuable(inv, cpg)) {
				// increment program location counter
				pc++;
				// retrieve frame at this location
				Frame f = fixFrame(flowinfo.getFrame(ih));
				// define custom state type for this execution point
				rewriter.define(createStateClass(mg, pc, f.getClone()));
				// save frame into custom state object
				saveFrame(mg, ih, pc, f.getClone(), ifc);
				frames.add(f);
				ihs.add(ih);
			}
		}
	}

	/**
	 * Return BCEL frame with fewer object types (only relevant ones)
	 * in order to simplify frame save/restore code.
	 *
	 * @param f input frame from data flow
	 * @return frame with fewer object types
	 */
	private static Frame fixFrame(Frame f) {
		// fix locals
		LocalVariables lv = f.getLocals().getClone();
		for (int i = 0; i < lv.maxLocals(); i++) {
			Type t = lv.get(i);
			lv.set(i, fixType(t));
		}
		// fix stack
		OperandStack os = f.getStack().getClone();
		Stack tmp = new Stack();
		while (!os.isEmpty()) tmp.push(fixType(os.pop()));
		while (!tmp.isEmpty()) os.push((Type) tmp.pop());
		return new Frame(lv, os);
	}

	/**
	 * Helper method for fixFrame to process individual types
	 * within Frame object.
	 *
	 * @param t input type from data flow frame
	 * @return simplified type
	 */
	private static Type fixType(Type t) {
		switch (t.getType()) {
			case Constants.T_OBJECT:
				// we don't care about reference types
				if (ReferenceType.class.equals(t.getClass())) return Type.OBJECT;
				return t;
			case Constants.T_BOOLEAN:
			case Constants.T_BYTE:
			case Constants.T_CHAR:
			case Constants.T_SHORT:
			case Constants.T_INT:
			case Constants.T_LONG:
			case Constants.T_FLOAT:
			case Constants.T_DOUBLE:
				return t;
			case Constants.T_ARRAY:
				// rimnote: recurse?
				return t;
			case Constants.T_VOID:
				throw new VerifyError("unexpected type: VOID");
			case Constants.T_ADDRESS:
				throw new VerifyError("cannot store return address in continuation: do you perhaps have a blocking call inside a finally clause?");
			case Constants.T_UNKNOWN:
				if (t instanceof UninitializedObjectType) {
					throw new VerifyError("Unable to preserve continuation due to " + t.getSignature() + " on stack. Is your blocking call within a constructor call?");
				}
				return null;
			default:
				throw new VerifyError("unexpected type: " + t.getType());
		}
	}

	/**
	 * Returns whether Type is Uninitialized.
	 *
	 * @param t BCEL type
	 * @return whether type is uninitialized
	 */
	public static boolean isNotInit(Type t) {
		return t.getType() == Constants.T_UNKNOWN && t instanceof UninitializedObjectType;
	}

	/**
	 * Generate state class for a specific execution point within a method. The
	 * generated classes subclass Event.ContinuationFrame, and have properly
	 * types variables called 'local_i' and 'stack_j' for each local variable and
	 * stack slot in the given frame. The class is unique names by its method and
	 * execution point.
	 *
	 * @param mg method whose frame is being saved
	 * @param pc program location identifier
	 * @param f  frame to save
	 * @return BCEL class object
	 */
	private static JavaClass createStateClass(MethodGen mg, int pc, Frame f) {
		// create class
		String name = Rewriter.getContinutationClassName(mg, pc);
		ClassGen cg = new ClassGen(name, ContinuationFrame.class.getName(), null,
		        Constants.ACC_PRIVATE | Constants.ACC_FINAL, null);
		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionFactory ifc = new InstructionFactory(cpg);
		// add constructor
		InstructionList il = new InstructionList();
		il.append(InstructionFactory.createThis());
		il.append(ifc.createInvoke(ContinuationFrame.class.getName(),
		        Constants.CONSTRUCTOR_NAME, Type.VOID, new Type[]{},
		        Constants.INVOKESPECIAL));
		il.append(InstructionFactory.createReturn(Type.VOID));
		MethodGen mginit = new MethodGen(Constants.ACC_PUBLIC, Type.VOID,
		        new Type[]{}, new String[]{},
		        Constants.CONSTRUCTOR_NAME,
		        name, il, cpg);
		mginit.setMaxStack();
		mginit.setMaxLocals();
		cg.addMethod(mginit.getMethod());
		// add fields for locals
		LocalVariables lv = f.getLocals();
		for (int i = 0; i < lv.maxLocals(); i++) {
			Type t = lv.get(i);
			if (t == null || t == Type.NULL || isNotInit(t)) continue;
			FieldGen fg = new FieldGen(Constants.ACC_PUBLIC, t, "local_" + i, cpg);
			cg.addField(fg.getField());
		}
		// add fields for stack
		OperandStack os = f.getStack();
		for (int i = 0; !os.isEmpty(); i++) {
			Type t = os.pop();
			if (t == null || t == Type.NULL || isNotInit(t)) continue;
			FieldGen fg = new FieldGen(Constants.ACC_PUBLIC, t, "stack_" + i, cpg);
			cg.addField(fg.getField());
		}
		return cg.getJavaClass();
	}

	/**
	 * Insert instructions to save an execution frame to state object.
	 *
	 * @param mg  method to be saved
	 * @param ih  instruction handle of save point
	 * @param pc  program location identifier
	 * @param f   stack and local types
	 * @param ifc class instruction factory
	 */
	private static void saveFrame(MethodGen mg, InstructionHandle ih,
	                              int pc, Frame f, InstructionFactory ifc) {
		String stateName = Rewriter.getContinutationClassName(mg, pc);
		Type stateType = new ObjectType(stateName);
		InstructionList pre = new InstructionList();
		int locals = mg.getMaxLocals();

		// ** create and initialize state object
		pre.append(ifc.createNew(stateName));
		// stack: (stuff) state_object
		pre.append(InstructionConstants.DUP);
		// stack: (stuff) state_object state_object
		pre.append(ifc.createInvoke(stateName, Constants.CONSTRUCTOR_NAME,
		        Type.VOID, new Type[]{}, Constants.INVOKESPECIAL));
		// stack: (stuff) state_object
		pre.append(InstructionFactory.createStore(stateType, locals));
		// stack: (stuff)

		// ** save pc into state object
		pre.append(InstructionFactory.createLoad(stateType, locals));
		// stack: (stuff) state_object
		pre.append(InstructionFactory.DUP);
		// stack: (stuff) state_object state_object
		pre.append(new PUSH(mg.getConstantPool(), pc));
		// stack: (stuff) state_object state_object pc
		pre.append(ifc.createPutField(ContinuationFrame.class.getName(),
		        ContinuationFrame.field_pc.getName(), Type.INT));
		// stack: (stuff) state_object

		// ** store locals into state object
		LocalVariables lv = f.getLocals();
		for (int i = 0; i < lv.maxLocals(); i++) {
			Type t = lv.get(i);
			if (t == null) continue;
			if (t != Type.NULL && !isNotInit(t)) {
				pre.append(InstructionFactory.DUP);
				// stack: (stuff) state_object state_object
				pre.append(InstructionFactory.createLoad(t, i));
				// stack: (stuff) state_object state_object local_i
				pre.append(ifc.createPutField(stateName, "local_" + i, t));
				// stack: (stuff) state_object
			}
		}
		// stack: (stuff) state_object

		// ** store stack into state object (and leave untouched)
		OperandStack os = f.getStack().getClone();
		InstructionList loadlist = new InstructionList();
		for (int i = 0; !os.isEmpty(); i++) {
			Type t = os.pop();
			if (t == null) continue;
			if (t == Type.NULL) {
				pre.append(InstructionFactory.SWAP);
				// stack: (stuff-1) state_object null
				pre.append(InstructionFactory.POP);
				// stack: (stuff-1) state_object
				loadlist.insert(InstructionFactory.ACONST_NULL);
			} else if (isNotInit(t)) {
				pre.append(InstructionFactory.SWAP);
				// stack: (stuff-1) state_object uninit
				pre.append(InstructionFactory.POP);
				// stack: (stuff-1) state_object
				loadlist.insert(ifc.createNew(((UninitializedObjectType) t).getInitialized()));
				// rimnote: Technicality... hopefully we don't ever have two
				//   uninitialized stack references to the same object. Should not ever
				//   occur if this is Java code that is compiled, because it's not
				//   possible to refer to the result of an expression within itself,
				//   and locals must be initialized.
			} else {
				pre.append(InstructionFactory.DUP);
				// stack: (stuff-1) (stuff) state_obj state_obj
				if (t.getSize() == 1) {
					pre.append(InstructionFactory.DUP2_X1);
				} else {
					pre.append(InstructionFactory.DUP2_X2);
				}
				// stack: (stuff-1) state_obj state_obj (stuff) state_obj state_obj
				pre.append(InstructionFactory.POP);
				pre.append(InstructionFactory.POP);
				// stack: (stuff-1) state_obj state_obj (stuff)
				pre.append(ifc.createPutField(stateName, "stack_" + i, t));
				// stack: (stuff-1) state_obj

				InstructionHandle ihload =
				        loadlist.insert(ifc.createLoad(stateType, locals));
				loadlist.append(ihload, ifc.createGetField(stateName, "stack_" + i, t));
			}
		}
		pre.append(InstructionFactory.POP);
		// stack: (empty)
		pre.append(loadlist);
		// stack: (stuff)

		InstructionList post = new InstructionList();

		// ** if(Controller.isCallSave())
		post.append(ifc.createInvoke(ContinuationManager.method_isModeSave.getDeclaringClass().getName(),
		        ContinuationManager.method_isModeSave.getName(),
		        Rewriter.getType(ContinuationManager.method_isModeSave.getReturnType()),
		        Rewriter.getTypes(ContinuationManager.method_isModeSave.getParameterTypes()),
		        Constants.INVOKESTATIC));
		// stack: (stuff) true
		post.append(InstructionFactory.createBranchInstruction(Constants.IFEQ, ih.getNext()));
		// stack: (stuff)

		// ** pushStateFrame(state_obj)
		post.append(InstructionFactory.createLoad(Type.OBJECT, locals));
		// stack: (post-call stuff) state_obj
		post.append(ifc.createInvoke(ContinuationManager.method_pushStateOutFrame.getDeclaringClass().getName(),
		        ContinuationManager.method_pushStateOutFrame.getName(),
		        Rewriter.getType(ContinuationManager.method_pushStateOutFrame.getReturnType()),
		        Rewriter.getTypes(ContinuationManager.method_pushStateOutFrame.getParameterTypes()),
		        Constants.INVOKESTATIC));
		// stack: (post-call stuff)

		// ** return
		Type retType = mg.getReturnType();
		ConstantPoolGen cpg = mg.getConstantPool();
		switch (retType.getType()) {
			case Constants.T_VOID:
				post.append(InstructionFactory.RETURN);
				break;
			case Constants.T_BOOLEAN:
				post.append(new PUSH(cpg, true));
				post.append(InstructionFactory.createReturn(retType));
				break;
			case Constants.T_BYTE:
			case Constants.T_CHAR:
			case Constants.T_SHORT:
			case Constants.T_INT:
				post.append(new PUSH(cpg, 0));
				post.append(InstructionFactory.createReturn(retType));
				break;
			case Constants.T_LONG:
				post.append(new PUSH(cpg, (long) 0));
				post.append(InstructionFactory.createReturn(retType));
				break;
			case Constants.T_FLOAT:
				post.append(new PUSH(cpg, (float) 0));
				post.append(InstructionFactory.createReturn(retType));
				break;
			case Constants.T_DOUBLE:
				post.append(new PUSH(cpg, (double) 0));
				post.append(InstructionFactory.createReturn(retType));
				break;
			case Constants.T_OBJECT:
			case Constants.T_ARRAY:
				post.append(InstructionFactory.ACONST_NULL);
				post.append(InstructionFactory.createReturn(retType));
				break;
			default:
				throw new RuntimeException("unexpected return type");
		}

		// modify instruction list
		InstructionHandle newTop = mg.getInstructionList().insert(ih, pre);
		mg.getInstructionList().append(ih, post);
		if (ih.hasTargeters()) {
			InstructionTargeter[] targeters = ih.getTargeters();
			for (int i = 0; i < targeters.length; i++) {
				targeters[i].updateTarget(ih, newTop);
			}
		}

		// todo: adjust exception handling regions
	}

	/**
	 * Insert instructions to restore an execution frame from a state object.
	 *
	 * @param mg     method to be restored onto stack
	 * @param frames frames of restoration points
	 * @param ihs    instruction handles of restoration points
	 * @param ifc    class instruction factory
	 */
	private static void restoreFrame(MethodGen mg, Vector frames, Vector ihs, InstructionFactory ifc) {
		InstructionList il = mg.getInstructionList();
		InstructionHandle start = il.getStart();
		int locals = mg.getMaxLocals();

		// ** if(Controller.isCallbackRestore())
		il.insert(start, ifc.createInvoke(ContinuationManager.method_isModeRestore.getDeclaringClass().getName(),
		        ContinuationManager.method_isModeRestore.getName(),
		        Rewriter.getType(ContinuationManager.method_isModeRestore.getReturnType()),
		        Rewriter.getTypes(ContinuationManager.method_isModeRestore.getParameterTypes()),
		        Constants.INVOKESTATIC));
		// stack: boolean
		il.insert(start, InstructionFactory.createBranchInstruction(Constants.IFEQ, start));
		// stack: (empty)

		// ** pop stack frame
		il.insert(start, ifc.createInvoke(ContinuationManager.method_popStateInFrame.getDeclaringClass().getName(),
		        ContinuationManager.method_popStateInFrame.getName(),
		        Rewriter.getType(ContinuationManager.method_popStateInFrame.getReturnType()),
		        Rewriter.getTypes(ContinuationManager.method_popStateInFrame.getParameterTypes()),
		        Constants.INVOKESTATIC));
		// stack: popStackFrame()
		il.insert(start, InstructionFactory.createStore(Rewriter.getType(ContinuationManager.method_popStateInFrame.getReturnType()), locals));
		// stack: (empty)

		// ** switch(popStackFrame()pc)
		il.insert(start, InstructionFactory.createLoad(Rewriter.getType(ContinuationManager.method_popStateInFrame.getReturnType()), locals));
		// stack: popStackFrame()
		il.insert(start, InstructionFactory.DUP);
		// stack: popStackFrame() popStackFrame()
		il.insert(start, ifc.createGetField(ContinuationFrame.class.getName(),
		        ContinuationFrame.field_pc.getName(),
		        Rewriter.getType(ContinuationFrame.field_pc.getType())));
		// stack: popStackFrame() popStackFrame().pc
		Select switchPc = new TABLESWITCH(Util.getRange(frames.size()), new InstructionHandle[frames.size()], null);
		// stack: popStackFrame()
		il.insert(start, switchPc);

		// ** case: bad pc
		InstructionHandle ihBadpc =
		        il.insert(start, ifc.createNew(RuntimeException.class.getName()));
		il.insert(start, InstructionConstants.DUP);
		il.insert(start, new PUSH(mg.getConstantPool(), "invalid pc value"));
		il.insert(start, ifc.createInvoke(RuntimeException.class.getName(), Constants.CONSTRUCTOR_NAME,
		        Type.VOID, new Type[]{Type.STRING}, Constants.INVOKESPECIAL));
		il.insert(start, new ATHROW());
		switchPc.setTarget(ihBadpc);
		switchPc.setTarget(0, ihBadpc);

		// ** case: good pc
		for (int pc = 1; pc < frames.size(); pc++) {
			String stateName = Rewriter.getContinutationClassName(mg, pc);
			ObjectType stateType = new ObjectType(stateName);
			InstructionHandle ihGoodpci =
			        il.insert(start, ifc.createCheckCast(stateType));
			// stack: (pc_i)popStackFrame()
			switchPc.setTarget(pc, ihGoodpci);
			Frame f = (Frame) frames.elementAt(pc);

			// ** restore locals
			LocalVariables lv = f.getLocals();
			for (int i = 0; i < lv.maxLocals(); i++) {
				Type t = lv.get(i);
				if (t == null) continue;
				if (t == Type.NULL) {
					il.insert(start, InstructionConstants.ACONST_NULL);
					// stack: (pc_i)popStackFrame(), null
				} else if (isNotInit(t)) {
					il.insert(start, ifc.createNew(((UninitializedObjectType) t).getInitialized()));
					// stack: (pc_i)popStackFrame(), uninit
				} else {
					il.insert(start, InstructionConstants.DUP);
					// stack: (pc_i)popStackFrame(), (pc_i)popStackFrame()
					il.insert(start, ifc.createGetField(stateName, "local_" + i, t));
					// stack: (pc_i)popStackFrame(), popStackFrame().local_i
				}
				il.insert(start, InstructionFactory.createStore(t, i));
				// stack: (pc_i)popStackFrame()
			}

			// ** restore stack
			OperandStack os = f.getStack().getClone();
			Stack os2 = new Stack();
			while (!os.isEmpty()) os2.push(os.pop());
			for (int i = os2.size() - 1; !os2.isEmpty(); i--) {
				Type t = (Type) os2.pop();
				if (t == null) continue;
				if (t == Type.NULL) {
					il.insert(start, InstructionFactory.ACONST_NULL);
					// stack: (pc_i)popStackFrame(), null
					il.insert(start, InstructionFactory.SWAP);
					// stack: null (pc_i)popStackFrame()
				} else if (isNotInit(t)) {
					il.insert(start, ifc.createNew(((UninitializedObjectType) t).getInitialized()));
					// stack: (pc_i)popStackFrame(), uninit
				} else {
					il.insert(start, InstructionFactory.DUP);
					// stack: (pc_i)popStackFrame(), (pc_i)popStackFrame()
					il.insert(start, ifc.createGetField(stateName, "stack_" + i, t));
					// stack: (pc_i)popStackFrame(), popStackFrame().stack_i
					if (t.getSize() == 1) {
						il.insert(start, InstructionFactory.SWAP);
					} else {
						il.insert(start, InstructionFactory.DUP2_X1);
						il.insert(start, InstructionFactory.POP2);
					}
					// stack: popStackFrame().stack_i, (pc_i)popStackFrame()
				}
			}
			// stack: (stuff) (pc_i)popStackFrame()
			il.insert(start, InstructionFactory.createStore(stateType, locals));
			// stack: (stuff)

			// ** jump back to continuation location
			il.insert(start, new GOTO((InstructionHandle) ihs.elementAt(pc)));
		}
	}

} // class: RewriterTraversalContinuableMethods

