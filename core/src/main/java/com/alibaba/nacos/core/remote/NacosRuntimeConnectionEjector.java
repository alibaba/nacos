/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;

/**
 * nacos runtime connection ejector.
 *
 * @author shiyiyue
 */
public class NacosRuntimeConnectionEjector extends RuntimeConnectionEjector {
    
    public NacosRuntimeConnectionEjector() {
    
    }
    
    /**
     * eject connections on runtime.
     */
    public void doEject() {
        try {
            
            Map<String, Connection> connections = connectionManager.connections;
            int totalCount = connections.size();
            Loggers.CONNECTION.info("Connection check task start");
            MetricsMonitor.getLongConnectionMonitor().set(totalCount);
            int currentSdkClientCount = connectionManager.currentSdkClientCount();
            
            Loggers.CONNECTION
                    .info("Long connection metrics detail ,Total count ={}, sdkCount={},clusterCount={}", totalCount,
                            currentSdkClientCount, (totalCount - currentSdkClientCount));
            
            Loggers.CONNECTION.info("Connection check task end");
            
        } catch (Throwable e) {
            Loggers.CONNECTION.error("Error occurs during connection check... ", e);
        }
    }
    
    @Override
    public String getName() {
        return "nacos";
    }
}
