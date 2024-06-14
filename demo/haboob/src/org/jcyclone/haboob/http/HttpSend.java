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

package org.jcyclone.haboob.http;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.SinkClosedEvent;
import org.jcyclone.core.queue.SinkClosedException;
import org.jcyclone.ext.http.HttpConnection;
import org.jcyclone.ext.http.HttpResponder;
import org.jcyclone.haboob.HaboobConst;
import org.jcyclone.haboob.HaboobStats;

import java.util.Hashtable;
import java.util.List;

/**
 * This stage simply forwards httpResponders to the appropriate
 * httpConnection. Note that when ENQUEUE_RESPONSES is false (the
 * default), then this stage is not actually used -- rather, the
 * sendResponse() call directly implements forwarding.
 */
public class HttpSend implements IEventHandler, HaboobConst {

	private static final boolean DEBUG = false;

	// If true, handle sends from this stage - otherwise inline into
	// sendResponse() call
	private static final boolean ENQUEUE_RESPONSES = false;

	private static ISink mysink, cacheSink;
	private static Hashtable respTable;
	private static int maxReqs;

	public HttpSend() {
		if (HaboobStats.httpSend != null) {
			throw new Error("HttpSend: More than one HttpSend running?");
		}

		HaboobStats.httpSend = this;
	}

	public void init(IConfigData config) throws Exception {
		mysink = config.getStage().getSink();
		cacheSink = config.getManager().getStage(CACHE_STAGE).getSink();
		maxReqs = config.getInt("maxRequests");
		if (maxReqs != -1) respTable = new Hashtable();
	}

	public void destroy() {
	}

	// Library call used to send HTTP response
	public static void sendResponse(HttpResponder resp) {
		if (ENQUEUE_RESPONSES) {
			mysink.enqueueLossy(resp);
		} else {
			// Inline the call
			doResponse(resp);
		}
	}

	private static final void doResponse(HttpResponder resp) {
		HaboobStats.httpRecv.doneWithReq();

		if (DEBUG) System.err.println("HttpSend: Got response " + resp);
		if (!resp.getConnection().enqueueLossy(resp)) {
			// This is OK if we have already closed the connection
			if (DEBUG) System.err.println("HttpSend: Could not enqueue response " + resp.getResponse() + " to connection " + resp.getConnection());
			return;
		}

		if (resp.shouldClose()) {
			HaboobStats.numConnectionsClosed++;
			HttpConnection conn = resp.getConnection();
			try {
				conn.close(cacheSink);
				if (maxReqs != -1) respTable.remove(conn);
			} catch (SinkClosedException sce) {
				if (DEBUG) System.err.println("Warning: Tried to close connection " + conn + " multiple times");
			}
			return;
		}

		if (maxReqs != -1) {
			HttpConnection conn = resp.getConnection();
			Integer count = (Integer) respTable.remove(conn);
			if (count == null) {
				respTable.put(conn, new Integer(0));
			} else {
				int prevConns = HaboobStats.numConnectionsEstablished - HaboobStats.numConnectionsClosed;
				int c = count.intValue();
				c++;
				if (c >= maxReqs) {
					HaboobStats.httpRecv.closeConnection(conn);
				} else {
					respTable.put(conn, new Integer(c));
				}
			}
		}
	}

	public void handleEvent(IElement item) {
		if (DEBUG) System.err.println("HttpSend: GOT QEL: " + item);

		if (item instanceof HttpResponder) {
			doResponse((HttpResponder) item);

		} else if (item instanceof SinkClosedEvent) {
			// Connection closed by remote peer
			SinkClosedEvent sce = (SinkClosedEvent) item;
			HttpConnection hc = (HttpConnection) sce.sink;
			if (maxReqs != -1) respTable.remove(hc);

		} else {
			System.err.println("HttpSend: Got unknown event type: " + item);
		}

	}

	public void handleEvents(List events) {
		for (int i = 0; i < events.size(); i++) {
			handleEvent((IElement) events.get(i));
		}
	}

	/**
	 * Inner class representing the number of requests on a given
	 * connection.
	 */
	class requestCount {
		int reqs;
	}

}

