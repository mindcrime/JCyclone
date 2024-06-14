package org.jcyclone.core.cfg;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

/**
 * Basic test of the {@link MapConfig} class.
 * Create a MapConfig and verify that defaults are overridden.
 *
 * @author toli
 * @version $Id: MapConfigTest.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */

public class MapConfigTest extends TestCase {
    public MapConfigTest(String inName) {
        super(inName);
    }

    public static Test suite() {
        return new TestSuite(MapConfigTest.class);
    }

    public void testOverrideDefaults() throws Exception {
        MapConfig mc = new MapConfig();

        // verify some defaults for now
        assertEquals(1, mc.getInt("global.threadPool.minThreads"));
        assertEquals(20, mc.getInt("global.threadPool.maxThreads"));

        Map<String, Map<String, Map>> extras = new HashMap<String, Map<String, Map>>();
        Map<String, Map> globalSection = new HashMap<String, Map>();
        Map<String, String> tpSection = new HashMap<String, String>();
        tpSection.put("minThreads", "23");
        tpSection.put("maxThreads", "148");
        globalSection.put("threadPool", tpSection);
        extras.put("global", globalSection);

        mc.setConfigs(extras);
        assertEquals(23, mc.getInt("global.threadPool.minThreads"));
        assertEquals(148, mc.getInt("global.threadPool.maxThreads"));
    }

    public void testCreateNewSection() throws Exception {
        MapConfig mc = new MapConfig();

        Map<String, Map<String, Map>> extras = new HashMap<String, Map<String, Map>>();
        Map<String, Map> parentSection = new HashMap<String, Map>();
        Map<String, String> leafSection = new HashMap<String, String>();
        leafSection.put("leaf1", "bob");
        leafSection.put("leaf2", "joe");
        parentSection.put("parent", leafSection);
        extras.put("global", parentSection);

        mc.setConfigs(extras);
        assertEquals("bob", mc.getString("global.parent.leaf1"));
        assertEquals("joe", mc.getString("global.parent.leaf2"));
    }

    /** Tests when you create all the sections manually and verifies that vars are accessbile */
    public void testCreateManuallly() throws Exception {
        Properties props = new Properties();
        props.setProperty("global.toli.section.leaf1", "bob");
        props.setProperty("global.toli.section.leaf2", "joe");
        props.setProperty("global.rama.section.leaf1", "vasya");
        props.setProperty("global.rama.section.leaf2", "pupkin");
        MapConfig mc = new MapConfig();
        Set<Object> keySet = props.keySet();
        for (Object aKeyObject : keySet){
            String aKey = (String)aKeyObject;
            if (aKey.startsWith("global")){
                //String strippedKey = aKey.substring("global".length());
                mc.putString(aKey, props.getProperty(aKey));
            }
        }

        assertEquals("bob", mc.getString("global.toli.section.leaf1"));
        assertEquals("joe", mc.getString("global.toli.section.leaf2"));
        assertEquals("vasya", mc.getString("global.rama.section.leaf1"));
        assertEquals("pupkin", mc.getString("global.rama.section.leaf2"));


    }
}
