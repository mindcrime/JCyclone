package org.jcyclone.core.fileconfig;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jcyclone.core.boot.Main;
import org.jcyclone.core.boot.JCyclone;
import org.jcyclone.core.cfg.FileConfigTest;

import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Toli Kuznets
 * @version $Id: JCycloneStagesTest.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */
public class JCycloneStagesTest extends TestCase {

    public JCycloneStagesTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return (new TestSuite(JCycloneStagesTest.class));
    }


    /** Load all the stages and verify tha that the event sent from first one
     * show up in the last stage
     *
     * @throws Exception
     */
    public void testStageThroughput() throws Exception {
        String config = FileConfigTest.CFG_FILE_NAME;
        final String cfgFile = ClassLoader.getSystemResource(config).getPath();

        ExecutorService service = Executors.newCachedThreadPool();
        service.execute(new Runnable() {
            public void run() {
                Main.main(new String[]{cfgFile});
            }
        });

        service.awaitTermination(5, TimeUnit.SECONDS);
        //service.awaitTermination(50000, TimeUnit.SECONDS);

        JCyclone cyclone = JCyclone.getInstance();
        LastStage lastStage = (LastStage) cyclone.getManager().getStage("lastStage").getWrapper().getEventHandler();
        Vector<String> allMsgs = lastStage.getAllMessages();
        assertEquals("didn't get all messages", 3, allMsgs.size());
        for (int i = 0; i < allMsgs.size(); i++) {
              assertEquals("event "+(i+1)+"--middle--last", allMsgs.get(i));
        }
    }
}
