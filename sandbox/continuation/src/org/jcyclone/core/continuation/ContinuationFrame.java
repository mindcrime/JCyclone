package org.jcyclone.core.continuation;


import java.lang.reflect.Field;

/**
 * Stores a single stack frame for calls with continuation.
 */
public class ContinuationFrame {

	//////////////////////////////////////////////////
	// constants
	//

	/**
	 * frame at bottom of stack .
	 */
	public static final ContinuationFrame BASE = new ContinuationFrame();

	/**
	 * field stub for pointer to next stack frame.
	 */
	public static Field field_next;

	/**
	 * field stub for program "counter" location.
	 */
	public static Field field_pc;

	static {
		try {
			field_next = ContinuationFrame.class.getDeclaredField("next");
			field_pc = ContinuationFrame.class.getDeclaredField("pc");
		} catch (NoSuchFieldException e) {
			throw new RewriterException("should not happen", e);
		}
	}

	//////////////////////////////////////////////////
	// locals
	//

	/**
	 * pointer to next stack frame.
	 */
	public ContinuationFrame next;

	/**
	 * program "counter" location where frame information captured.
	 */
	public int pc;

	/**
	 * Test whether to events are equal.
	 *
	 * @param o object to perform equality test
	 * @return whether object is equal to event
	 */
	public boolean equals(Object o) {
		if (o == null) return false;
		if (!(o instanceof ContinuationFrame)) return false;
		ContinuationFrame cf = (ContinuationFrame) o;
		if (pc != cf.pc) return false;
		if (next == null && cf.next != null) return false;
		return next.equals(cf.next);
	}

} // class: ContinuationFrame