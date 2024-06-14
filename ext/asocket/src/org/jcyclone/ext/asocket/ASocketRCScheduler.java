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

import org.jcyclone.core.internal.IBatchDescr;
import org.jcyclone.core.internal.IScheduler;
import org.jcyclone.core.stage.IStageManager;
import org.jcyclone.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * ASocketRCScheduler is a version of ASocketScheduler that incorporates
 * a rate controller: given a target packet-processing rate, it adjusts
 * its schedule to attempt to match that rate. The controller is based
 * on adding controlled pauses to the packet-processing loop.
 *
 * @author Matt Welsh
 */
class ASocketRCScheduler extends ASocketScheduler implements IScheduler, ASocketConst {

	private static final boolean DEBUG = false;
	private static final int INITIAL_SLEEPTIME = 1;
	private static final int INITIAL_SLEEPFREQ = 1;
	private static final int MAX_AGGREGATION = 32;
	private double targetRate;

	ASocketRCScheduler(IStageManager mgr) {
		super(mgr);
		this.targetRate = mgr.getConfig().getInt("global.aSocket.rateController.rate");
		System.err.println("aSocketRCTMSleep: Created, target rate " + targetRate);
	}

	protected aSocketThread makeThread(ASocketStageWrapper wrapper) {
		return new aSocketRCThread(wrapper);
	}

	/**
	 * Internal class representing a single aSocketTM-managed thread.
	 */
	protected class aSocketRCThread extends ASocketScheduler.aSocketThread implements Runnable {
		private final long MIN_USEFUL_SLEEP = 10;

		protected aSocketRCThread(ASocketStageWrapper wrapper) {
			super(wrapper);
		}

		public void run() {
			if (DEBUG) System.err.println(name + ": starting, selsource=" + selsource + ", eventQ=" + eventQ + ", targetRate=" + targetRate);

			long t1, t2;
			int num_measurements = 0, num_events = 0;
			long sleeptime = INITIAL_SLEEPTIME;
			int sleepfreq = INITIAL_SLEEPFREQ;

			List buffer = new ArrayList();

			t1 = System.currentTimeMillis();

			while (true) {

				try {

					while (selsource.numActive() == 0) {
						if (DEBUG) System.err.println(name + ": numActive is zero, waiting on event queue");

						IBatchDescr batch;
						while ((batch = sorter.nextBatch(EVENT_QUEUE_TIMEOUT)) != null) {
							List events = batch.getBatch();
							if (DEBUG) System.err.println(name + ": got " + events.size() + " new requests");
							num_events += events.size();
							handler.handleEvents(events);
							batch.batchDone();
						}
					}

					for (int s = 0; s < SELECT_SPIN; s++) {
						if (DEBUG) System.err.println(name + ": doing select, numActive " + selsource.numActive());
						int num = selsource.blockingDequeueAll(buffer, SELECT_TIMEOUT);
						if (num > 0) {
							if (DEBUG) System.err.println(name + ": select got " + num + " elements");
							num_events += num;
							handler.handleEvents(buffer);
							buffer.clear();
						} else if (DEBUG) System.err.println(name + ": select got null");
					}

					if (DEBUG) System.err.println(name + ": Checking request queue");
					for (int s = 0; s < EVENT_QUEUE_SPIN; s++) {
						IBatchDescr batch = sorter.nextBatch(0);
						if (batch != null) {
							List events = batch.getBatch();
							if (DEBUG) System.err.println(name + ": got " + events.size() + " new requests");
							num_events += events.size();
							handler.handleEvents(events);
							batch.batchDone();
							break;
						}
					}

				} catch (Exception e) {
					System.err.println(name + ": got exception " + e);
					e.printStackTrace();
				}

				if (((num_measurements % sleepfreq) == 0) && (sleeptime > 0)) {
					try {
						Thread.sleep(sleeptime);
					} catch (InterruptedException ie) {
						// Ignore
					}
				}

				t2 = System.currentTimeMillis();
				num_measurements++;

				if ((num_measurements % MEASUREMENT_SIZE) == 0) {
					double timesec = ((t2 - t1) * 1.0e-3);
					double actualrate = num_events / timesec;
					System.err.println("aSocketRCTMSleep (" + name + "): time " + Util.format(timesec) + ", num_events " + num_events);
					//if (DEBUG)
					System.err.println("aSocketRCTMSleep (" + name + "): Rate is " + Util.format(actualrate) + ", target " + targetRate + ", sleeptime " + sleeptime);

					if ((actualrate >= (1.05 * targetRate)) ||
					    (actualrate <= (0.95 * targetRate))) {
						// Update delay
						double delay = (num_events / targetRate) - timesec;
						sleeptime = (long) (delay * 1.0e3);
						if (sleeptime < 0) sleeptime = 0;
						if ((sleeptime > 0) &&
						    ((sleeptime / MEASUREMENT_SIZE) < MIN_USEFUL_SLEEP)) {
							sleeptime = MIN_USEFUL_SLEEP;
							sleepfreq = (int) (MIN_USEFUL_SLEEP / sleeptime);
							if (sleepfreq < 1) sleepfreq = 1;
						} else {
							sleeptime /= MEASUREMENT_SIZE;
							sleepfreq = 1;
						}
						System.err.println("aSocketRCTMSleep (" + name + "): Adjusted sleeptime to " + sleeptime + ", sleepfreq " + sleepfreq);
					}

					t1 = System.currentTimeMillis();
					num_events = 0;
				}

			}
		}

	}

}

