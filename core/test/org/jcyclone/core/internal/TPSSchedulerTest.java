package org.jcyclone.core.internal;

import org.jcyclone.core.cfg.JCycloneConfig;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Properties;

/**
 * Test of the original {@link TPSScheduler} algorithm.
 * Basically, we just rerun the same tests as in {@link TPSSchedulerConcurrentTest} but
 * specify a different scheduler at launch.
 *
 * @author toli
 * @version $Id: TPSSchedulerTest.java,v 1.1 2006/09/27 01:37:25 tolikuznets Exp $
 */

public class TPSSchedulerTest extends TPSSchedulerConcurrentTest {
    public TPSSchedulerTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(TPSSchedulerTest.class);
    }

    protected void setThreadManager(Properties inProps) {
        inProps.setProperty("global.defaultThreadManager", JCycloneConfig.THREADMGR_TPSTM);
    }
}
