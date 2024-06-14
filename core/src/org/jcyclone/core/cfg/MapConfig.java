package org.jcyclone.core.cfg;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the config object that doesn't need input from a file
 * Configuration is done through a Map of values that is passed in. 
 * @author Graham Miller
 */
public class MapConfig extends JCycloneConfig {

    public MapConfig() {
        super();
    }

    public MapConfig(String[] defaultArgs) throws IOException {
        super(defaultArgs);
    }


    /** Takes in a map and adds it to the existing configuration, replacing
     * any existing settings or creating new sections as necessary.
     *
     * If the incoming map corresponds to a "leaf" node, then it just contains
     * key/value pairs.
     * Otherwise, if the incoming map contains nested values, then it's another subseciton
     * so it'll actually be a combination of key/another_map pairs.
     * @param configs
     */
    public void setConfigs(Map configs){
        setConfigsHelper(root, configs);
    }

    private void setConfigsHelper(ConfigSection inSection, Map configs){
        if (configs == null){
            return;
        }
        Set<Map.Entry> entries = configs.entrySet();
        for (Map.Entry entry : entries) {
            String aKey = (String) entry.getKey();
            Object aValue = entry.getValue();
            if (aValue instanceof Map){
                ConfigSection theSection = inSection.getSubsection(aKey);
                if(theSection == null) {
                    theSection = new ConfigSection(aKey);
                    inSection.addSubsection(theSection);
                }
                setConfigsHelper(theSection, (Map)aValue);
            } else {
                inSection.putVal(aKey, aValue);
            }
        }
    }
}
