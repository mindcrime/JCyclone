package org.jcyclone.core.cfg;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.IOException;

/**
 * Tests the {@link FileConfig} class to make sure we can override the
 * default args with ones specified in the config file
 *
 * @author toli
 * @version $Id: FileConfigTest.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */

public class FileConfigTest extends TestCase {
    public static final String CFG_FILE_NAME = "fileconfig-jcyclone.cfg";
    private static String sCfgFileLoc;

    public FileConfigTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        sCfgFileLoc = ClassLoader.getSystemResource(CFG_FILE_NAME).getPath();
        return new TestSuite(FileConfigTest.class);
    }

    /** Basic test that we can read the config file and reset some of the default values */
    public void testReadConfigFile() throws Exception {
        FileConfig fc = new FileConfig(sCfgFileLoc);
        compareArrays("stages", new String[]{"stage1", "stage2", "lastStage"}, fc.getStageNames());
        compareArrays("plugins", new String[]{"plugin"}, fc.getPluginNames());
    }

    /** Verifies that overrides specified in the file override defaults */
    public void testVerifyFileOverrides() throws Exception {
        FileConfig fc = new FileConfig(sCfgFileLoc);
        assertEquals(33, fc.getInt("global.threadPool.minThreads"));
        assertEquals(330, fc.getInt("global.threadPool.maxThreads"));
        assertEquals(11, fc.getInt("global.threadPool.initialThreads"));
    }

    /** file is missing the closing </global> tag so should error out */
    public void testMisformedFile() throws Exception {
        final String misformedPath = ClassLoader.getSystemResource("fileconfig-misformed.cfg").getPath();
        assertNotNull(misformedPath);
        try {
            /*waste result */  new FileConfig(misformedPath);
            fail("should've failed to load the file config");
        } catch(IOException expected) {
            // expected
        }
    }

    /** Compares two incoming arrays */
    public static void compareArrays(String desc, String[] inExpected, String[] actual)
    {
        assertEquals(desc+": different number of elements", inExpected.length, actual.length);
        for(int i=0;i<inExpected.length;i++) {
            assertEquals(desc +"["+i+"]: ", inExpected[i], actual[i]);
        }
    }
}
