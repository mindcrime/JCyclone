package org.jcyclone.core.internal;

import org.jcyclone.core.handler.ISingleThreadedEventHandler;

/**
 * Literally, the same thing as the regular {@link Consumer} but setup to run single-threaded
 *
 * @author toli
 * @version $Id: ConsumerSingleThreaded.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class ConsumerSingleThreaded extends Consumer implements ISingleThreadedEventHandler {
    public ConsumerSingleThreaded() {
    }
}
