package org.jcyclone.ext.atls;

/*
 * Copyright (c) 2002 by The Regents of the University of California. 
 * All rights reserved.
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
 * Author: Dennis Chi <denchi@uclink4.berkeley.edu> 
 *
 */


import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.SinkClosedEvent;
import org.jcyclone.ext.asocket.ATcpInPacket;

import java.util.List;

public class ServerTest implements IEventHandler {

	private static final boolean DEBUG = true;
	private static final int PORT = 8096;

	private ISink mysink;

	public void init(IConfigData config) throws Exception {
		if (DEBUG) System.err.println("serverTest: inside init()");

		mysink = config.getStage().getSink();

		System.err.println("serverTest: Started");

		ATLSServerSocket server = new ATLSServerSocket(config.getManager(), mysink, PORT);
	}

	public void destroy() {
	}

	public void handleEvent(IElement item) {
		if (DEBUG) System.err.println("SERVER TEST GOT QEL: " + item);

		if (item instanceof ATLSConnection) {
			System.err.println("serverTest: received a connection");
			((ATLSConnection) item).startReader(mysink);
		} else if (item instanceof ATcpInPacket) {
			readData((ATcpInPacket) item);
		} else if (item instanceof SinkClosedEvent) {
			if (DEBUG) System.err.println("serverTest: CONNECTION HAS BEEN CLOSED");
		}
	}

	public void handleEvents(List items) {
		if (DEBUG) System.err.println("GOT " + items.size() + " ELEMENTS");
		for (int i = 0; i < items.size(); i++) {
			handleEvent((IElement) items.get(i));
		}
	}

	// This is just to test the encryption/decryption of sending actual data between client/server
	public void readData(ATcpInPacket req) {
		byte[] data = req.getBytes();
		String request = new String(data);
		System.err.println("Server just received a request: " + request);

		String response = "<html><body bgcolor=\"white\"><h3>aTLS Web Server Response</h3><p><b>Hello, this is the aTLS test web server.</b><br><p>Your complete request was as follows: <p><pre> " + request + "</pre><p>And, by the way, your request (and this reply) were encrypted using TLS! Glad to be of service today.</body></html>\r\n\r\n";

		String x = "HTTP/1.1 200 OK\n" + "Server: Sandstorm (unknown version)\n" + "Content-Type: text/html\n" + "Content-Length: 30\r\n\r\n";

		req.getConnection().enqueueLossy(new BufferElement(x.getBytes()));

		req.getConnection().enqueueLossy(new BufferElement(response.getBytes()));
		for (int j = 0; j < 3; j++) {
			System.err.println();
		}
	}

}
