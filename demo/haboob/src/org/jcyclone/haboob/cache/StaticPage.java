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

package org.jcyclone.haboob.cache;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.SinkClosedEvent;
import org.jcyclone.ext.asocket.ATcpConnection;
import org.jcyclone.ext.http.HttpBadRequestResponse;
import org.jcyclone.ext.http.HttpOKResponse;
import org.jcyclone.ext.http.HttpRequest;
import org.jcyclone.ext.http.HttpResponder;
import org.jcyclone.haboob.HaboobConst;
import org.jcyclone.haboob.HaboobStats;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Random;

/**
 * This stage responds to HTTP requests with static pages.
 * This is meant to be used for debugging only.
 */
public class StaticPage implements IEventHandler, HaboobConst {

	private static final boolean DEBUG = false;

	// Size of static page in bytes
	private static final int DEBUG_STATIC_PAGE_SIZE = 8192;
	// Send static page as raw TCP packet, not httpResponse
	private static final boolean DEBUG_STATIC_PAGE_RAW = false;
	// Allocate a new response for each response
	private static final boolean DEBUG_STATIC_PAGE_ALLOCATE = false;
	// If true, break up write of raw page into small chunks
	private static final boolean DEBUG_STATIC_PAGE_RAW_MULTIWRITE = false;
	private static final int DEBUG_multiWriteSize = 8192;

	// Send two different page sizes with fixed distribution
	private static final boolean DEBUG_STATIC_PAGE_BIMODAL = false;
	private static final int DEBUG_STATIC_PAGE_SIZE2 = 200;
	private static final double DEBUG_bimodalSmallPageFreq = 0.1;

	// Send response based on size computed from SPECweb99 files
	private static final boolean DEBUG_STATIC_PAGE_CLASS = false;
	// Base sizes of files in each class
	private static final int DEBUG_classBaseSize[] = {1024, 10240, 102400, 1024000};
	// Number of files per class
	private static final int DEBUG_filesPerClass = 9;
	// Max size of a file
	private static final int DEBUG_maxClassFileSize = (DEBUG_classBaseSize[3] * DEBUG_filesPerClass) / 10;
	private static int DEBUG_classFileSizes[][];
	private static HttpOKResponse DEBUG_classResps[][];

	private ISink sendSink;
	private Random rand;

	private BufferElement static_page;
	private HttpOKResponse static_page_response;
	private HttpOKResponse static_page_response2;
	private byte static_page_payload[];
	private BufferElement static_page_raw_chunks[];

	static {
		// Initialize DEBUG_classFileSizes
		DEBUG_classFileSizes = new int[DEBUG_classBaseSize.length][];
		for (int c = 0; c < DEBUG_classBaseSize.length; c++) {
			DEBUG_classFileSizes[c] = new int[DEBUG_filesPerClass];
			for (int n = 0; n < DEBUG_filesPerClass; n++) {
				int sz = (DEBUG_classBaseSize[c] * (n + 1)) / 10;
				DEBUG_classFileSizes[c][n] = sz;
			}
		}
	}

	// Given a URL, return the size of the associated SPECweb99 file
	public static int classURLToSize(String url) {
		int cn = url.indexOf("class");
		if (cn == -1) {
			return 0;
		}
		int classnum = new Integer(url.substring(cn + 5, cn + 6)).intValue();
		int filenum = new Integer(url.substring(cn + 7, cn + 8)).intValue();
		// Limit to filesPerClass
		filenum = Math.min(DEBUG_filesPerClass - 1, filenum);
		return DEBUG_classFileSizes[classnum][filenum];
	}

