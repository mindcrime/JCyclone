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
import org.jcyclone.core.queue.IElement;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Internal event handler for socket listen events.
 */
class ListenEventHandler extends ASocketEventHandler implements IEventHandler {

	private static final boolean DEBUG = false;

	ListenEventHandler() {
	}

	public void init(IConfigData config) {
	}

	public void destroy() {
	}

	private void processAccept(ListenSockState lss) throws IOException {
		if (DEBUG) System.err.println("ListenEventHandler: processAccept called");

		Socket sock;

		int numAccepted = 0;

		// Try to do as many accepts as we can in one go
		while (numAccepted++ < ASocketConst.MAX_ACCEPTS_AT_ONCE) {
			sock = lss.accept();

			if (sock == null) break;

			if (DEBUG) System.err.println("ListenThread: did accept on " + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
			ATcpConnection conn = new ATcpConnection(lss.servsock, sock.getInetAddress(), sock.getPort());
			if (DEBUG) System.err.println("ListenThread: Created new conn " + conn);
			SockState ss = ASocketMgr.getFactory().newSockState(conn, sock, lss.writeClogThreshold);
			if (DEBUG) System.err.println("ListenThread: Created new sockstate " + ss);
			conn.sockState = ss;
			if (DEBUG) System.err.println("ListenThread: Calling lss complete");
			lss.complete(conn);
		}

		if (DEBUG) System.err.println("ListenEventHandler: processAccept finished");
	}

	private void processListenRequest(ASocketRequest req) throws IOException {

		if (req instanceof ATcpListenRequest) {
			// This registers itself
			ListenSockState lss;
			lss = ASocketMgr.getFactory().newListenSockState((ATcpListenRequest) req, selsource);

		} else if (req instanceof ATcpSuspendAcceptRequest) {
			ATcpSuspendAcceptRequest susreq = (ATcpSuspendAcceptRequest) req;

			ListenSockState lss = susreq.servsock.lss;
			if (lss == null) {
				throw new Error("ListenEventHandler: Got ATcpSuspendAcceptRequest for server socket " + susreq.servsock + " with null lss!");
			}
			lss.suspend();

		} else if (req instanceof ATcpResumeAcceptRequest) {
			ATcpResumeAcceptRequest resreq = (ATcpResumeAcceptRequest) req;

			ListenSockState lss = resreq.servsock.lss;
			if (lss == null) {
				throw new Error("ListenEventHandler: Got ATcpResumeAcceptRequest for server socket " + resreq.servsock + " with null lss!");
			}
			lss.resume();

		} else if (req instanceof ATcpCloseServerRequest) {
			ATcpCloseServerRequest creq = (ATcpCloseServerRequest) req;

			ListenSockState lss = creq.servsock.lss;
			// OK for lss to be null if closed down already
			if (lss != null) lss.close();

		} else {
			throw new IllegalArgumentException("Bad request type to enqueueListen");
		}
	}

	public void handleEvent(IElement qel) {
		if (DEBUG) System.err.println("ListenEventHandler: Got QEL: " + qel);

		try {

			if (qel instanceof SelectEvent) {
				ListenSockState lss;
				lss = (ListenSockState) ((SelectEvent) qel).getAttachment();
				((SelectEvent) qel).clearEvents();

				processAccept(lss);

			} else if (qel instanceof ASocketRequest) {
				processListenRequest((ASocketRequest) qel);

			} else {
				throw new IllegalArgumentException("ReadEventHandler: Got unknown event type " + qel);
			}

		} catch (Exception e) {
			System.err.println("ListenEventHandler: Got exception: " + e);
			e.printStackTrace();
		}
	}

	public void handleEvents(List events) {
		for (int i = 0; i < events.size(); i++) {
			handleEvent((IElement) events.get(i));
		}
	}


}

