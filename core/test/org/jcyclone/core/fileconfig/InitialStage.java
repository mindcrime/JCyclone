package org.jcyclone.core.fileconfig;

import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.handler.EventHandlerException;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.SinkException;
import org.jcyclone.core.cfg.IConfigData;

import java.util.List;

/**
 * First stage of the 3-stage sample Jcyclone HellowWorld app
 * @author Toli Kuznets
 * @version $Id: InitialStage.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */
public class InitialStage implements IEventHandler
{
    private ISink nextHandler;

    public void init(IConfigData iConfigData) throws Exception {
        nextHandler = iConfigData.getManager().getStage(iConfigData.getString("nextHandler")).getSink();

        // send 3 events on next stage
        nextHandler.enqueue(new SampleEvent("event 1"));
        nextHandler.enqueue(new SampleEvent("event 2"));
        nextHandler.enqueue(new SampleEvent("event 3"));
    }

    public void handleEvent(IElement iElement) throws EventHandlerException {
        // noop - we only generate events
    }

    public void handleEvents(List list) throws EventHandlerException {
        // noop - we only generate events
    }

    public void destroy() throws Exception {
    }

    public void sendOneEvent(String inMsg) throws SinkException
    {
        nextHandler.enqueue(new SampleEvent(inMsg));
    }
}
