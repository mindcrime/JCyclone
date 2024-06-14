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

package org.jcyclone.ext.adisk;

import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;

/**
 * Abstract base class of I/O requests which can be posted to the
 * AFile enqueue() methods.
 *
 * @author Matt Welsh
 * @see org.jcyclone.ext.adisk.AFileReadRequest
 * @see AFileWriteRequest
 * @see AFileSeekRequest
 * @see org.jcyclone.ext.adisk.AFileFlushRequest
 * @see org.jcyclone.ext.adisk.AFileCloseRequest
 */
public abstract class AFileRequest implements IElement {

	AFile afile;
	ISink compQ;

	protected AFileRequest(ISink compQ) {
		this.compQ = compQ;
	}

	protected AFileRequest(AFile afile, ISink compQ) {
		this.afile = afile;
		this.compQ = compQ;
	}

	public AFile getFile() {
		return afile;
	}

	AFileImpl getImpl() {
		return afile.getImpl();
	}

	ISink getCompQ() {
		return compQ;
	}

	void complete(IElement comp) {
		if (compQ != null) compQ.enqueueLossy(comp);
	}

}


