package org.jcyclone.core.fileconfig;

import org.jcyclone.core.queue.IElement;

/**
 * Subclass to encapsulate events for JCyclone
 * @author Toli Kuznets
 * @version $Id: SampleEvent.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */
public class SampleEvent implements IElement {
    private String data;
    public SampleEvent(String inData) {
        data = inData;
    }

    public String getData() {return data; }

    public String toString() {
        return getData();
    }
}
