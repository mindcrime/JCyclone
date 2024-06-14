package org.jcyclone.core.cfg;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Hashtable;
import java.io.IOException;

/**
 * Basic test to verify that configuration parameters are being read correctly
 * @author toli
 * @version $Id: JCycloneConfigTest.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */

public class JCycloneConfigTest extends TestCase {
    public JCycloneConfigTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(JCycloneConfigTest.class);
    }

    /** verify a couple basic values from the default config
     * then verify that all values are actually set - remember to step by 2 since the default array is
     * laid out as key/value pair for odd/even indexes
     * */
    public void testVanillaConfig() throws Exception {
        JCycloneConfig cfg = new JCycloneConfig();
        assertEquals(20, cfg.getInt("global.threadPool.maxThreads"));
        for (int i = 0; i < JCycloneConfig.defaults.length; i+=2) {
            String key = JCycloneConfig.defaults[i];
            assertNotNull(key, cfg.getString(key));
            assertEquals(key, cfg.getString(key), JCycloneConfig.defaults[i+1]);
        }
    }

    public void testWithCmdLineArgs() throws Exception {
        String[] args = new String[]{
                "key1=value1",
                "key2=value2",
                "global.threadPool.maxThreads=37"
        };
        MyJCycloneConfig cfg = new MyJCycloneConfig(args);
        assertEquals("key1", "value1", cfg.getCmdLineArgs().get("key1"));
        assertEquals("key2", "value2", cfg.getCmdLineArgs().get("key2"));
        assertEquals("global.threadPool.maxThreads", "37", cfg.getCmdLineArgs().get("global.threadPool.maxThreads"));
    }

    private class MyJCycloneConfig extends JCycloneConfig
    {
        public MyJCycloneConfig(String defaultArgs[]) throws IOException {
            super(defaultArgs);    
        }

        private Hashtable getCmdLineArgs() { return cmdLineArgs; }
    }
}
