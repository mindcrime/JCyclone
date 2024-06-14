package org.jcyclone.core.plugin;

import org.jcyclone.core.internal.ISystemManager;
import org.jcyclone.core.stage.IStageManager;

/**
 * Actually used to remove dependencies from the core to asocket and adisk.
 */
public interface IPlugin {

	void initialize(IStageManager stagemgr, ISystemManager sysmgr, String pluginName) throws Exception;

    /**
     * Called when a plugin is destroyed. This method should
     * perform any cleanup or shutdown operations as required by the
     * application before the plugin is removed from the system.
     *
     * @throws Exception The plugin can indicate an
     *                   error to the runtime during shutdown by throwing an
     *                   Exception.
     */
    void destroy() throws Exception;

}
