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

package org.jcyclone.ext.http;

import org.jcyclone.core.event.BufferElement;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;

/**
 * An httpResponse corresponding to a '200 OK' response.
 *
 * @author Matt Welsh
 */
public class HttpOKResponse extends HttpResponse implements HttpConst, IElement {

	private static final boolean DEBUG = false;
	private String contentType;

	/**
	 * Create an httpOKResponse with the given payload corresponding
	 * to the given request, using the given MIME content-type.
	 */
	public HttpOKResponse(String contentType, BufferElement payload) {
		super(HttpResponse.RESPONSE_OK, contentType, payload);
	}

	/**
	 * Create an httpOKResponse with the given payload corresponding
	 * to the given request, using the given MIME content-type. Use
	 * the given content length in the header of the response.
	 */
	public HttpOKResponse(String contentType, BufferElement payload, int contentLength) {
		super(HttpResponse.RESPONSE_OK, contentType, payload, contentLength);
	}

	/**
	 * Create an httpOKResponse with a given response payload size and
	 * MIME type.
	 */
	public HttpOKResponse(String contentType, int payloadSize) {
		super(HttpResponse.RESPONSE_OK, contentType, payloadSize);
	}

	/**
	 * Create an httpOKResponse with a given response payload size,
	 * MIME type, and completion sink.
	 */
	public HttpOKResponse(String contentType, int payloadSize, ISink compQ) {
		super(HttpResponse.RESPONSE_OK, contentType, payloadSize, compQ);
	}

	protected String getEntityHeader() {
		return null;
	}

	public String toString() {
		return "httpOKResponse [content-length=" + contentLength + ", contentType=" + contentType + "]";
	}

}
