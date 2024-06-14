/* 
 * Copyright (c) 2001 by Matt Welsh and The Regents of the University of 
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

package org.jcyclone.core.basic;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.SinkException;

import java.util.List;

public class GenericHandler implements IEventHandler {

	private static final boolean DEBUG = true;

	private ISink nextHandlerSink;

	private int CPU1_TIME;
	private int CPU2_TIME;
	private int SLEEP_TIME;

	public GenericHandler() {
	}

	public void init(IConfigData config) throws Exception {

		if (config.getString("cpu1") == null)
			System.err.println("Must specify cpu1!");
		if (config.getString("cpu2") == null)
			System.err.println("Must specify cpu2!");
		if (config.getString("sleep") == null)
			System.err.println("Must specify sleep!");
		if (config.getString("next_handler") == null)
			System.err.println("Must specify next_handler!");

		CPU1_TIME = Integer.parseInt(config.getString("cpu1"));
		CPU2_TIME = Integer.parseInt(config.getString("cpu2"));
		SLEEP_TIME = Integer.parseInt(config.getString("sleep"));

		nextHandlerSink = config.getManager().getStage(config.getString("next_handler")).getSink();

		System.err.println("sleep=" + SLEEP_TIME);
	}

	public void destroy() {
	}

	public void handleEvent(IElement item) {
		long t1, t2;
		if (DEBUG) System.err.println("GenericHandler: GOT QEL: " + item);

		if (item instanceof BufferElement) {

			BufferElement buf = (BufferElement) item;

			if (CPU1_TIME != 0) {
				t1 = System.currentTimeMillis();
				do {
					t2 = System.currentTimeMillis();
				} while ((t2 - t1) < CPU1_TIME);
			}

			if (SLEEP_TIME != 0) {
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException ie) {
					System.err.println("Warning: Interrupted sleep!");
				}
			}

			if (CPU2_TIME != 0) {
				t1 = System.currentTimeMillis();
				do {
					t2 = System.currentTimeMillis();
				} while ((t2 - t1) < CPU2_TIME);
			}

			try {
				nextHandlerSink.enqueue(buf);
			} catch (SinkException sce) {
				System.err.println("Got SE: " + sce);
			}

		} else {
			System.err.println("Got unexpected event: " + item);
		}

	}

	public void handleEvents(List events) {
		for (int i = 0; i < events.size(); i++) {
			handleEvent((IElement) events.get(i));
		}
	}


}

