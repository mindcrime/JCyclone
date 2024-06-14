package org.jcyclone.core.internal;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Same as {@link TPSSchedulerTest} but with single-threaded producer/consumers
 * @author toli
 * @version $Id: TPSScheduler_SIngleThreadedTest.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class TPSScheduler_SIngleThreadedTest extends TPSSchedulerTest {
    public TPSScheduler_SIngleThreadedTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(TPSScheduler_SIngleThreadedTest.class);
    }

    protected String getConsumerClass() {
        return "org.jcyclone.core.internal.ConsumerSingleThreaded";
    }

    protected String getProducerClass() {
        return "org.jcyclone.core.internal.ProducerSingleThreaded";
    }
}
