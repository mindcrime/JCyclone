package org.jcyclone.core.fileconfig;

import org.jcyclone.core.queue.IElement;
import org.jcyclone.core.cfg.IConfigData;
import org.jcyclone.core.handler.EventHandlerException;
import org.jcyclone.core.handler.IEventHandler;

import java.util.List;
import java.util.Vector;

/**
 * @author Toli Kuznets
 * @version $Id: LastStage.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */
public class LastStage implements IEventHandler {
    private String callSign = "last";
    private Vector<String> allMsgs;

    public LastStage()
    {
        allMsgs = new Vector<String>();
    }

    public void init(IConfigData iConfigData) throws Exception {
    }

    public void handleEvent(IElement iElement) throws EventHandlerException {
        if(iElement instanceof SampleEvent) {
            String msg = ((SampleEvent)iElement).getData();
            String outgoing = msg + "--"+callSign + "--";
            allMsgs.add(outgoing);
        }
    }

    public void handleEvents(List list) throws EventHandlerException {
        for (Object o : list) {
            if(o instanceof SampleEvent) {
                String msg = ((SampleEvent)o).getData();
                String outgoing = msg + "--"+callSign;
                allMsgs.add(outgoing);
            }
        }
    }

    public void destroy() throws Exception {
        for (String s : allMsgs) {
            System.err.println(s);
        }
    }

    public Vector<String> getAllMessages() {return allMsgs; }
}

