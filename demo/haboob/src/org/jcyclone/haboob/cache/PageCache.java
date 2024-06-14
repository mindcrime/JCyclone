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
import org.jcyclone.core.queue.SinkException;
import org.jcyclone.ext.adisk.AFile;
import org.jcyclone.ext.adisk.AFileIOCompleted;
import org.jcyclone.ext.adisk.AFileStat;
import org.jcyclone.ext.http.*;
import org.jcyclone.haboob.HaboobConst;
import org.jcyclone.haboob.HaboobStats;
import org.jcyclone.util.FastLinkedList;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

/**
 * This implementation of the Haboob page cache simply caches
 * recently accessed Web pages, and randomly removes pages from the
 * cache when the cache grows too large. This implementation does
 * not work as well as PageCacheSized.
 */
public class PageCache implements IEventHandler, HaboobConst {

	private static final boolean DEBUG = false;
	private static final boolean PROFILE = false;

	// Don't actually read file; just store empty buffer in cache
	private static final boolean DEBUG_NO_FILE_READ = false;
	// Don't even stat file; just allocate buffer of fixed size
	private static final boolean DEBUG_NO_FILE_READ_SAMESIZE = false;
	private static final int DEBUG_NO_FILE_READ_SAMESIZE_SIZE = 8192;

	// Rewrite incoming filename so all cache entries hit
	private static final boolean DEBUG_SINGLE_CACHE_PAGE = false;
	// If true, rewrite all request URLs to DEBUG_SINGLE_CACHE_PAGE_FNAME
	// If false, all cache misses access same file, but different entries
	private static final boolean DEBUG_SINGLE_CACHE_PAGE_SAMENAME = false;
	// This file is of size 8192 bytes
	private static final String DEBUG_SINGLE_CACHE_PAGE_FNAME = "/dir00000/class1_7";

	private String DEFAULT_URL;
	private String ROOT_DIR;

	private ISink mysink, sendSink;
	private Hashtable pageTbl; // Map URL -> cacheEntry
	private Hashtable aFileTbl;  // Map aFile -> cacheEntry
	private int maxCacheSize;
	private Random rand;

	private Hashtable mimeTbl; // Filename extension -> MIME type
	private static final String defaultMimeType = "text/plain";

