package org.jcyclone.core.fileconfig;

import org.jcyclone.core.queue.ISink;
import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.queue.SinkException;
import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.EventHandlerException;
import org.jcyclone.core.handler.IEventHandler;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Toli Kuznets
 * @version $Id: MiddleStage.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */
public class MiddleStage implements IEventHandler {
    private ISink nextHandler;
    private String callSign = "middle";

    public void init(IConfigData iConfigData) throws Exception {
        String nextHandlerName = iConfigData.getString("nextHandler");
        nextHandler = iConfigData.getManager().getStage(nextHandlerName).getSink();
    }

    public void handleEvent(IElement iElement) throws EventHandlerException {
        if(iElement instanceof SampleEvent) {
            nextHandler.enqueueLossy(getNextEvent((SampleEvent)iElement));
        }
    }

    public void handleEvents(List list) throws EventHandlerException {
        ArrayList<SampleEvent> outList = new ArrayList<SampleEvent>();
        try {
            for (Object o : list) {
                if(o instanceof SampleEvent) {
                    outList.add(getNextEvent((SampleEvent)o));
                }

            }
            nextHandler.enqueueMany(outList);
        } catch (SinkException ex) {
            throw new MyEventHandlerException(ex);
        }
    }

    public void destroy() throws Exception {
    }

    // append stage2 data
    private SampleEvent getNextEvent(SampleEvent in)
    {
        String msg = in.getData();
        return new SampleEvent(msg + "--"+callSign);
    }

    public class MyEventHandlerException extends EventHandlerException {
        public MyEventHandlerException(Exception nested) {
            super(nested.getMessage());
        }
    }

}
