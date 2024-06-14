package org.jcyclone.core.internal;

import org.jcyclone.core.handler.ISingleThreadedEventHandler;

/**
 * Literally, the same thing as the regular {@link Producer} but setup to run single-threaded

 * @author toli
 * @version $Id: ProducerSingleThreaded.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class ProducerSingleThreaded extends Producer implements ISingleThreadedEventHandler {
    public ProducerSingleThreaded() {
    }
}
