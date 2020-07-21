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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.connection.Connection;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.RemotePushService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    }
    
    @Override
    public void clientConnected(Connection connect) {
        connectionCache.put(connect.getConnectionId(), new RemotingConnection(connect));
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        RemotingConnection remotingConnection = connectionCache.remove(connect.getConnectionId());
        try {
            for (String each : remotingConnection.getInstanceIndex().keySet()) {
                Set<Instance> instances = remotingConnection.getInstanceIndex().get(each);
                serviceManager.removeInstance(KeyBuilder.getNamespace(each), KeyBuilder.getServiceName(each), true,
                        instances.toArray(new Instance[instances.size()]));
            }
            for (String each : remotingConnection.getSubscriberIndex().keySet()) {
                remotePushService.removeAllSubscribeForService(each);
            }
        } catch (NacosException e) {
            Loggers.SRV_LOG
                    .error(String.format("Remove context of connection %s failed", connect.getConnectionId()), e);
        }
    }
    
    public RemotingConnection getRemotingConnection(String connectionId) {
        return connectionCache.get(connectionId);
    }
}
