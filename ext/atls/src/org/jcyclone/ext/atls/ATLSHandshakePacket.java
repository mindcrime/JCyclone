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

package org.jcyclone.ext.atls;

import org.jcyclone.core.queue.IElement;
import org.jcyclone.ext.atls.protocol.ATLSHandshakeRecord;

/**
 * aTLSHandshakePacket is used solely for the handshake stage to indicate
 * that a handshake message was received and should be handled. Also necessary
 * to differentiate between a normal handshake message and a change cipher spec.
 */

class ATLSHandshakePacket implements IElement {

	private ATLSConnection atlsconn;
	private ATLSHandshakeRecord record;
	private boolean startHS = false;

	/**
	 * Only created by the Record Stage to indicate that the HandshakeStage should
	 * begin the handshake process.
	 */
	ATLSHandshakePacket(ATLSConnection atlsconn) {
		this.atlsconn = atlsconn;
		record = null;
		startHS = true;
	}

	/**
	 * Created by record stage for every handshake message except a change cipher spec message.
	 */
	ATLSHandshakePacket(ATLSConnection atlsconn, ATLSHandshakeRecord record) {
		this.atlsconn = atlsconn;
		this.record = record;
	}

	/**
	 * Returns the aTLSConnection from which this packet was received.
	 */
	ATLSConnection getConnection() {
		return atlsconn;
	}

	/**
	 * Returns whether the packet actually contains useful data, or is just a signal for the
	 * handshake stage to start the handshake.
	 */
	boolean getboolHS() {
		return startHS;
	}

	/**
	 * Returns the record associated with this packet, if any.
	 */
	ATLSHandshakeRecord getRecord() {
		return record;
	}
}
