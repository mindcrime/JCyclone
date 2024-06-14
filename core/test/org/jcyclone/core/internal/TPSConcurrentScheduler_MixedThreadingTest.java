package org.jcyclone.core.internal;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test uses concurrent scheduler, single-threaded producer and multi-threaded consumer
 * @author toli
 * @version $Id: TPSConcurrentScheduler_MixedThreadingTest.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class TPSConcurrentScheduler_MixedThreadingTest extends TPSSchedulerConcurrentTest {
    public TPSConcurrentScheduler_MixedThreadingTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(TPSConcurrentScheduler_MixedThreadingTest.class);
    }

    protected String getProducerClass() {
        return "org.jcyclone.core.internal.ProducerSingleThreaded";
    }
    
}