	public void init(IConfigData config) throws Exception {
		sendSink = config.getManager().getStage(HTTP_SEND_STAGE).getSink();
		rand = new Random();

		if (DEBUG_STATIC_PAGE_RAW) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PrintWriter pw = new PrintWriter(baos);
			// Apache header
			pw.println("HTTP/1.1 200 OK");
			pw.println("Date: Fri, 20 Oct 2000 18:33:14 GMT");
			pw.println("Server: Apache/1.3.6 (Unix)  (Red Hat/Linux) PHP/3.0.9");
			pw.println("Last-Modified: Thu, 12 Oct 2000 19:39:35 GMT");
			pw.println("ETag: \"5a80f-2000-39e61377\"");
			pw.println("Accept-Ranges: bytes");
			pw.println("Content-Length: " + DEBUG_STATIC_PAGE_SIZE);
			pw.println("Content-Type: text/html\n");
			pw.flush();
			byte header[] = baos.toByteArray();
			byte response[] = new byte[DEBUG_STATIC_PAGE_SIZE + header.length];
			System.arraycopy(header, 0, response, 0, header.length);
			for (int i = header.length; i < DEBUG_STATIC_PAGE_SIZE + header.length - 1; i++) {
				response[i] = (byte) 'A';
			}
			response[DEBUG_STATIC_PAGE_SIZE + header.length - 1] = (byte) '\n';
			static_page = new BufferElement(response);

			if (DEBUG_STATIC_PAGE_RAW_MULTIWRITE) {
				int numChunks = response.length / DEBUG_multiWriteSize;
				if ((response.length % DEBUG_multiWriteSize) != 0) {
					numChunks++;
				}
				static_page_raw_chunks = new BufferElement[numChunks];
				int off = 0;
				for (int i = 0; i < numChunks; i++) {
					int sz = Math.min(DEBUG_multiWriteSize, (response.length - off));
					static_page_raw_chunks[i] = new BufferElement(sz);
					System.arraycopy(response, off, static_page_raw_chunks[i].data, 0, sz);
					off += sz;
				}
			}

		} else if (DEBUG_STATIC_PAGE_CLASS) {
			DEBUG_classResps = new HttpOKResponse[DEBUG_classBaseSize.length][];
			for (int c = 0; c < DEBUG_classBaseSize.length; c++) {
				DEBUG_classResps[c] = new HttpOKResponse[DEBUG_filesPerClass];
				for (int n = 0; n < DEBUG_filesPerClass; n++) {
					int sz = (DEBUG_classBaseSize[c] * (n + 1)) / 10;
					DEBUG_classResps[c][n] = new HttpOKResponse("text/plain", sz);
				}
			}

		} else {
			static_page_response = new HttpOKResponse("text/plain", DEBUG_STATIC_PAGE_SIZE);
			static_page_payload = new byte[DEBUG_STATIC_PAGE_SIZE];
			for (int i = 0; i < DEBUG_STATIC_PAGE_SIZE; i++) {
				static_page_payload[i] = (byte) 'a';
			}
			BufferElement payload = static_page_response.getPayload();
			byte paydata[] = payload.data;
			System.arraycopy(static_page_payload, 0, paydata, payload.offset, payload.size);

			if (DEBUG_STATIC_PAGE_BIMODAL) {
				static_page_response2 = new HttpOKResponse("text/plain", DEBUG_STATIC_PAGE_SIZE2);
				byte static_page_payload2[] = new byte[DEBUG_STATIC_PAGE_SIZE2];
				for (int i = 0; i < DEBUG_STATIC_PAGE_SIZE2; i++) {
					static_page_payload2[i] = (byte) 'a';
				}
				payload = static_page_response2.getPayload();
				paydata = payload.data;
				System.arraycopy(static_page_payload2, 0, paydata, payload.offset, payload.size);
			}
		}

	}

	public void destroy() {
	}

	public void handleEvent(IElement item) {
		if (DEBUG) System.err.println("PageCache: GOT QEL: " + item);

		if (item instanceof HttpRequest) {
			HaboobStats.numRequests++;

			HttpRequest req = (HttpRequest) item;
			if (req.getRequest() != HttpRequest.REQUEST_GET) {
				HaboobStats.numErrors++;
				sendSink.enqueueLossy(new HttpResponder(new HttpBadRequestResponse(req, "Only GET requests supported at this time"), req, true));
				return;
			}

			if (DEBUG_STATIC_PAGE_RAW) {
				ATcpConnection atcpconn = req.getConnection().getConnection();

				if (DEBUG_STATIC_PAGE_RAW_MULTIWRITE) {
					for (int i = 0; i < static_page_raw_chunks.length; i++) {
						if (!atcpconn.enqueueLossy(static_page_raw_chunks[i])) {
							System.err.println("PageCache: Warning: Cannot enqueue_lossy static page " + static_page);
						}
					}
				} else {
					if (!atcpconn.enqueueLossy(static_page)) {
						System.err.println("PageCache: Warning: Cannot enqueue_lossy static page " + static_page);
					}
				}

			} else if (DEBUG_STATIC_PAGE_CLASS) {
				String url = req.getURL();
				int cn = url.indexOf("class");
				if (cn == -1) {
					System.err.println("Got bad url: " + url);
					return;
				}
				int classnum = new Integer(url.substring(cn + 5, cn + 6)).intValue();
				int filenum = new Integer(url.substring(cn + 7, cn + 8)).intValue();
				// Limit to filesPerClass
				filenum = Math.min(DEBUG_filesPerClass - 1, filenum);
				HttpOKResponse res;

				if (DEBUG_STATIC_PAGE_ALLOCATE) {
					int sz = DEBUG_classFileSizes[classnum][filenum];
					res = new HttpOKResponse("text/html", sz);
				} else {
					res = DEBUG_classResps[classnum][filenum];
				}
				if (DEBUG) System.err.println("Got url " + url + ", class " + classnum + ", filenum " + filenum + ", sz " + res.getPayload().size);
				HttpResponder resp = new HttpResponder(res, req);
				if (!sendSink.enqueueLossy(resp)) {
					System.err.println("PageCache: Warning: Cannot enqueue_lossy static page " + resp);
				}

			} else if (DEBUG_STATIC_PAGE_BIMODAL) {
				boolean sendSmall = (rand.nextDouble() <= DEBUG_bimodalSmallPageFreq) ? (true) : (false);
				HttpResponder resp;
				if (sendSmall) {
					resp = new HttpResponder(static_page_response2, req);
				} else {
					resp = new HttpResponder(static_page_response, req);
				}
				if (!sendSink.enqueueLossy(resp)) {
					System.err.println("PageCache: Warning: Cannot enqueue_lossy static page " + resp);
				}

			} else {
				// Just send static response

				HttpResponder resp;
				if (!DEBUG_STATIC_PAGE_ALLOCATE) {
					resp = new HttpResponder(static_page_response, req);
				} else {
					resp = new HttpResponder(new HttpOKResponse("text/html", DEBUG_STATIC_PAGE_SIZE), req);
				}
				if (!sendSink.enqueueLossy(resp)) {
					System.err.println("PageCache: Warning: Cannot enqueue_lossy static page " + resp);
				}
			}

			return;

		} else if (item instanceof SinkClosedEvent) {
			// Ignore

		} else {
			System.err.println("StaticPage: Got unknown event type: " + item);
		}

	}

	public void handleEvents(List events) {
		for (int i = 0; i < events.size(); i++) {
			handleEvent((IElement) events.get(i));
		}
	}

}

