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
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.remote.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
     * @param groupKey      groupKey
     * @param notifyRequest notifyRequest
     */
    public void configDataChanged(String groupKey, final ConfigChangeNotifyRequest notifyRequest, boolean isBeta,
            List<String> betaIps,String content) {
        
        Set<String> listeners = configChangeListenContext.getListeners(groupKey);
        if (CollectionUtils.isEmpty(listeners)) {
            return;
        }
        
        int notifyCount = 0;
        for (final String client : listeners) {
            Connection connection = connectionManager.getConnection(client);
            if (connection == null) {
                continue;
            }
            
            if (isBeta) {
                if (betaIps != null && !betaIps.contains(connection.getMetaInfo().getClientIp())) {
                    continue;
                }
            }
            
            RpcPushTask rpcPushRetryTask = new RpcPushTask(notifyRequest, 50, client,
                    connection.getMetaInfo().getClientIp(), connection.getMetaInfo().getConnectionId());
            push(rpcPushRetryTask);
            notifyCount++;
        }
        
        Loggers.REMOTE_PUSH.info("push [{}] clients ,groupKey=[{}]", notifyCount, groupKey);
    
        notifyInternalConfigChange(groupKey,content,betaIps);
    }
    
    private void notifyInternalConfigChange(String groupKey,String content,List<String> betaIps) {
        if (GroupKey.getKey("nacos.internal.connection.limit.rule", "nacos").equals(groupKey)) {
            if (betaIps!=null&&!betaIps.contains(NetUtils.localIP())){
                return;
            }
            NotifyCenter.publishEvent(new ConnectionLimitRuleChangeEvent(content));
        }
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        String groupKey = event.groupKey;
        boolean isBeta = event.isBeta;
        List<String> betaIps = event.betaIps;
        String[] strings = GroupKey.parseKey(groupKey);
        String dataId = strings[0];
        String group = strings[1];
        String tenant = strings.length > 2 ? strings[2] : "";
        ConfigChangeNotifyRequest notifyRequest = ConfigChangeNotifyRequest.build(dataId, group, tenant);
        configDataChanged(groupKey, notifyRequest, isBeta, betaIps,event.content);
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
    
    class RpcPushTask implements Runnable {
        
        ConfigChangeNotifyRequest notifyRequest;
        
        int maxRetryTimes = -1;
        
        int tryTimes = 0;
        
        String clientId;
        
        String clientIp;
        
        String appName;
        
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequest, String clientId, String clientIp, String appName) {
            this(notifyRequest, -1, clientId, clientIp, appName);
        }
        
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequest, int maxRetryTimes, String clientId, String clientIp,
                String appName) {
            this.notifyRequest = notifyRequest;
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
            rpcPushService.pushWithCallback(clientId, notifyRequest, new AbstractPushCallBack(3000L) {
                int retryTimes = tryTimes;
                
                @Override
                public void onSuccess() {
                
                }
                
                @Override
                public void onFail(Throwable e) {
                    if (e instanceof ConnectionAlreadyClosedException) {
                        Loggers.CORE.warn(e.getMessage());
                    }
                    push(RpcPushTask.this);
                }
                
            }, ConfigExecutor.getClientConfigNotifierServiceExecutor());
            
            tryTimes++;
        }
    }
    
    private void push(RpcPushTask retryTask) {
        ConfigChangeNotifyRequest notifyRequet = retryTask.notifyRequest;
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

