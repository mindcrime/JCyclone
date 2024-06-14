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

import org.jcyclone.core.boot.JCyclone;
import org.jcyclone.core.cfg.ConfigData;
import org.jcyclone.core.cfg.ISystemConfig;
import org.jcyclone.core.cfg.JCycloneConfig;
import org.jcyclone.core.internal.IScheduler;
import org.jcyclone.core.internal.ISystemManager;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.SinkException;
import org.jcyclone.core.stage.IStage;
import org.jcyclone.core.stage.IStageManager;
import org.jcyclone.core.plugin.IPlugin;
import org.jcyclone.util.Tracer;

/**
 * The aSocketMgr is an internal class used to provide an interface between
 * the JCyclone runtime and the aSocket library. Applications should not
 * make use of this class.
 *
 * @author Matt Welsh
 */
public class ASocketMgr implements IPlugin {

	private static final boolean DEBUG = false;
	private static final boolean PROFILE = false;

	private static IScheduler aSocketTM, aSocketRCTM;
	private static ISink read_sink;
	private static ISink listen_sink;
	private static ISink write_sink;

	private static ReadEventHandler read_handler;

	private static Object init_lock = new Object();
	private static boolean initialized = false;

	private static ASocketImplFactory factory;

	public static Tracer tracer;

	public void initialize(IStageManager stagemgr, ISystemManager sysmgr, String pluginName) throws Exception {
		synchronized (init_lock) {
			if (!initialized) {
				static_initialize(stagemgr, sysmgr);
			}
		}
	}

	public void destroy() throws Exception
	{}


	/**
	 * Called at startup time by the JCyclone runtime.
	 */
	private static void static_initialize(IStageManager mgr, ISystemManager sysmgr) throws Exception {

		synchronized (init_lock) {
			if (PROFILE) tracer = new Tracer("aSocketMgr");

			ISystemConfig cfg = mgr.getConfig();

			factory = ASocketImplFactory.getFactory();

			aSocketTM = new ASocketScheduler(mgr);
			sysmgr.addScheduler("aSocket", aSocketTM);

			ReadEventHandler revh = new ReadEventHandler();
			read_handler = revh;	// save this for calls to interruptSelect.
			ASocketStageWrapper rsw;
			if (cfg.getBoolean("global.aSocket.governor.enable")) {
				aSocketRCTM = new ASocketRCScheduler(mgr);
				sysmgr.addScheduler("aSocketRCTM", aSocketRCTM);
				rsw = new ASocketStageWrapper(mgr, "aSocket ReadStage",
				    revh, new ConfigData(mgr), aSocketRCTM);
			} else {
				rsw = new ASocketStageWrapper(mgr, "aSocket ReadStage",
				    revh, new ConfigData(mgr), aSocketTM);
			}

			IStage readStage = sysmgr.createStage(rsw, true);
			read_sink = readStage.getSink();

			ListenEventHandler levh = new ListenEventHandler();
			ASocketStageWrapper lsw = new ASocketStageWrapper(mgr, "aSocket ListenStage",
			    levh, new ConfigData(mgr), aSocketTM);
			IStage listenStage = sysmgr.createStage(lsw, true);
			listen_sink = listenStage.getSink();

			WriteEventHandler wevh = new WriteEventHandler();
			ASocketStageWrapper wsw = new ASocketStageWrapper(mgr, "aSocket WriteStage",
			    wevh, new ConfigData(mgr), aSocketTM);
			IStage writeStage = sysmgr.createStage(wsw, true);
			write_sink = writeStage.getSink();

			initialized = true;
		}
	}

	/**
	 * Ensure that the aSocket layer is initialized, in case the library
	 * is being used in standalone mode.
	 */
	static void init() {
		synchronized (init_lock) {
			// When invoked in standalone mode
			if (!initialized) {
				try {
					JCyclone ss = JCyclone.getInstance();
					if (ss != null) {
						static_initialize(ss.getManager(), ss.getSystemManager());
					} else {
						JCycloneConfig cfg = new JCycloneConfig();
						ss = new JCyclone(cfg);
					}
				} catch (Exception e) {
					System.err.println("aSocketMgr: Warning: Initialization failed: " + e);
					e.printStackTrace();
					return;
				}
			}
		}
	}

	static ASocketImplFactory getFactory() {
		return factory;
	}

	static public void enqueueRequest(ASocketRequest req) {
		if (PROFILE) tracer.trace("enqueueRequest called");
		init();

		if ((req instanceof ATcpWriteRequest) ||
		    (req instanceof ATcpConnectRequest) ||
		    (req instanceof ATcpFlushRequest) ||
		    (req instanceof ATcpCloseRequest) ||
		    (req instanceof AUdpWriteRequest) ||
		    (req instanceof AUdpCloseRequest) ||
		    (req instanceof AUdpFlushRequest) ||
		    (req instanceof AUdpConnectRequest) ||
		    (req instanceof AUdpDisconnectRequest)) {

			try {
				if (PROFILE) WriteEventHandler.tracer.trace("write_sink enqueue");
				write_sink.enqueue(req);
				//Thread.currentThread().yield(); // XXX MDW TESTING
				if (PROFILE) WriteEventHandler.tracer.trace("write_sink enqueue done");
			} catch (SinkException se) {
				System.err.println("aSocketMgr.enqueueRequest: Warning: Got SinkException " + se);
				System.err.println("aSocketMgr.enqueueRequest: This is a bug - contact <mdw@cs.berkeley.edu>");
			}

		} else if ((req instanceof ATcpStartReadRequest) ||
		    (req instanceof AUdpStartReadRequest)) {

			try {
				read_sink.enqueue(req);
			} catch (SinkException se) {
				System.err.println("aSocketMgr.enqueueRequest: Warning: Got SinkException " + se);
				System.err.println("aSocketMgr.enqueueRequest: This is a bug - contact <mdw@cs.berkeley.edu>");
			}
			if (req instanceof ATcpStartReadRequest) {
				ATcpStartReadRequest srreq = (ATcpStartReadRequest) req;
				SockState ss = srreq.conn.sockState;
			} else if (req instanceof AUdpStartReadRequest) {
				AUdpStartReadRequest srreq = (AUdpStartReadRequest) req;
				DatagramSockState ss = srreq.sock.sockState;
			}
			read_handler.interruptSelect();

		} else if ((req instanceof ATcpListenRequest) ||
		    (req instanceof ATcpSuspendAcceptRequest) ||
		    (req instanceof ATcpResumeAcceptRequest) ||
		    (req instanceof ATcpCloseServerRequest)) {

			try {
				listen_sink.enqueue(req);
			} catch (SinkException se) {
				System.err.println("aSocketMgr.enqueueRequest: Warning: Got SinkException " + se);
				System.err.println("aSocketMgr.enqueueRequest: This is a bug - contact <mdw@cs.berkeley.edu>");
			}

		} else {
			throw new IllegalArgumentException("Bad request type " + req);
		}
		if (PROFILE) tracer.trace("enqueueRequest done");
	}
}

