package org.jcyclone.core.continuation;

import org.jcyclone.core.internal.IStageWrapper;

/**
 * A Continuation contains all of the information that a
 * thread will need for any future computation.
 */
public class Continuation {

	/**
	 * Outgoing callback state for (blocking) call event.
	 */
	public ContinuationFrame callState;

	/**
	 * Incoming callback state for returning (blocking) call event.
	 */
	public ContinuationFrame callbackState;

	/**
	 * Used to chain continuations in wait set.
	 */
//	public Continuation next;

	/**
	 * The stage that this continuation belong to.
	 */
	public IStageWrapper stage;

}
