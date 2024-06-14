package org.jcyclone.core.internal;

import org.jcyclone.core.fileconfig.SampleEvent;

/**
 * Subclass of {@link SampleEvent} that contains the object plus
 * name of the thread that's processing the event
 * @author toli
 * @version $Id: ThreadSampleEvent.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class ThreadSampleEvent extends SampleEvent {
    private String producerThread;
    private String consumerThread;

    public ThreadSampleEvent(String inData) {
        super(inData);
    }

    public String getProducerThread() {
        return producerThread;
    }

    public void setProducerThread(String producerThread) {
        this.producerThread = producerThread;
    }

    public String getConsumerThread() {
        return consumerThread;
    }

    public void setConsumerThread(String consumerThread) {
        this.consumerThread = consumerThread;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer(500);
        buffer.append("[Value: ").append(getData());
        buffer.append(", ").append(getProducerThread());
        buffer.append(", ").append(getConsumerThread()).append("]");
        return buffer.toString();
    }
}

