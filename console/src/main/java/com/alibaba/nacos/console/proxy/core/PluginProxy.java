/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.core.PluginHandler;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Proxy class for handling plugin operations.
 *
 * @author WangzJi
 */
@Service
public class PluginProxy {
    
    private final PluginHandler pluginHandler;
    
    public PluginProxy(PluginHandler pluginHandler) {
        this.pluginHandler = pluginHandler;
    }
    
    /**
     * Get a list of plugins.
     *
     * @param pluginType optional plugin type filter
     * @return list of plugin info VOs
     * @throws NacosException if there is an issue fetching the plugins
     */
    public List<PluginInfoVO> listPlugins(String pluginType) throws NacosException {
        return pluginHandler.listPlugins(pluginType);
    }
    
    /**
     * Get plugin detail.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return plugin detail VO
     * @throws NacosException if there is an issue fetching the plugin detail
     */
    public PluginDetailVO getPluginDetail(String pluginType, String pluginName)
        throws NacosException {
        return pluginHandler.getPluginDetail(pluginType, pluginName);
    }
    
    /**
     * Update plugin enabled/disabled status.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @param enabled    whether to enable
     * @param localOnly  whether only apply to local node
     * @throws NacosException if there is an issue updating the plugin status
     */
    public void updatePluginStatus(String pluginType, String pluginName, boolean enabled,
        boolean localOnly)
        throws NacosException {
        pluginHandler.updatePluginStatus(pluginType, pluginName, enabled, localOnly);
    }
    
    /**
     * Update plugin configuration.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @param config     configuration map
     * @param localOnly  whether only apply to local node
     * @throws NacosException if there is an issue updating the plugin config
     */
    public void updatePluginConfig(String pluginType, String pluginName, Map<String, String> config,
        boolean localOnly) throws NacosException {
        pluginHandler.updatePluginConfig(pluginType, pluginName, config, localOnly);
    }
    
    /**
     * Get plugin availability across cluster nodes.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return node availability map
     * @throws NacosException if there is an issue fetching the availability
     */
    public Map<String, Boolean> getPluginAvailability(String pluginType, String pluginName)
        throws NacosException {
        return pluginHandler.getPluginAvailability(pluginType, pluginName);
    }
}
