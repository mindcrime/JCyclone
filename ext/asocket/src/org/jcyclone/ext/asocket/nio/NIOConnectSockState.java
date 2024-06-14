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

package org.jcyclone.ext.asocket.nio;

import org.jcyclone.core.queue.ITransaction;
import org.jcyclone.core.queue.SinkException;
import org.jcyclone.ext.asocket.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal class used to represent state of a socket while an
 * outgoing connection is pending.
 */
public class NIOConnectSockState extends ConnectSockState {

	private static final boolean DEBUG = false;
	private static final boolean PROFILE = false;

	private SocketChannel nio_sc;
	private SelectionKey selkey;
	private NIOSelectSource write_nio_selsource;

/*  private ATcpClientSocket clisock; 
  private ISink compQ;
  private int connectClogTries, connectNumTries;
  private int writeClogThreshold;
  boolean completed = false; */

	public NIOConnectSockState(ATcpConnectRequest req, SelectSourceIF write_selsource) throws IOException {
		this(req);
		this.write_nio_selsource = (NIOSelectSource) write_selsource;
		selkey = (SelectionKey) write_selsource.register(nio_sc, SelectionKey.OP_CONNECT);
		if (DEBUG) System.err.println("Done creating ConnectSockState");
		selkey.attach(this);
	}

	protected NIOConnectSockState(ATcpConnectRequest req) throws IOException {
		this.clisock = req.clisock;
		this.compQ = req.compQ;
		this.writeClogThreshold = req.writeClogThreshold;
		this.connectClogTries = req.connectClogTries;
		this.connectNumTries = 0;
		try {
			nio_sc = SocketChannel.open();
			nio_sc.configureBlocking(false);
			if (DEBUG) System.err.println("connecting to " + req.addr + ", " + req.port);
			nio_sc.connect(new InetSocketAddress(req.addr, req.port));
		} catch (IOException ioe) {
			// Cannot connect
			compQ.enqueueLossy(new ATcpConnectFailedEvent(clisock, "Got error trying to connect: " + ioe.getMessage()));
			return;
		}
	}

	protected void complete() {
		if (completed) return; // In case we get triggered for complete twice
		if (DEBUG) System.err.println("completing connections");
		ITransaction txn = null;

		try {

			// Do a split-phase enqueue:
			// First prepare an empty connection, prepare it for enqueue,
			// then finish the connect...
			ATcpConnection conn;
			if ((!nio_sc.finishConnect()) && DEBUG) {
				System.err.println("Did not finish connect!!");
			}
			conn = new ATcpConnection(clisock, nio_sc.socket().getInetAddress(), nio_sc.socket().getPort());

			// XXX: it would be more performant to have a method
			// queue.enqueuePrepare(IElement), so we would not have 
			// to create a list each time.
			List tmparr = new ArrayList(1);
			tmparr.add(conn);

			try {
				txn = compQ.enqueuePrepare(tmparr);
			} catch (SinkException se) {
				// Whoops - cannot enqueue it
				if (connectNumTries++ > connectClogTries) {
					// Can't do it; just drop
					System.err.println("aSocket: Warning: dropping connect completion in css.complete, sink is clogged");
					write_nio_selsource.deregister(selkey);
					nio_sc.socket().close();
				}
				// Try again later
				return;
			}

			if (DEBUG) System.err.println("finishing");
			// Reserved entry, complete connection
			if (nio_sc.socket().getRemoteSocketAddress() == null) {
				txn.abort();
				System.err.println("aSocket.CSS.complete: Warning: connectDone returned false!");
				// Try again later
				return;
			}

			Socket sock = nio_sc.socket();
			if (DEBUG) System.err.println("ConnectSockState: connect finished on " + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
			NIOSockState ss = new NIOSockState(conn, sock, writeClogThreshold);
			conn.sockState = ss;

			// Finally enqueue
			txn.commit();

			completed = true;

		} catch (IOException ioe) {
			error(new ATcpConnectFailedEvent(clisock, "Got error trying to connect: " + ioe.getMessage()));
			if (txn != null) txn.abort();

			return;
		}

		// Deregister
		if (DEBUG) System.err.println("WriteThread: CSS.complete: Deregistering si");
//    write_nio_selsource.deregister(selkey);
	}

	protected void error(ASocketErrorEvent error) {
		write_nio_selsource.deregister(selkey);
		compQ.enqueueLossy(error);
	}
}
