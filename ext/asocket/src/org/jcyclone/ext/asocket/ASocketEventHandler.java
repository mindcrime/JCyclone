/* 
 * Copyright (c) 2000 by Matt Welsh and The Regents of the University of 
 * California. All rights reserved.
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without written agreement is
 * hereby granted, provided that the above copyright notice and the following
 * two paragraphs appear in all copies of this software.
 * 
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
 * OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF
 * CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Author: Matt Welsh <mdw@cs.berkeley.edu>
 * 
 */

package org.jcyclone.ext.asocket;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;

import java.util.List;

/**
 * Abstract superclass of the event handlers used by aSocket.
 */
abstract class ASocketEventHandler implements IEventHandler {

	private static final boolean DEBUG = false;

	protected ISink eventQ;
	protected SelectSourceIF selsource;

	ASocketEventHandler() {
		this.selsource = ASocketMgr.getFactory().newSelectSource();
	}

	// Used to let ReadStageWrapper get a handle to the selsource
	SelectSourceIF getSelectSource() {
		return selsource;
	}

	public abstract void init(IConfigData config);

	public abstract void destroy();

	public abstract void handleEvent(IElement qel);

	public abstract void handleEvents(List events);

	//  Called to nudge the selecting thread out of a poll when a StartReading
	//  event is in the queue.
	public void interruptSelect() {
		if (DEBUG) System.err.println("ReadEventHandler.interruptSelect() called.");
		selsource.interruptSelect();
	}
}

