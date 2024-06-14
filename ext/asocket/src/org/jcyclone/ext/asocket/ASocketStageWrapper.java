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
import org.jcyclone.core.internal.*;
import org.jcyclone.core.queue.IBlockingQueue;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.ISource;
import org.jcyclone.core.queue.LinkedBlockingQueue;
import org.jcyclone.core.rtc.AdmissionControlledSink;
import org.jcyclone.core.rtc.IAdmissionControlledSink;
import org.jcyclone.core.rtc.IResponseTimeController;
import org.jcyclone.core.rtc.QueueThresholdPredicate;
import org.jcyclone.core.stage.IStage;
import org.jcyclone.core.stage.IStageManager;
import org.jcyclone.core.stage.Stage;

/**
 * Internal stage wrapper implementation for aSocket.
 */
class ASocketStageWrapper implements IStageWrapper {

	private String name;
	private IStage stage;
	private IEventHandler handler;
	private IConfigData config;
	private IBlockingQueue eventQ;
	private AdmissionControlledSink admConSink;
	private SelectSourceIF selsource;
	private IScheduler tm;
	private IStageStats stats;
	private IBatchSorter sorter;
	private int status; // lifecycle level

	ASocketStageWrapper(IStageManager mgr, String name, IEventHandler handler, IConfigData config, IScheduler tm) {
		this.name = name;
		this.handler = handler;
		this.config = config;
		this.tm = tm;
		this.stats = new StageStats(this);
		eventQ = new LinkedBlockingQueue();


		int queuelen;
		if ((queuelen = config.getInt("_queuelength")) <= 0) {
			queuelen = -1;
		}

		QueueThresholdPredicate pred = new QueueThresholdPredicate(eventQ, queuelen);
		admConSink = new AdmissionControlledSink(eventQ);
		admConSink.setEnqueuePredicate(pred);

		if (mgr.getConfig().getBoolean("global.batchController.enable")) {
			this.sorter = new AggThrottleBatchSorter();
		} else {
			this.sorter = new NullBatchSorter();
		}

		this.selsource = ((ASocketEventHandler) handler).getSelectSource();
		this.stage = new Stage(name, this, (ISink) admConSink, config);
//		this.config.setStage(this.stage);
		status = LOADED;
	}

	public void program() {
		// XXX: move code from constructor in this method
		status = PROGRAMMED;
	}

	/**
	 * Initialize this stage.
	 */
	public void init() throws Exception {
		if (status >= INITIALIZED) return;
		program();
		handler.init(config);
		status = INITIALIZED;
	}

	public void start() throws Exception {
		if (status >= STARTED) return;
		init();
		tm.register(this);
		status = STARTED;
	}

	public void stop() throws Exception {
		tm.deregister(this);
	}

	/**
	 * Destroy this stage.
	 */
	public void destroy() throws Exception {
		handler.destroy();
	}

	public void deprogram() throws Exception {
		// XXX JM: not implemented yet.
	}

	/**
	 * Return the event handler associated with this stage.
	 */
	public IEventHandler getEventHandler() {
		return handler;
	}

	/**
	 * Return the stage handle for this stage.
	 */
	public IStage getStage() {
		return stage;
	}

	public String getName() {
		return name;
	}

	/**
	 * Return the source from which events should be pulled to
	 * pass to this EventHandlerIF. <b>Note</b> that this method is not
	 * used internally.
	 */
	public ISource getSource() {
		return eventQ;
	}

	/**
	 * Return the thread manager for this stage.
	 */
	public IScheduler getThreadManager() {
		return tm;
	}

	// So aSocketTM can access it
	SelectSourceIF getSelectSource() {
		return selsource;
	}

	// So aSocketTM can access it
	ISource getEventQueue() {
		return eventQ;
	}

	public IStageStats getStats() {
		return stats;
	}

	/**
	 * Not implemented.
	 */
	public void setBatchSorter(IBatchSorter sorter) {
		return;
	}

	public IAdmissionControlledSink getSink() {
		return admConSink;
	}

	public int getLifecycleLevel() {
		// XXX JM: not implemented yet.
		return 0;
	}

	public IBatchSorter getBatchSorter() {
		return sorter;
	}

	/**
	 * Not implemented.
	 */
	public IResponseTimeController getResponseTimeController() {
		return null;
	}

	public String toString() {
		return "ASOCKETSW[" + stage.getName() + "]";
	}

}

