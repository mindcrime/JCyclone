package org.jcyclone.ext.http;

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

import org.jcyclone.core.boot.JCyclone;
import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;

import java.net.URL;
import java.util.List;

/**
 * Simple test program demonstrating use of the Sandstorm http library.
 * Creates a simple HTTP server which coughs up a static web
 * page for each request.
 *
 * @author Matt Welsh
 */
public class TestServer implements IEventHandler {

	private static final boolean DEBUG = false;
	private static final int PORT = 8080;

	private ISink mysink;

	public void init(IConfigData config) throws Exception {
		mysink = config.getStage().getSink();

		System.err.println("TestServer: Started");

		HttpServer server = new HttpServer(config.getManager(), mysink, PORT);
	}

	public void destroy() {
	}

	public void handleEvent(IElement item) {
		if (DEBUG) System.err.println("GOT QEL: " + item);

		if (item instanceof HttpConnection) {
			System.err.println("TestServer: Got connection " + item);

		} else if (item instanceof HttpRequest) {
			handleRequest((HttpRequest) item);

		} else {
			if (DEBUG) System.err.println("Got unknown event type: " + item);
		}

	}

	public void handleEvents(List items) {
		if (DEBUG) System.err.println("GOT " + items.size() + " ELEMENTS");
		for (int i = 0; i < items.size(); i++) {
			handleEvent((IElement) items.get(i));
		}
	}

	private void handleRequest(HttpRequest req) {
		String url = req.getURL();
		System.err.println("TestServer: Got request " + url);

		String response = "<html><body bgcolor=\"white\"><h3>Sandstorm Web Server Response</h3><p><b>Hello, this is the Sandstorm test web server.</b><br>You requested the following URL: <p><tt>" + url + "</tt><p>Your complete request was as follows: <p><pre>" + req.toString() + "</pre><p>Glad to be of service today.</body></html>";
		BufferElement resp = new BufferElement(response.getBytes());
		HttpOKResponse ok = new HttpOKResponse("text/html", resp);
		req.getConnection().enqueueLossy(new HttpResponder(ok, req));
	}

	public static void main(String[] args) throws Exception {
		URL url = TestServer.class.getResource("sandstorm.cfg");
		new JCyclone(url.getFile());
	}

}

