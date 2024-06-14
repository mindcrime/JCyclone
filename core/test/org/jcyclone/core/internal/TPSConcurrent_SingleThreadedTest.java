package org.jcyclone.core.internal;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Same as the regular {@link TPSSchedulerConcurrentTest} but with all producers/consumers
 * being single-threaded
 * @author toli
 * @version $Id: TPSConcurrent_SingleThreadedTest.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class TPSConcurrent_SingleThreadedTest extends TPSSchedulerConcurrentTest {
    public TPSConcurrent_SingleThreadedTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(TPSConcurrent_SingleThreadedTest.class);
    }

    protected String getConsumerClass() {
        return "org.jcyclone.core.internal.ConsumerSingleThreaded";
    }

    protected String getProducerClass() {
        return "org.jcyclone.core.internal.ProducerSingleThreaded";
    }
}
