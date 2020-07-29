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

import com.alibaba.nacos.api.remote.connection.Connection;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.event.RemotingHeartBeatEvent;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.RemotePushService;
import com.alibaba.nacos.naming.remote.task.RenewInstanceBeatTask;
import com.alibaba.nacos.naming.remote.worker.RemotingWorkersManager;
import org.springframework.stereotype.Component;

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
        NotifyCenter.registerSubscriber(new RemotingHeartBeatSubscriber(this));
    }
    
    @Override
    public void clientConnected(Connection connect) {
        Loggers.SRV_LOG.info("Client connection {} connect", connect.getConnectionId());
        connectionCache.put(connect.getConnectionId(), new RemotingConnection(connect));
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        Loggers.SRV_LOG
                .info("Client connection {} disconnect, remove instances and subscribers", connect.getConnectionId());
        RemotingConnection remotingConnection = connectionCache.remove(connect.getConnectionId());
        for (String each : remotingConnection.getSubscriberIndex().keySet()) {
            remotePushService.removeAllSubscribeForService(each);
        }
    }
    
    public RemotingConnection getRemotingConnection(String connectionId) {
        return connectionCache.get(connectionId);
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
        RemotingWorkersManager.dispatch(connectionId, new RenewInstanceBeatTask(remotingConnection, serviceManager));
    }
    
    private static class RemotingHeartBeatSubscriber extends Subscriber<RemotingHeartBeatEvent> {
        
        private final RemotingConnectionHolder remotingConnectionHolder;
    
        public RemotingHeartBeatSubscriber(RemotingConnectionHolder remotingConnectionHolder) {
            this.remotingConnectionHolder = remotingConnectionHolder;
        }
    
        @Override
        public void onEvent(RemotingHeartBeatEvent event) {
            remotingConnectionHolder.renewRemotingConnection(event.getConnectionId());
        }
    
        @Override
        public Class<? extends Event> subscribeType() {
            return RemotingHeartBeatEvent.class;
        }
    }
}
