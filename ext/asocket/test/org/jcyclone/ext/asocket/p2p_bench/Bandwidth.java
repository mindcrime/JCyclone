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

/* This is a simple test program which demonstrates the use of the
 * aSocket interface. It implements a simple bandwidth benchmark
 * which sends bursts of packets and receives a small ack for each
 * burst.
 */

package org.jcyclone.ext.asocket.p2p_bench;

import org.jcyclone.core.boot.JCyclone;
import org.jcyclone.core.cfg.JCycloneConfig;
import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.queue.*;
import org.jcyclone.ext.asocket.ATcpClientSocket;
import org.jcyclone.ext.asocket.ATcpConnection;
import org.jcyclone.ext.asocket.ATcpInPacket;
import org.jcyclone.ext.asocket.ATcpServerSocket;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Bandwidth {

	String peer;
	boolean sending;
	IQueue comp_q = null;
	ISink sink;
	ATcpConnection conn;
	ATcpClientSocket clisock;
	ATcpServerSocket servsock;

	private static final boolean DEBUG = false;
	private static final boolean VERBOSE = true;
	private static final boolean RX_DEBUG = false;
	private static boolean BLOCKING_DEQUEUE = true;
	private static final boolean SEND_ACKS = true;

	private static final int PORTNUM = 5721;
	private static int ACK_SIZE = 32;
	private static int MSG_SIZE;
	private static int WINDOW_SIZE;

	public Bandwidth(String peer, boolean sending) {
		this.peer = peer;
		this.sending = sending;
	}

	public void setup() throws IOException, UnknownHostException {
		int max_n;

		comp_q = new LinkedBlockingQueue();

		if (sending) {
			clisock = new ATcpClientSocket(peer, PORTNUM, comp_q);
		} else {
			servsock = new ATcpServerSocket(PORTNUM, comp_q);
		}

	}

	public void doIt() throws SinkClosedException, IOException, InterruptedException {
		int i;
		int n = 0;

		if (DEBUG) System.err.println("Bandwidth: doIt called");

		byte barr[] = new byte[MSG_SIZE];
		if (sending) {
			for (i = 0; i < MSG_SIZE; i++) {
				barr[i] = (byte) (i & 0xff);
			}
		}
		BufferElement buf;

		if (sending) {
			buf = new BufferElement(barr, 0, MSG_SIZE);
		} else {
			buf = new BufferElement(barr, 0, ACK_SIZE);
		}

		List fetched = new ArrayList();
		int numFetched = 0;
		IElement el;
		long before, after;

		/* Wait for connection */
		if (DEBUG) System.err.println("Bandwidth: Waiting for connection to complete");
		while (true) {
			fetched.clear();
			while (numFetched == 0) {
				numFetched = comp_q.blockingDequeueAll(fetched, 0);
			}
			if (numFetched != 1) throw new IOException("Got more than one event on initial fetch?");
			el = (IElement) fetched.get(0);
			if (!(el instanceof ATcpConnection)) continue;
			break;
		}
		conn = (ATcpConnection) el;
		System.err.println("Got connection from " + conn.getAddress() + ":" + conn.getPort());
		conn.startReader(comp_q);
		sink = (ISink) conn;

		before = System.currentTimeMillis();

		if (sending && !SEND_ACKS) {
			/* Just blast the data! */
			while (true) {
				while (!sink.enqueueLossy(buf)) ;
				n++;
				if (n % 500 == 0) {
					after = System.currentTimeMillis();
					printTime(before, after, 500, MSG_SIZE);
					before = after;
				}
			}
		}

		if (sending) {
			for (int m = 0; m < WINDOW_SIZE; m++) {
				sink.enqueueLossy(buf);
			}
		}

		int total_size = 0;
		boolean first_window = true;

		while (true) {

			if (DEBUG) System.err.println("Bandwidth: Waiting for dequeue...");

			fetched.clear();

			if (BLOCKING_DEQUEUE) {
				numFetched = comp_q.blockingDequeueAll(fetched, -1);
			} else {
				while (numFetched == 0) {
					numFetched = comp_q.dequeueAll(fetched);
					if (numFetched == 0)
						Thread.yield();
				}
			}

			for (i = 0; i < numFetched; i++) {

				el = (IElement) fetched.get(i);

				if (el instanceof ATcpInPacket) {

					ATcpInPacket pkt = (ATcpInPacket) el;
					int size = pkt.size();

					if (DEBUG) System.err.println("GOT PACKET: " + size + " bytes");

					if (!sending) {
						total_size += size;

						if (total_size == MSG_SIZE * WINDOW_SIZE) {
							if (first_window == true) {
								System.err.println("Finished receiving first burst");
								first_window = false;
							}
							n += WINDOW_SIZE;

							if ((n % (WINDOW_SIZE * 100)) == 0) {
								after = System.currentTimeMillis();
								if (VERBOSE) printTime(before, after, WINDOW_SIZE * 100, MSG_SIZE);
								before = after;
							}

							if (SEND_ACKS) {
								if (DEBUG) System.err.println("SENDING AN ACK");
								sink.enqueueLossy(buf);
							}

							total_size = 0;
						} else if (total_size > MSG_SIZE * WINDOW_SIZE) {
							throw new IOException("Huh?? Got total_size " + total_size + ", should be no more than " + MSG_SIZE * WINDOW_SIZE);
						}
					} else {
						if (size != ACK_SIZE) {
							throw new IOException("Huh? Got ack size " + size);
						}

						n += WINDOW_SIZE;
						if (DEBUG) System.err.println("n/WINDOW_SIZE=" + (n / WINDOW_SIZE));
						if ((n % (WINDOW_SIZE * 100)) == 0) {
							after = System.currentTimeMillis();
							if (VERBOSE) printTime(before, after, WINDOW_SIZE * 100, MSG_SIZE);
							before = after;
						}

						if (DEBUG) System.err.println("SENDING NEXT BURST");
						for (int m = 0; m < WINDOW_SIZE; m++) {
							sink.enqueueLossy(buf);
						}
					}


				} else if (el instanceof SinkDrainedEvent) {
					if (DEBUG) System.err.println("Got NetworkWriteDrainedEvent!");
				} else {
					throw new IOException("Sender got unknown comp_q event: " + el.toString());
				}
			}
		}

	}

	private static void printTime(long t1, long t2, int numiters, int msg_size) {
		long diff = t2 - t1;
		double iters_per_ms = (double) numiters / (double) diff;
		double iters_per_sec = iters_per_ms * 1000.0;
		double rtt_usec = (diff * 1000.0) / ((double) numiters);
		double mbps = (numiters * msg_size * 8.0) / ((double) diff * 1.0e3);

		System.err.println(numiters + " iterations in " + diff +
		    " milliseconds = " + iters_per_sec
		    + " iterations per second");
		System.err.println("\t" + rtt_usec + " usec RTT, " + mbps + " mbps bandwidth");
	}

	private static void usage() {
		System.err.println("usage: Bandwidth [send|recv] <remote_hostname> <msgsize> <burstsize>");
		System.exit(1);
	}

	public static void main(String args[]) {
		Bandwidth np;
		boolean sending = false;
		if (args.length != 4) usage();

		if (args[0].equals("send")) sending = true;
		MSG_SIZE = Integer.decode(args[2]).intValue();
		WINDOW_SIZE = Integer.decode(args[3]).intValue();

		try {
			JCycloneConfig cfg = new JCycloneConfig();
			JCyclone ss = new JCyclone(cfg);

			System.err.println("Bandwidth: message size=" + MSG_SIZE + ", burst size=" + WINDOW_SIZE + ", rx block=" + BLOCKING_DEQUEUE);

			np = new Bandwidth(args[1], sending);
			np.setup();
			np.doIt();
			System.exit(0);

		} catch (Exception e) {
			if (VERBOSE) System.err.println("Bandwidth.main() got exception: " + e);
			if (VERBOSE) e.printStackTrace();
		}
	}

}
