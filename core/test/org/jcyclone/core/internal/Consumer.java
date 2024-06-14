package org.jcyclone.core.internal;

import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.handler.EventHandlerException;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.cfg.IConfigData;

import java.util.List;

/**
 * @author toli
 * @version $Id: Consumer.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class Consumer implements IEventHandler  {
    protected String name;
    public Consumer() {
    }

    public void handleEvents(List events) throws EventHandlerException {
        for (Object o : events) {
            handleEvent((IElement) o);
        }
    }

    public void init(IConfigData config) throws Exception {
        SchedulerTestBase.addConsumer(this);
        name = config.getStage().getName();
    }

    public void destroy() throws Exception {
    }

    // incoming String
    public void handleEvent(IElement elem) throws EventHandlerException {
        ThreadSampleEvent incoming = (ThreadSampleEvent)elem;
        incoming.setConsumerThread(name+"["+Thread.currentThread().getName()+"]");
        SchedulerTestBase.addHandledMesssage(incoming);
    }
}
