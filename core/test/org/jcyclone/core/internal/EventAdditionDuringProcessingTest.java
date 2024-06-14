package org.jcyclone.core.internal;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.jcyclone.core.cfg.JCycloneConfig;
import org.jcyclone.core.cfg.MapConfig;

import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.Semaphore;

/**
 * We had scenario where a consumer stage receives a batch of events and starts processing it,
 * while the JCyclone scheduler gets another batch and tries to add it to the list of events
 * to be processed.
 * As a result, if the consumer stage was using an iterator to access the list of events (such as the
 * java5 for-each construct) and it was the same list that both the JCyclone scheduler and the consumer
 * stage was working on, we'd get a {@link java.util.ConcurrentModificationException).
 * This was in the {@link NullBatchSorter} implementation which has subsequently been modified to
 * no longer reuse the same array for sending batches of events to be processed, but instead to
 * create a new list every time.
 *
 * The purpose of this test is to create a series of Producers that send events (some one, some multiple)
 * and a consumer that receives them.
 *
 * The first producer sends a "trigger" event, which when the conumers receives it starts to wait on a semaphore.
 * It is only another producer a few ticks later that signals on this sema after sending its own events, with a few
 * other producers in between.
 * The idea is to make sure that the consumer is in the middle of iterating over the list of events,
 * and set it up so that JCyclone will try to add new events to the future batch of events for this consumer
 * to process.
 * The "signalling" producer signals the sema later, and the test proceeds the same way as the usual
 * battery of tests deriving from {@link SchedulerTestBase}.
 *
 * The {@link NullBatchSorter} implementation has been fixed to no longer reuse the same list, but
 * we should have this test just in case.
 *
 * It works with the {@link AllAtOnceProducer} and the {@link EventAddingConsumer}.
 *
 * @author toli
 * @version $Id: EventAdditionDuringProcessingTest.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class EventAdditionDuringProcessingTest extends SchedulerTestBase {

    public static Semaphore sema = new Semaphore(0);

    public EventAdditionDuringProcessingTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(EventAdditionDuringProcessingTest.class);
    }

    public static final String SEMA_TRIGGER_EVENT = "sema";
    public static final String SEMA_TRIGGER_RELEASER_NAME = "triggerReleaser";
    public static final String[] MESSAGES = {"abc", "123", "def", "zzz", SEMA_TRIGGER_EVENT, "ghi", "789", "jkl",
            "101112", "mno", "prs", "tuv","wxyz"};
    public final static int CONSUMER_WAIT = 6;

    public void testEventsAdded() throws Exception {
        failureStatus = null;
        Properties props = new Properties();
        props.setProperty("global.defaultThreadManager", JCycloneConfig.THREADMGR_TPSTM_CONCURRENT);

        int nProducers = 6;
        for (int i=1;i<=nProducers;i++) {
            props.setProperty("stages.producer"+i+".class", AllAtOnceProducer.class.getName());
            props.setProperty("stages.producer"+i+".initargs."+NEXT_STAGE, "consumer");
            props.setProperty("stages.producer"+i+".initargs."+WAIT_INTERFVAL, ""+(i*2));
        }
        props.setProperty("stages.producer1.initargs."+TO_SEND, SEMA_TRIGGER_EVENT);
        props.setProperty("stages.producer2.initargs."+TO_SEND, "123"+MSG_SEPARATOR+"def");
        props.setProperty("stages.producer3.initargs."+TO_SEND, "zzz");
        props.setProperty("stages.producer4.initargs."+TO_SEND, "jkl"+MSG_SEPARATOR+"101112");
        props.setProperty("stages.producer5.initargs."+TO_SEND, "mno"+MSG_SEPARATOR+"prs");
        props.setProperty("stages.producer6.initargs."+TO_SEND, "tuv"+MSG_SEPARATOR+"wxyz");

        // Setup the "trigger releaser" producer - that's the one that'll signal on the semaphore
        props.setProperty("stages."+SEMA_TRIGGER_RELEASER_NAME+".class", AllAtOnceProducer.class.getName());
        props.setProperty("stages."+SEMA_TRIGGER_RELEASER_NAME+".initargs."+NEXT_STAGE, "consumer");
        props.setProperty("stages."+SEMA_TRIGGER_RELEASER_NAME+".initargs."+WAIT_INTERFVAL, ""+10);
        props.setProperty("stages."+SEMA_TRIGGER_RELEASER_NAME+".initargs."+
                TO_SEND, "abc"+MSG_SEPARATOR+"ghi"+MSG_SEPARATOR+"789");

        props.setProperty("stages.consumer.class", EventAddingConsumer.class.getName());
        final MapConfig mc = generateMapConfig(props);

        runTest(mc, MESSAGES.length);

        // dump all received messages into a table
        HashSet<String> set = new HashSet<String>(messages.size());
        for (ThreadSampleEvent event : messages) {
            set.add(event.getData());
        }

        for (String sentMsg : MESSAGES) {
            assertTrue("couldn't find an expected message among received: "+ sentMsg, set.remove(sentMsg));
        }

        assertEquals("had some extra messages in received set", 0, set.size());
    }
}
