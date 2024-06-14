/*
 * Copyright (c) 2002 by Matt Welsh and The Regents of the University of 
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

package org.jcyclone.ext.atls.http;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.stage.IStageManager;
import org.jcyclone.ext.atls.ATLSServerSocket;
import org.jcyclone.ext.http.HttpConst;
import org.jcyclone.ext.http.HttpServer;

/**
 * An httpSecureServer is a SandStorm stage which accepts incoming HTTP
 * connections from an aTLS socket. The server has a client sink
 * associated with it, onto which httpConnection and httpRequest events
 * are pushed. When a connection is closed, a SinkClosedEvent is pushed,
 * with the sink pointer set to the httpConnection that closed.
 *
 * @author Matt Welsh (mdw@cs.berkeley.edu)
 * @see HttpServer
 * @see org.jcyclone.ext.http.HttpConnection
 * @see org.jcyclone.ext.http.HttpRequest
 */
public class HttpSecureServer extends HttpServer
    implements IEventHandler, HttpConst {

	private static final boolean DEBUG = false;
	private static final int DEFAULT_SECURE_HTTP_PORT = 443;

	/**
	 * Create an HTTP server listening for incoming connections on
	 * the default port of 443.
	 */
	public HttpSecureServer(IStageManager mgr, ISink clientSink) throws Exception {
		this(mgr, clientSink, DEFAULT_SECURE_HTTP_PORT);
	}

	/**
	 * Create an HTTP server listening for incoming connections on
	 * the given listenPort.
	 */
	public HttpSecureServer(IStageManager mgr, ISink clientSink, int listenPort) throws Exception {
		super(mgr, clientSink, listenPort);
	}

	/**
	 * The Sandstorm stage initialization method.
	 */
	public void init(IConfigData config) throws Exception {
		mySink = config.getStage().getSink();
		servsock = new ATLSServerSocket(mgr, mySink, listenPort);
	}

}
