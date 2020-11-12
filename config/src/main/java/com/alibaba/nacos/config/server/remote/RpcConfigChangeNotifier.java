/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.remote.response.AbstractPushCallBack;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ConfigChangeNotifier.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifier.java, v 0.1 2020年07月20日 3:00 PM liuzunfei Exp $
 */
@Component(value = "rpcConfigChangeNotifier")
public class RpcConfigChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    public RpcConfigChangeNotifier() {
        NotifyCenter.registerSubscriber(this);
    }
    
    @Autowired
    ConfigChangeListenContext configChangeListenContext;
    
    @Autowired
    private RpcPushService rpcPushService;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    /**
     * adaptor to config module ,when server side config change ,invoke this method.
     *
     * @param groupKey     groupKey
     * @param notifyRequet notifyRequet
     */
    public void configDataChanged(String groupKey, final ConfigChangeNotifyRequest notifyRequet) {
    
        Set<String> listeners = configChangeListenContext.getListeners(groupKey);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        Set<String> clients = new HashSet<>(listeners);
        int notifyCount = 0;
        if (!CollectionUtils.isEmpty(clients)) {
            for (final String client : clients) {
                Connection connection = connectionManager.getConnection(client);
                if (connection == null) {
                    continue;
                }
    
                if (notifyRequet.isBeta()) {
                    List<String> betaIps = notifyRequet.getBetaIps();
                    if (betaIps != null && !betaIps.contains(connection.getMetaInfo().getClientIp())) {
                        continue;
                    }
                }
    
                RpcPushTask rpcPushRetryTask = new RpcPushTask(notifyRequet, 50, client,
                        connection.getMetaInfo().getClientIp(), connection.getMetaInfo().getConnectionId());
                push(rpcPushRetryTask);
                notifyCount++;
            }
        }
    
        Loggers.REMOTE_PUSH.info("push [{}] clients ,groupKey=[{}]", clients == null ? 0 : notifyCount, groupKey);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        String groupKey = event.groupKey;
        boolean isBeta = event.isBeta;
        List<String> betaIps = event.betaIps;
        String[] strings = GroupKey.parseKey(groupKey);
        String dataid = strings[0];
        String group = strings[1];
        String tenant = strings.length > 2 ? strings[2] : "";
        ConfigChangeNotifyRequest notifyRequest = ConfigChangeNotifyRequest.build(dataid, group, tenant);
        notifyRequest.setBeta(isBeta);
        notifyRequest.setBetaIps(betaIps);
        if (PropertyUtil.isPushContent()) {
            notifyRequest.setContent(event.content);
            notifyRequest.setType(event.type);
            notifyRequest.setLastModifiedTs(event.lastModifiedTs);
            notifyRequest.setContentPush(true);
        }
        
        configDataChanged(groupKey, notifyRequest);
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
    
    class RpcPushTask implements Runnable {
        
        ConfigChangeNotifyRequest notifyRequet;
    
        int maxRetryTimes = -1;
        
        int tryTimes = 0;
        
        String clientId;
    
        String clientIp;
    
        String appName;
    
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequet, String clientId, String clientIp, String appName) {
            this(notifyRequet, -1, clientId, clientIp, appName);
        }
    
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequet, int maxRetryTimes, String clientId, String clientIp,
                String appName) {
            this.notifyRequet = notifyRequet;
            this.maxRetryTimes = maxRetryTimes;
            this.clientId = clientId;
            this.clientIp = clientIp;
            this.appName = appName;
        }
        
        public boolean isOverTimes() {
            return maxRetryTimes > 0 && this.tryTimes >= maxRetryTimes;
        }
        
        @Override
        public void run() {
            rpcPushService.pushWithCallback(clientId, notifyRequet, new AbstractPushCallBack(3000L) {
                int retryTimes = tryTimes;
                
                @Override
                public void onSuccess() {
                    //                    Loggers.REMOTE_PUSH.warn("push success.dataId={},group={},tenant={},clientId={},tryTimes={}",
                    //                            notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(), clientId,
                    //                            retryTimes);
                }
                
                @Override
                public void onFail(Throwable e) {
                    //                    Loggers.REMOTE_PUSH.warn("push fail.dataId={},group={},tenant={},clientId={},tryTimes={},errorMessage={}",
                    //                            notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(), clientId,
                    //                            retryTimes, e.getMessage());
                    //
                    push(RpcPushTask.this);
                }
    
            }, ConfigExecutor.getClientConfigNotifierServiceExecutor());
            
            tryTimes++;
        }
    }
    
    private void push(RpcPushTask retryTask) {
        ConfigChangeNotifyRequest notifyRequet = retryTask.notifyRequet;
        if (retryTask.isOverTimes()) {
            Loggers.CORE
                    .warn("push callback retry fail over times .dataId={},group={},tenant={},clientId={},will unregister client.",
                            notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(),
                            retryTask.clientId);
            connectionManager.unregister(retryTask.clientId);
            return;
        } else if (connectionManager.getConnection(retryTask.clientId) != null) {
            // first time :delay 0s; sencond time:delay 2s  ;third time :delay 4s
            ConfigExecutor.getClientConfigNotifierServiceExecutor()
                    .schedule(retryTask, retryTask.tryTimes * 2, TimeUnit.SECONDS);
        } else {
            // client is already offline,ingnore task.
        }
        
    }
}

