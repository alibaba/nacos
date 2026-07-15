/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster.remote.request;

/**
 * Plugin availability request for cluster RPC.
 * Used to query if a plugin is available on a specific node.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class PluginAvailabilityRequest extends AbstractClusterRequest {
    
    private String pluginId;
    
    private boolean queryAll;
    
    public PluginAvailabilityRequest() {
    }
    
    public String getPluginId() {
        return pluginId;
    }
    
    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
    
    public boolean isQueryAll() {
        return queryAll;
    }
    
    public void setQueryAll(boolean queryAll) {
        this.queryAll = queryAll;
    }
}
