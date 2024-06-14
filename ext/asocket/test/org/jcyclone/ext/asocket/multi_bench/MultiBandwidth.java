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

/* This is a server which accepts multiple connections from clients running
 * the 'Bandwidth' or 'MultiClient' clients and reads packets from them, 
 * sending bursts of a given size. The idea is to demonstrate the 
 * performance of the aSocket layer with multiple client machines loading it. 
 */

package org.jcyclone.ext.asocket.multi_bench;

import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.queue.*;
import org.jcyclone.ext.asocket.ATcpConnection;
import org.jcyclone.ext.asocket.ATcpInPacket;
import org.jcyclone.ext.asocket.ATcpServerSocket;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class MultiBandwidth {

	private static ATcpServerSocket servsock;
	private static IQueue comp_q = null;
	private static Hashtable conn_table;
	private static int num_connections = 0;
	private static int num_active_connections = 0;

	private static final boolean DEBUG = true;
	private static final boolean VERBOSE = true;
	private static boolean BLOCKING_DEQUEUE = true;
	private static boolean FAIRNESS_REPORT = true;

	private static final int PORTNUM = 5721;

	private static int SEND_MESSAGE_SIZE;
	private static int SEND_BURST_SIZE;
	private static int RECV_MESSAGE_SIZE;
	private static int RECV_BURST_SIZE;


	public MultiBandwidth() {
		conn_table = new Hashtable();
	}

	public void setup() throws IOException, UnknownHostException {

		comp_q = new LinkedBlockingQueue();
		servsock = new ATcpServerSocket(PORTNUM, comp_q);
	}

	class ConnState {
		ISink sink;
		int num_messages;
		int cur_offset, cur_length_target;
		int burstsSent;
		boolean firstRead;

		ConnState(ISink sink) {
			this.sink = sink;
			this.num_messages = 0;
			this.cur_offset = 0;
			this.cur_length_target = RECV_MESSAGE_SIZE * RECV_BURST_SIZE;
			this.firstRead = true;
		}

		boolean readDone(int size) {
			if (firstRead) {
				firstRead = false;
				num_active_connections++;
			}

			if (DEBUG) System.err.println("Adding " + size + " bytes to " + this.cur_offset);
			this.cur_offset += size;
			if (DEBUG) System.err.println("Total now " + this.cur_offset + ", target " + this.cur_length_target);

			if (this.cur_offset == this.cur_length_target) {
				if (DEBUG) System.err.println("Reached cur_length_target");
				num_messages++;
				return true;
			} else {
				return false;
			}
		}

		void reset() {
			this.cur_offset = 0;
			this.cur_length_target = RECV_MESSAGE_SIZE * RECV_BURST_SIZE;
		}

	}

	public void doIt() throws SinkClosedException, IOException, InterruptedException {
		int i;
		int n = 0;

		int num_received = 0;
		byte sendmsg[] = new byte[SEND_MESSAGE_SIZE];
		BufferElement sendbuf = new BufferElement(sendmsg);

		IElement el;
		List fetched = new ArrayList();
		int numFetched = 0;
		long before, after;
		long total_bytes = 0;

		before = System.currentTimeMillis();

		while (true) {

			if (DEBUG) System.err.println("Waiting for dequeue...");

			fetched.clear();
			if (BLOCKING_DEQUEUE) {
				numFetched = comp_q.blockingDequeueAll(fetched, -1);
			} else {
				while (true) {
					numFetched = comp_q.dequeueAll(fetched);
					if (numFetched == 0)
						Thread.yield();
					else
						break;
				}
			}

//			System.err.println("FETCHED "+fetched.size()+" NEW EVENTS");


			for (i = 0; i < numFetched; i++) {

				el = (IElement) fetched.get(i);

				if (fetched.get(i) instanceof ATcpConnection) {
					System.err.println("Got connection " + num_connections);
					ATcpConnection conn = (ATcpConnection) el;
					num_connections++;

					conn.startReader(comp_q);
					ConnState cs = new ConnState(conn);
					conn_table.put(conn, cs);

				} else if (el instanceof ATcpInPacket) {
					ATcpInPacket pkt = (ATcpInPacket) el;
					ATcpConnection conn = pkt.getConnection();

//System.err.println("Got packet: "+pkt.size()+" from conn "+conn);
					total_bytes += pkt.size();
					n++;
					//if (n % (num_connections * 100) == 0) {
					after = System.currentTimeMillis();
					if ((after - before) >= 5000) {
						printTime(before, after, total_bytes);
						before = System.currentTimeMillis();
						total_bytes = 0;
					}

					ConnState cs = (ConnState) conn_table.get(conn);
					if (cs.readDone(pkt.size())) {
						if (DEBUG) System.err.println("Sending burst");

						for (int j = 0; j < SEND_BURST_SIZE; j++) {
							if (!cs.sink.enqueueLossy(sendbuf)) {
								System.err.println("WARNING: Could not enqueue message onto sink " + cs.sink);
							}
							cs.burstsSent++;
						}
						cs.reset();
					}

				} else if (el instanceof SinkDrainedEvent) {
					if (DEBUG) System.err.println("Got SinkDrainedEvent!");
				} else if (el instanceof SinkClosedEvent) {
					if (DEBUG) System.err.println("Got SinkClosedEvent!");
					System.err.println("Closed connection " + num_connections);
					SinkClosedEvent sce = (SinkClosedEvent) el;
					conn_table.remove(sce.sink);
					num_connections--;
					num_active_connections--;
				} else {
					System.err.println("Sender got unknown comp_q event: " + el.toString());
				}
			}
		}

	}

	private static void printTime(long t1, long t2, long msg_size) {
		long diff = t2 - t1;
		double mbits = (msg_size * 8.0) / (1024 * 1024);
		double mbps = mbits / (double) (diff * 1.0e-3);

		System.err.println("Completed: " + msg_size + " bytes in " + diff + " msec");
		System.err.println("\t" + mbps + " mbps bandwidth");
		System.err.println("\t" + num_connections + " connections");
		System.err.println("\t" + num_active_connections + " active connections");

		// Compute fairness
		if (FAIRNESS_REPORT) {
			int totalSent = 0, n = 0;
			int numBad = 0;
			Enumeration e = conn_table.elements();
			while (e.hasMoreElements()) {
				ConnState cs = (ConnState) e.nextElement();
				totalSent += cs.burstsSent;
				n++;
				if (cs.burstsSent == 0) numBad++;
			}
			double averageSent = totalSent / n;
			double t = 0;
			e = conn_table.elements();
			while (e.hasMoreElements()) {
				ConnState cs = (ConnState) e.nextElement();
				t += (cs.burstsSent - averageSent) * (cs.burstsSent - averageSent);
				cs.burstsSent = 0;
			}
			double stddev = Math.sqrt(t / (n - 1));
			System.err.println("\t" + averageSent + " avg bursts/conn, stddev " + stddev);
			System.err.println("\t" + numBad + " connections with no bursts");
		}
	}

	private static void usage() {
		System.err.println("usage: MultiBandwidth <send-msg-size> <send-burst-size> <recv-msg-size> <recv-burst-size>");
		System.exit(1);
	}

	public static void main(String args[]) {
		MultiBandwidth np;
		boolean sending = false;

		if (args.length != 4) usage();

		SEND_MESSAGE_SIZE = Integer.decode(args[0]).intValue();
		SEND_BURST_SIZE = Integer.decode(args[1]).intValue();
		RECV_MESSAGE_SIZE = Integer.decode(args[2]).intValue();
		RECV_BURST_SIZE = Integer.decode(args[3]).intValue();

		try {
			System.err.println("MultiBandwidth: send message size=" + SEND_MESSAGE_SIZE + ", send burst size=" + SEND_BURST_SIZE + ", recv message size=" + RECV_MESSAGE_SIZE + ", recv burst size=" + RECV_BURST_SIZE + ", rx block=" + BLOCKING_DEQUEUE);

			np = new MultiBandwidth();
			np.setup();
			np.doIt();

		} catch (Exception e) {
			System.err.println("MultiBandwidth.main() got exception: " + e);
			e.printStackTrace();
		}
	}

}
