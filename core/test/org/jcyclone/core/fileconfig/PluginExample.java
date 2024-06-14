package org.jcyclone.core.fileconfig;

import org.jcyclone.core.plugin.IPlugin;
import org.jcyclone.core.stage.IStageManager;
import org.jcyclone.core.internal.ISystemManager;

/**
 * Dummy class to be loaded as a JCyclone Plugin
 * @author Toli Kuznets
 * @version $Id: PluginExample.java,v 1.1 2006/09/27 01:37:24 tolikuznets Exp $
 */
public class PluginExample implements IPlugin {
    public PluginExample() {
    }

    public void initialize(IStageManager stagemgr, ISystemManager sysmgr, String pluginName) throws Exception {
        // do nothing
    }

    public void destroy() throws Exception {
        // do nothing`
    }
}
