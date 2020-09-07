/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.RemotePushService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Remoting connection holder.
 *
 * @author xiweng.yy
 */
@Component
public class RemotingConnectionHolder extends ClientConnectionEventListener {
    
    private final ConcurrentMap<String, RemotingConnection> connectionCache = new ConcurrentHashMap<>();
    
    private final RemotePushService remotePushService;
    
    private final ServiceManager serviceManager;
    
    public RemotingConnectionHolder(RemotePushService remotePushService, ServiceManager serviceManager) {
        this.remotePushService = remotePushService;
        this.serviceManager = serviceManager;
        GlobalExecutor.scheduleRemoteConnectionManager(new RemotingConnectionCleaner(this), 0,
                Constants.DEFAULT_HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void clientConnected(Connection connect) {
        Loggers.SRV_LOG.info("Client connection {} connect", connect.getConnectionId());
        if (!connectionCache.containsKey(connect.getConnectionId())) {
            connectionCache.put(connect.getConnectionId(), new RemotingConnection(connect));
        }
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        clientDisConnected(connect.getConnectionId());
    }
    
    private void clientDisConnected(String connectionId) {
        Loggers.SRV_LOG.info("Client connection {} disconnect, remove instances and subscribers", connectionId);
        RemotingConnection remotingConnection = connectionCache.remove(connectionId);
        if (null == remotingConnection) {
            return;
        }
        for (String each : remotingConnection.getSubscriberIndex().keySet()) {
            remotePushService.removeAllSubscribeForService(each);
        }
        for (Map.Entry<String, Set<Instance>> entry : remotingConnection.getInstanceIndex().entrySet()) {
            String namespace = KeyBuilder.getNamespace(entry.getKey());
            String serviceName = KeyBuilder.getServiceName(entry.getKey());
            for (Instance each : entry.getValue()) {
                try {
                    serviceManager.removeInstance(namespace, serviceName, true, each);
                } catch (NacosException e) {
                    Loggers.SRV_LOG.error("Remove instance {} for service {}##{} failed. ", each.toIpAddr(), namespace,
                            serviceName, e);
                }
            }
        }
    }
    
    public RemotingConnection getRemotingConnection(String connectionId) {
        return connectionCache.get(connectionId);
    }
    
    public Collection<String> getAllConnectionId() {
        return connectionCache.keySet();
    }
    
    /**
     * Renew remoting connection.
     *
     * @param connectionId connection id
     */
    public void renewRemotingConnection(String connectionId) {
        if (!connectionCache.containsKey(connectionId)) {
            return;
        }
        RemotingConnection remotingConnection = connectionCache.get(connectionId);
        remotingConnection.setLastHeartBeatTime(System.currentTimeMillis());
    }
    
    private static class RemotingConnectionCleaner implements Runnable {
        
        private final RemotingConnectionHolder remotingConnectionHolder;
        
        public RemotingConnectionCleaner(RemotingConnectionHolder remotingConnectionHolder) {
            this.remotingConnectionHolder = remotingConnectionHolder;
        }
        
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            for (String each : remotingConnectionHolder.getAllConnectionId()) {
                RemotingConnection remotingConnection = remotingConnectionHolder.getRemotingConnection(each);
                if (null != remotingConnection && isExpireConnection(currentTime, remotingConnection)) {
                    remotingConnectionHolder.clientDisConnected(each);
                }
            }
        }
        
        private boolean isExpireConnection(long currentTime, RemotingConnection remotingConnection) {
            return currentTime - remotingConnection.getLastHeartBeatTime() > Constants.DEFAULT_IP_DELETE_TIMEOUT * 2;
        }
    }
}
