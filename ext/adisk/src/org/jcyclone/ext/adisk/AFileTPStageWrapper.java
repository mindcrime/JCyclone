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

package org.jcyclone.ext.adisk;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.internal.*;
import org.jcyclone.core.queue.ISource;
import org.jcyclone.core.rtc.IAdmissionControlledSink;
import org.jcyclone.core.rtc.IResponseTimeController;
import org.jcyclone.core.stage.IStage;
import org.jcyclone.core.stage.Stage;

/**
 * Internal stage wrapper implementation for AFileTPImpl.
 *
 * @author Matt Welsh
 */
class AFileTPStageWrapper implements IStageWrapper {

	private String name;
	private IStage stage;
	private IEventHandler handler;
	private IConfigData config;
	private IScheduler tm;
	private IBatchSorter sorter;

	// This stagewrapper has no (real) event queue: Threads created
	// by AFileTPTM will poll across the per-AFile queues instead.
	// This class is just used for bookkeeping purposes.

	AFileTPStageWrapper(String name, IEventHandler handler, IConfigData config, IScheduler tm) {
		this.name = name;
		this.handler = handler;
		this.config = config;
		this.tm = tm;
		this.sorter = new NullBatchSorter();
		this.stage = new Stage(name, this, null, config);
//		this.config.setStage(this.stage);
	}

	/**
	 * Initialize this stage.
	 */
	public void init() throws Exception {
		if (handler != null) handler.init(config);
		tm.register(this);
	}

	public void start() throws Exception {
		// XXX JM: not implemented yet
	}

	public void stop() throws Exception {
		// XXX JM: not implemented yet
	}

	/**
	 * Destroy this stage.
	 */
	public void destroy() throws Exception {
		tm.deregister(this);
		if (handler != null) handler.destroy();
	}

	public void deprogram() throws Exception {
		// XXX JM: not implemented yet
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

	// Not used
	public ISource getSource() {
		// Not used
		return null;
	}

	/**
	 * Return the thread manager for this stage.
	 */
	public IScheduler getThreadManager() {
		return tm;
	}

	// Not used
	public IStageStats getStats() {
		return null;
	}

	// Not used
	public IResponseTimeController getResponseTimeController() {
		return null;
	}

	// Not used
	public void setBatchSorter(IBatchSorter sorter) {
		return;
	}

	// Not used
	public IAdmissionControlledSink getSink() {
		return null;
	}

	// Not used
	public int getLifecycleLevel() {
		return 0;
	}

	// Not used
	public void program() throws Exception {
	}

	/**
	 * Return the batch sorter.
	 */
	public IBatchSorter getBatchSorter() {
		return sorter;
	}


	public String toString() {
		return "AFILETPSW[" + stage.getName() + "]";
	}

}