	public void init(IConfigData config) throws Exception {
		mysink = config.getStage().getSink();
		sendSink = config.getManager().getStage(HTTP_SEND_STAGE).getSink();
		pageTbl = new Hashtable();
		aFileTbl = new Hashtable();
		rand = new Random();

		mimeTbl = new Hashtable();
		mimeTbl.put(".html", "text/html");
		mimeTbl.put(".gif", "image/gif");
		mimeTbl.put(".jpg", "image/jpeg");
		mimeTbl.put(".jpeg", "image/jpeg");
		mimeTbl.put(".pdf", "application/pdf");

		DEFAULT_URL = config.getString("defaultURL");
		if (DEFAULT_URL == null) throw new IllegalArgumentException("Must specify defaultURL");
		ROOT_DIR = config.getString("rootDir");
		if (ROOT_DIR == null) throw new IllegalArgumentException("Must specify rootDir");
		maxCacheSize = config.getInt("maxCacheSize");
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

			String url;
			if (DEBUG_SINGLE_CACHE_PAGE && DEBUG_SINGLE_CACHE_PAGE_SAMENAME) {
				url = DEBUG_SINGLE_CACHE_PAGE_FNAME;
			} else {
				url = req.getURL();
			}

			cacheEntry entry;
			synchronized (pageTbl) {

				long t1 = 0, t2;
				if (PROFILE) t1 = System.currentTimeMillis();
				entry = (cacheEntry) pageTbl.get(url);
				if (PROFILE) {
					t2 = System.currentTimeMillis();
					HaboobStats.numCacheLookup++;
					HaboobStats.timeCacheLookup += (t2 - t1);
				}

				if (entry == null) {
					handleCacheMiss(req);
				}
			}
			if (entry != null) {
				synchronized (entry) {
					if (entry.pending) {
						entry.addWaiter(req);
					} else {
						HaboobStats.numCacheHits++;
						entry.send(req);
					}
				}
			}

		} else if (item instanceof AFileIOCompleted) {
			AFileIOCompleted comp = (AFileIOCompleted) item;
			AFile af = comp.getFile();
			cacheEntry entry = (cacheEntry) aFileTbl.get(af);
			if (entry == null) {
				throw new RuntimeException("PageCache: WARNING: Got AFileIOCompleted for non-entry: " + comp);
			}
			if (comp.sizeCompleted != entry.length) {
				throw new RuntimeException("PageCache: WARNING: Got " + comp.sizeCompleted + " bytes read, expecting " + entry.length);
			}
			af.close();
			aFileTbl.remove(af);
			entry.done();

		} else if (item instanceof SinkClosedEvent) {
			// Ignore

		} else {
			System.err.println("PageCache: Got unknown event type: " + item);
		}

	}

	public void handleEvents(List events) {
		for (int i = 0; i < events.size(); i++) {
			handleEvent((IElement) events.get(i));
		}
	}

	private void handleCacheMiss(HttpRequest req) {
		String url;
		String fname;
		long t1 = 0, t2;

		if (DEBUG_SINGLE_CACHE_PAGE) {
			if (DEBUG_SINGLE_CACHE_PAGE_SAMENAME) {
				// Rewrite url
				url = DEBUG_SINGLE_CACHE_PAGE_FNAME;
				fname = ROOT_DIR + url;
			} else {
				// Rewrite fname, not url
				url = req.getURL();
				fname = ROOT_DIR + DEBUG_SINGLE_CACHE_PAGE_FNAME;
			}
		} else {
			url = req.getURL();
			fname = ROOT_DIR + url;
		}

		AFile af = null;
		AFileStat stat;
		BufferElement payload = null;
		HttpOKResponse resp;
		cacheEntry entry;

		if (DEBUG_NO_FILE_READ && DEBUG_NO_FILE_READ_SAMESIZE) {
			resp = new HttpOKResponse(getMimeType(fname), DEBUG_NO_FILE_READ_SAMESIZE_SIZE);
			entry = new cacheEntry(req, resp, null, DEBUG_NO_FILE_READ_SAMESIZE_SIZE);

		} else {

			// Open file and stat it to determine size
			try {
				af = new AFile(fname, mysink, false, true);
				stat = af.stat();
				if (stat.isDirectory) {
					af.close();
					fname = fname + "/" + DEFAULT_URL;
					af = new AFile(fname, mysink, false, true);
					stat = af.stat();
				}

				if (PROFILE) t1 = System.currentTimeMillis();
				resp = new HttpOKResponse(getMimeType(fname), (int) stat.length);
				if (PROFILE) {
					t2 = System.currentTimeMillis();
					HaboobStats.numCacheAllocate++;
					HaboobStats.timeCacheAllocate += (t2 - t1);
				}

				payload = resp.getPayload();

			} catch (IOException ioe) {
				// File not found
				System.err.println("PageCache: Could not open file " + fname + ": " + ioe);
				HaboobStats.numErrors++;
				HttpNotFoundResponse notfound = new HttpNotFoundResponse(req, ioe.getMessage());
				sendSink.enqueueLossy(new HttpResponder(notfound, req, true));
				return;
			}

			entry = new cacheEntry(req, resp, af, (int) stat.length);
		}

		if (!DEBUG_NO_FILE_READ || !DEBUG_NO_FILE_READ_SAMESIZE) {
			aFileTbl.put(af, entry);
		}
		pageTbl.put(url, entry);

		if ((maxCacheSize != -1) && (HaboobStats.cacheSizeBytes > maxCacheSize * 1024)) {
			if (PROFILE) t1 = System.currentTimeMillis();
			rejectCacheEntry();
			if (PROFILE) {
				t2 = System.currentTimeMillis();
				HaboobStats.numCacheReject++;
				HaboobStats.timeCacheReject += (t2 - t1);
			}
		}

		if (!DEBUG_NO_FILE_READ) {
			try {
				af.read(payload);
			} catch (SinkException se) {
				// XXX Should not really happen
				System.err.println("PageCache: Got SinkException attempting read on " + fname + ": " + se);
				aFileTbl.remove(af);
				af.close();
				HaboobStats.numErrors++;
				HttpNotFoundResponse notfound = new HttpNotFoundResponse(req, se.getMessage());
				sendSink.enqueueLossy(new HttpResponder(notfound, req, true));
				return;
			}
		} else {
			// Pretend we got it already
			if (!DEBUG_NO_FILE_READ_SAMESIZE) {
				af.close();
				aFileTbl.remove(af);
			}
			entry.done();
		}

		HaboobStats.numCacheMisses++;
	}

	private String getMimeType(String url) {
		Enumeration e = mimeTbl.keys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			if (url.endsWith(key)) return (String) mimeTbl.get(key);
		}
		return defaultMimeType;
	}

	private void rejectCacheEntry() {
		while (HaboobStats.cacheSizeBytes > maxCacheSize * 1024) {

			int victim = rand.nextInt() % pageTbl.size();
			Enumeration e = pageTbl.keys();
			for (int i = 0; i < victim; i++) {
				Object obj = e.nextElement();
				if (obj == null) throw new Error("ERROR: Got null entry searching for victim " + victim + " in cache, pageTbl.size " + pageTbl.size());
			}
			String url = (String) e.nextElement();
			cacheEntry entry = (cacheEntry) pageTbl.get(url);
			if (entry == null) {
				throw new Error("ERROR: rejectCacheEntry got null entry for url " + url);
			}
			// Don't reject pending entries
			if (entry.pending) continue;
			pageTbl.remove(url);
			HaboobStats.cacheSizeBytes -= entry.size;
			HaboobStats.cacheSizeEntries--;
			if (DEBUG) System.err.println("Rejecting cache entry " + url + " (" + entry.size + " bytes)");
			if (DEBUG) System.err.println("  Cache size now " + (HaboobStats.cacheSizeBytes / 1024));
		}
	}

	private class cacheEntry {
		HttpOKResponse response;
		int length;
		boolean pending;
		int size;
		FastLinkedList waiting;
		String url;

		cacheEntry(HttpRequest req, HttpOKResponse resp, AFile af, int size) {
			this.response = resp;
			this.length = resp.getPayload().size;
			this.url = req.getURL();
			this.size = size;
			pending = true;
			waiting = new FastLinkedList();
			addWaiter(req);
		}

		synchronized void addWaiter(HttpRequest req) {
			waiting.add_to_tail(req);
		}

		// Send response to all waiters when done reading
		synchronized void done() {
			if (DEBUG) System.err.println("PageCache: Done with file read");
			pending = false;
			HaboobStats.cacheSizeEntries++;
			HaboobStats.cacheSizeBytes += length;
			HttpRequest waiter;

			while ((waiter = (HttpRequest) waiting.remove_head()) != null) {
				HttpResponder respd = new HttpResponder(response, waiter);
				sendSink.enqueueLossy(respd);
			}
		}

		// Send cache entry on hit
		void send(HttpRequest req) {
			HttpResponder respd = new HttpResponder(response, req);
			sendSink.enqueueLossy(respd);
		}

	}


}

