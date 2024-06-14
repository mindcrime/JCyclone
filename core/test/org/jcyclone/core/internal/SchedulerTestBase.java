package org.jcyclone.core.internal;

import junit.framework.TestCase;
import org.jcyclone.core.boot.JCyclone;
import org.jcyclone.core.cfg.MapConfig;

import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Tests the correctness of the {@link org.jcyclone.core.internal.TPSSchedulerConcurrent} scheduling algorithm.
 * This is less of a unit test and more of a full-on integration test.
 * Tries to setup producer/consumer scenarios of various complexity to make sure
 * that schedule works and there's no starvation.
 *
 * @author toli
 * @version $Id: SchedulerTestBase.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public abstract class SchedulerTestBase extends TestCase {

    public static final String NEXT_STAGE = "nextStage";
    public static final String WAIT_INTERFVAL = "waitInterval";
    public static final String TO_SEND = "toSend";
    public static final String MSG_SEPARATOR = ",";
    public static final String STAGE_NAME_SEPARATOR = ",";

    public final static Random sRandom = new Random();
    protected static final List<Producer> producerList = Collections.synchronizedList(new ArrayList<Producer>());
    protected static final List<Consumer> consumerList = Collections.synchronizedList(new ArrayList<Consumer>());
    protected static final List<ThreadSampleEvent> messages = Collections.synchronizedList(new ArrayList<ThreadSampleEvent>());
    protected static Semaphore completion;
    public static FailureStatus failureStatus;

    public SchedulerTestBase(String inName) {
        super(inName);
    }

    public static void addProducer(Producer inP)
    {
        producerList.add(inP);
    }

    public static void addConsumer(Consumer inC)
    {
        consumerList.add(inC);
    }

    public static void addHandledMesssage(ThreadSampleEvent msg)
    {
        messages.add(msg);
        System.out.println("Adding message "+msg);
        completion.release();
    }

    protected MapConfig generateMapConfig(Properties props)
    {
        MapConfig mc = new MapConfig();
        Set<Object> keySet = props.keySet();
        for (Object aKeyObject : keySet){
            String aKey = (String)aKeyObject;
            mc.putString(aKey, props.getProperty(aKey));
        }
        return mc;
    }

    /** Setup the semaphore to wait for n-1 expected messages
     * Reset all the lists so that each test run starts out fresh.
     * Start JCyclone. After it has been started, notify all the Producers to start going
     * Wait for all the messages to come in, and verify the right number of messages have been posted
     * (this is a sanity check, since otherwise the semaphore will block).
     * Kill the JCyclone setup afterwards, and "displose" of it to allow for another run.
     *
     * The expectation is that each method calling into this method will do its own
     * subsequent validation of the correctness of the message array.
     *
     * @param config
     * @param nExpectedMsgs    Expected number of messages to go through
     * @throws Exception
     */
    protected void runTest(MapConfig config, int nExpectedMsgs) throws Exception
    {
        messages.clear();
        producerList.clear();
        consumerList.clear();
        failureStatus = null;

        completion = new Semaphore(-nExpectedMsgs+1);
        System.out.println("Starting a test expectedMessages: "+nExpectedMsgs);
        JCyclone theJC = new JCyclone(config);

        for (Producer producer : producerList) {
            producer.startProducer();
        }

        completion.acquire();
        theJC.stop();
        theJC.dispose();

        if(failureStatus != null) {
            System.out.println("Test failed for unexpected reason: " +failureStatus.message+": " +failureStatus.ex);
            failureStatus.ex.printStackTrace();
            fail(failureStatus.message);
        }

        assertEquals(nExpectedMsgs, messages.size());
    }

    /** Helper method for outside threads to register a failure with the test-driver.
     * setup a failure status and signal all semaphores to speed up the completion of the test */
    public static void registerFailure(String message, Exception ex)
    {
        failureStatus = new FailureStatus(ex, message);
        completion.release(10000);
        System.out.println("registered failure, test will fail: " +failureStatus.message+": " +failureStatus.ex);
        fail(failureStatus.message);
    }

    public static class FailureStatus {
        public Exception ex;
        public String message;

        public FailureStatus(Exception ex, String message) {
            this.ex = ex;
            this.message = message;
        }
    }
}
