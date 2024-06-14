package org.jcyclone.core.internal;

import org.jcyclone.core.handler.EventHandlerException;
import org.jcyclone.core.queue.IElement;

import java.util.List;
import java.util.ConcurrentModificationException;

/**
 * Special subclass of a regular {@link Consumer} that waits on a semaphore
 * when a particular trigger message comes in.
 * We also trap the {@link ConcurrentModificationException} and register a failure
 * in that case.
 * It is also imperative to use the Java-5 foreach loop construct - otherwise, the
 * error is not triggered.
 * example: <pre>
 *              for (Object o : events) {
                    handleEvent((IElement) o);
                }
 * </pre>
 *
 * Used in the {@link EventAdditionDuringProcessingTest}.
 *
 * @author toli
 * @version $Id: EventAddingConsumer.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */
public class EventAddingConsumer extends Consumer
{
    public EventAddingConsumer() {
    }

    /** Override the generic behaviour of handleEvents to specifically block when a certain message comes in
     * When we see the trigger message, we wait until the semaphore is signalled until we process any other messages
     * @param events
     * @throws EventHandlerException
     */
    public void handleEvents(List events) throws EventHandlerException {
            try {
                for (Object o : events) {
                    if(EventAdditionDuringProcessingTest.SEMA_TRIGGER_EVENT.equals(((ThreadSampleEvent)o).getData())) {
                        System.out.println("saw a trigger event, acquiring sema");
                        EventAdditionDuringProcessingTest.sema.acquire();
                        System.out.println("past the triggerevent sema");
                    }
                    handleEvent((IElement) o);
                }
            } catch(ConcurrentModificationException concEx) {
                SchedulerTestBase.registerFailure("received concurrentModEx", concEx);
                throw concEx;
            } catch (InterruptedException e) {
                System.out.println("got interrupted exception in consumer.handleMany");
                e.printStackTrace();
            }
    }
}
