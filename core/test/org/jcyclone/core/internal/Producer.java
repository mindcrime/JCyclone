package org.jcyclone.core.internal;

import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.EventHandlerException;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;

import java.util.List;

/**
 * Represents a "producer" stage - something that creates a work of data
 * Producer expects config like this:
 * <pre>
 * <producerName>.class                 class for next stage
 * <producerName>.initargs.nextStages    comma-separated list of next stages (where to send will be random)
 * <producerName>.initargs.waitInterval how long to wait before sending an event
 * <producerName>.initargs.toSend       :-separate list of messages to send
 *
 * @author toli
 * @version $Id: Producer.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class Producer implements IEventHandler {
    protected ISink[] nextStages;
    protected int waitInterval;
    protected String name;
    protected String[] toSend;

    public Producer() {
    }

    public void handleEvent(IElement elem) throws EventHandlerException {

    }

    public void handleEvents(List events) throws EventHandlerException {

    }

    public void init(IConfigData config) throws Exception {
        String[] nextHandlerNames = config.getString(SchedulerTestBase.NEXT_STAGE).split(SchedulerTestBase.STAGE_NAME_SEPARATOR);
        nextStages = new ISink[nextHandlerNames.length];
        for (int i=0;i<nextHandlerNames.length; i++) {
            nextStages[i] = config.getManager().getStage(nextHandlerNames[i]).getSink();
        }
        waitInterval = config.getInt(SchedulerTestBase.WAIT_INTERFVAL);
        toSend = config.getString(SchedulerTestBase.TO_SEND).split(SchedulerTestBase.MSG_SEPARATOR);

        // add self to the test
        SchedulerTestBase.addProducer(this);
        name = config.getStage().getName();
    }

    public void startProducer()
    {
        (new Thread(getRunner())).start();
    }

    /** To be modified by subclasses that want to substitute a different runnable */
    public Runnable getRunner() {
        return new Runner();
    }

    public void destroy() throws Exception {
        System.out.println("producer [" + name + "] getting destroyed");
    }

    protected class Runner implements Runnable {

        /** Sleep for a random period up to the specified wait interval.
         * Pick a random stage from list of available stages and send the event there
         */
        public void run() {
            try {
                System.out.println("producer.runner [" + name + "] started and will send "+toSend.length +
                        " messages to " + nextStages.length + " consumers.");
                for (String aMsgsToSend : toSend) {
                    long millisDelay = (int) (SchedulerTestBase.sRandom.nextDouble() * waitInterval * 1000);
                    //System.out.println("[" + name + "] sleeping for " + millisDelay);
                    Thread.sleep(millisDelay);
                    ThreadSampleEvent event = new ThreadSampleEvent(aMsgsToSend);
                    event.setProducerThread(name+"["+Thread.currentThread().getName()+"]");
                    nextStages[SchedulerTestBase.sRandom.nextInt(nextStages.length)].enqueue(event);
                    System.out.println("[" + name + "]  sent event: " + event);
                }
            } catch (Exception e) {
                System.out.println("[" + name + "]  got exception: "+e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
