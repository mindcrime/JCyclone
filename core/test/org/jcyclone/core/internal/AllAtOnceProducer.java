package org.jcyclone.core.internal;

import java.util.Arrays;

/** We have only one consumer sink.
 * Wait a random amount, and then send all events that were specified at once in one go
 * to a randmly chosen sink from all specified
 *
 * If we are a special "trigger" producer then signal on a semaphore.
 *
 * Used in the {@link EventAdditionDuringProcessingTest}.
 *
 * @author toli kuznets
 * @version $Id: AllAtOnceProducer.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */
public class AllAtOnceProducer extends Producer
{
    public AllAtOnceProducer() {
    }

    public Runnable getRunner() {
        return new Runner() {

            public void run() {
                try {
                    Thread.sleep((int) (SchedulerTestBase.sRandom.nextDouble() * waitInterval * 1000));
                    ThreadSampleEvent[] allEvents = new ThreadSampleEvent[toSend.length];
                    for(int i=0;i<toSend.length;i++) {
                        allEvents[i] = new ThreadSampleEvent(toSend[i]);
                        allEvents[i].setProducerThread(name+"["+Thread.currentThread().getName()+"]");
                    }
                    nextStages[SchedulerTestBase.sRandom.nextInt(nextStages.length)].enqueueMany(Arrays.asList(allEvents));
                    if(name.equals(EventAdditionDuringProcessingTest.SEMA_TRIGGER_RELEASER_NAME)) {
                        // force wait another waitInterval secs
                        Thread.sleep(waitInterval*1000);
                        EventAdditionDuringProcessingTest.sema.release();
                        System.out.println(EventAdditionDuringProcessingTest.SEMA_TRIGGER_RELEASER_NAME + " released sema");
                    }
                    System.out.println("[" + name + "]  sent all events: " + allEvents.length);
                } catch (Exception e) {
                    System.out.println("[" + name + "]  got exception: "+e.getMessage());
                    e.printStackTrace();
                }
            }
        };
    }
}
