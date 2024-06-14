package org.jcyclone.core.continuation;



import org.jcyclone.core.continuation.Continuation;
import org.jcyclone.core.continuation.ContinuationFrame;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class ContinuationManager {

	/**
	 * Logger instance.
	 */
	public static final Logger log = Logger.getLogger(ContinuationManager.class.getName());

	/**
	 * Return next frame of incoming event continuation state.
	 */
	public static Method method_popStateInFrame;

	/**
	 * Store next frame of outgoing event continuation state.
	 */
	public static final Method method_pushStateOutFrame;

	/**
	 * Method stub for callback to determine whether current event
	 * invocation is in restore mode.
	 */
	public static final Method method_isModeRestore;

	/**
	 * Method stub for callback to determine whether current event
	 * invocation is in save mode.
	 */
	public static final Method method_isModeSave;

	static {
		try {
			method_popStateInFrame = ContinuationManager.class.getDeclaredMethod("popStateInFrame",
			        new Class[]{});
			method_pushStateOutFrame = ContinuationManager.class.getDeclaredMethod("pushStateOutFrame",
			        new Class[]{ContinuationFrame.class});
			method_isModeRestore = ContinuationManager.class.getDeclaredMethod("isModeRestore",
			        new Class[]{});
			method_isModeSave = ContinuationManager.class.getDeclaredMethod("isModeSave",
			        new Class[]{});
		} catch (NoSuchMethodException e) {
			throw new RewriterException("should never happen", e);
		}
	}

	/**
	 * Pop a frame from the incoming execution stack.
	 *
	 * @return next frame from the incoming execution stack.
	 */
	public static ContinuationFrame popStateInFrame() {
		ContinuationFrame f = null;
	    Continuation c = getCurrentContinuation();
		f = c.callbackState;
		c.callbackState = c.callbackState.next;
		log.finer("popStateIn: "+f.getClass());
		return f;
	}

	/**
	 * Push a frame onto the outgoing execution stack.
	 *
	 * @param f next frame of outgoing execution stack.
	 */
	public static void pushStateOutFrame(ContinuationFrame f) {
		log.finer("pushStateOut "+f.getClass());
		Continuation c = getCurrentContinuation();
		f.next = c.callState;
		c.callState = f;
	}

	static Continuation getCurrentContinuation() {
		return (Continuation) continuation.get();
	}

	static void setCurrentContinuation(Continuation cont) {
		continuation.set(cont);
	}

	private static LocalContinuation continuation = new LocalContinuation();

	private static class LocalContinuation extends ThreadLocal { }


	/**
	 * Return whether the event invocation is currently in restore mode.
	 *
	 * @return true if currently restoring an event execution stack
	 */
	public static boolean isModeRestore() {
		return false;
//		return getActiveController().callbackState != null;
	}

	public static void enableModeRestore() {

	}

	/**
	 * Return whether the event invocation is currently in save mode.
	 *
	 * @return true if current save an event execution stack
	 */
	public static boolean isModeSave() {
		return true;
//        return getCurrentContinuation().call != null;
	}

	public static void enableModeSave() {

	}


}
