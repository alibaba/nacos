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

import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.plugin.control.Loggers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
    
            Loggers.CONNECTION.info("Connection check task start");
    
            Map<String, Connection> connections = connectionManager.connections;
            int totalCount = connections.size();
            MetricsMonitor.getLongConnectionMonitor().set(totalCount);
            int currentSdkClientCount = connectionManager.currentSdkClientCount();
            
            Loggers.CONNECTION.info("Long connection metrics detail ,Total count ={}, sdkCount={},clusterCount={}",
                    totalCount, currentSdkClientCount, (totalCount - currentSdkClientCount));
            
            Set<String> outDatedConnections = new HashSet<>();
            long now = System.currentTimeMillis();
            //outdated connections collect.
            for (Map.Entry<String, Connection> entry : connections.entrySet()) {
                Connection client = entry.getValue();
                if (now - client.getMetaInfo().getLastActiveTime() >= KEEP_ALIVE_TIME) {
                    outDatedConnections.add(client.getMetaInfo().getConnectionId());
                }
            }
            
            // check out date connection
            Loggers.CONNECTION.info("Out dated connection ,size={}", outDatedConnections.size());
            if (CollectionUtils.isNotEmpty(outDatedConnections)) {
                Set<String> successConnections = new HashSet<>();
                final CountDownLatch latch = new CountDownLatch(outDatedConnections.size());
                for (String outDateConnectionId : outDatedConnections) {
                    try {
                        Connection connection = connectionManager.getConnection(outDateConnectionId);
                        if (connection != null) {
                            ClientDetectionRequest clientDetectionRequest = new ClientDetectionRequest();
                            connection.asyncRequest(clientDetectionRequest, new RequestCallBack() {
                                @Override
                                public Executor getExecutor() {
                                    return null;
                                }
                                
                                @Override
                                public long getTimeout() {
                                    return 5000L;
                                }
                                
                                @Override
                                public void onResponse(Response response) {
                                    latch.countDown();
                                    if (response != null && response.isSuccess()) {
                                        connection.freshActiveTime();
                                        successConnections.add(outDateConnectionId);
                                    }
                                }
                                
                                @Override
                                public void onException(Throwable e) {
                                    latch.countDown();
                                }
                            });
                            
                            Loggers.CONNECTION.info("[{}]send connection active request ", outDateConnectionId);
                        } else {
                            latch.countDown();
                        }
                        
                    } catch (ConnectionAlreadyClosedException e) {
                        latch.countDown();
                    } catch (Exception e) {
                        Loggers.CONNECTION.error("[{}]Error occurs when check client active detection ,error={}",
                                outDateConnectionId, e);
                        latch.countDown();
                    }
                }
                
                latch.await(5000L, TimeUnit.MILLISECONDS);
                Loggers.CONNECTION.info("Out dated connection check successCount={}", successConnections.size());
                
                for (String outDateConnectionId : outDatedConnections) {
                    if (!successConnections.contains(outDateConnectionId)) {
                        Loggers.CONNECTION.info("[{}]Unregister Out dated connection....", outDateConnectionId);
                        connectionManager.unregister(outDateConnectionId);
                    }
                }
            }
            
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
