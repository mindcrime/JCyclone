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

package org.jcyclone.core.bench.stage_latency;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.event.NullEvent;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.stage.IStage;
import org.jcyclone.core.timer.ITimer;
import org.jcyclone.core.timer.JCycloneTimer;
import org.jcyclone.util.Util;

import java.util.List;

public class TimerHandler implements IEventHandler {

	private static final boolean DEBUG = false;

	private ISink nextHandlerSink, mysink;
	private ITimer timer;
	private int DELAY_TIME = 1000; // Delay before initial burst
	private int NUM_STAGES;
	private int BURST_SIZE, EVENT_SIZE;
	private int MEASUREMENT_SIZE, LATENCY_MEASUREMENT_SIZE;
	private myEvent[] burst;
	private int numCompletions = 0;
	private long t1, t2;

	public TimerHandler() {
	}

	public void init(IConfigData config) throws Exception {
		mysink = config.getStage().getSink();

		NUM_STAGES = config.getInt("num_stages");
		if (NUM_STAGES == -1) System.err.println("Must specify num_stages!");
		BURST_SIZE = config.getInt("burst_size");
		if (BURST_SIZE == -1) System.err.println("Must specify burst_size!");
		EVENT_SIZE = config.getInt("event_size");
		if (EVENT_SIZE == -1) System.err.println("Must specify event_size!");

		MEASUREMENT_SIZE = BURST_SIZE * 100;
		LATENCY_MEASUREMENT_SIZE = 100;

		// Create stages
		IStage stagearr[] = new IStage[NUM_STAGES];
		for (int i = NUM_STAGES - 1; i >= 0; i--) {
			String name = "GenericHandler-" + i;
			String nextName;
			if (i == NUM_STAGES - 1) {
				// Point back to outself
				nextName = config.getStage().getName();
			} else {
				nextName = "GenericHandler-" + (i + 1);
			}
			GenericHandler gh = new GenericHandler(nextName, 0, 0, 0);
			stagearr[i] = config.getManager().createStage(name, gh, null);
			if (i == 0) {
				nextHandlerSink = stagearr[i].getSink();
			}
		}

		burst = new myEvent[BURST_SIZE];
		for (int i = 0; i < BURST_SIZE; i++) {
			burst[i] = new myEvent(i, EVENT_SIZE);
		}

		timer = new JCycloneTimer();
		timer.registerEvent(DELAY_TIME, new NullEvent(), mysink);
	}

	public void destroy() {
	}

	public void handleEvent(IElement item) {
		if (DEBUG) System.err.println("GOT QEL: " + item);

		if (item instanceof NullEvent) {

			// Send initial burst
			t1 = System.currentTimeMillis();
			for (int i = 0; i < BURST_SIZE; i++) {
				if (burst[i].num == 0) {
					burst[i].startTime = System.currentTimeMillis();
				}
				nextHandlerSink.enqueueLossy(burst[i]);
			}

		} else if (item instanceof myEvent) {
			myEvent ev = (myEvent) item;

			numCompletions++;
			if (numCompletions == MEASUREMENT_SIZE) {
				t2 = System.currentTimeMillis();
				printBW();
				numCompletions = 0;
				t1 = System.currentTimeMillis();
			}

			if (ev.num == 0) {
				ev.count++;
				if (ev.count == LATENCY_MEASUREMENT_SIZE) {
					ev.printLat();
					ev.count = 0;
				}
			}

			// Send it back in
			nextHandlerSink.enqueueLossy(ev);
		}
	}

	public void handleEvents(List events) {
		for (int i = 0; i < events.size(); i++) {
			handleEvent((IElement) events.get(i));
		}
	}

	private void printBW() {
		long diff = t2 - t1;
		double numpersec = ((double) MEASUREMENT_SIZE * 1.0) / (diff * 1.0e-3);
		System.err.println("Bandwidth: " + Util.format(numpersec) + " packets/sec");
	}

	// Class to represent an event passing through the system
	class myEvent implements IElement {
		int num;
		BufferElement buf;
		long startTime;
		int count = 0;

		myEvent(int num, int size) {
			this.num = num;
			if (size != 0) this.buf = new BufferElement(size);
		}

		void printLat() {
			long endTime = System.currentTimeMillis();
			double avglat = ((endTime - startTime) * 1.0) / (count * 1.0);
			avglat /= (NUM_STAGES * 1.0);
			System.err.println("Latency: " + avglat + " ms per stage");
			startTime = System.currentTimeMillis();
		}
	}

}

