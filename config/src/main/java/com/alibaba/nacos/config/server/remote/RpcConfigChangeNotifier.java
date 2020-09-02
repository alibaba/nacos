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
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ConfigChangeNotifier.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifier.java, v 0.1 2020年07月20日 3:00 PM liuzunfei Exp $
 */
@Component
public class RpcConfigChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    private ThreadPoolExecutor retryPushexecutors = new ThreadPoolExecutor(15, 30, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1000000), new ThreadPoolExecutor.AbortPolicy());
    
    private static final ScheduledExecutorService ASYNC_CONFIG_CHANGE_NOTIFY_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(Config.class), 100,
                    new NameThreadFactory("com.alibaba.nacos.config.server.remote.ConfigChangeNotifier"));
    
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
     * adaptor to config module ,when server side congif change ,invoke this method.
     *
     * @param groupKey     groupKey
     * @param notifyRequet notifyRequet
     */
    public void configDataChanged(String groupKey, final ConfigChangeNotifyRequest notifyRequet) {
    
        Set<String> clients = new HashSet<>(configChangeListenContext.getListeners(groupKey));
        int notifyCount = 0;
        if (!CollectionUtils.isEmpty(clients)) {
            for (final String client : clients) {
                Connection connection = connectionManager.getConnection(client);
                if (connection == null) {
                    continue;
                }
    
                if (notifyRequet.isBeta()) {
                    List<String> betaIps = notifyRequet.getBetaIps();
                    if (betaIps != null && betaIps.contains(connection.getMetaInfo().getClientIp())) {
                        continue;
                    }
                }
                
                RpcPushTask rpcPushRetryTask = new RpcPushTask(notifyRequet, 5, client);
                push(rpcPushRetryTask);
                notifyCount++;
            }
        }
    
        Loggers.RPC.info("push {} clients ,groupKey={}", clients == null ? 0 : notifyCount, groupKey);
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
    
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequet, String clientId) {
            this(notifyRequet, -1, clientId);
        }
        
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequet, int maxRetryTimes, String clientId) {
            this.notifyRequet = notifyRequet;
            this.maxRetryTimes = maxRetryTimes;
            this.clientId = clientId;
        }
        
        public boolean isOverTimes() {
            return maxRetryTimes > 0 && this.tryTimes >= maxRetryTimes;
        }
        
        @Override
        public void run() {
            rpcPushService.pushWithCallback(clientId, notifyRequet, new AbstractPushCallBack(500L) {
                int retryTimes = tryTimes;
                
                @Override
                public void onSuccess() {
                    Loggers.CORE.warn("push callback  success.dataId={},group={},tenant={},clientId={},tryTimes={}",
                            notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(), clientId,
                            retryTimes);
                }
                
                @Override
                public void onFail(Exception e) {
                    Loggers.CORE.warn("push callback  fail.dataId={},group={},tenant={},clientId={},tryTimes={}",
                            notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(), clientId,
                            retryTimes);
                    
                    push(RpcPushTask.this);
                }
                
                @Override
                public void onTimeout() {
                    Loggers.CORE.warn("push callback  timeout.dataId={},group={},tenant={},clientId={},tryTimes={}",
                            notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(), clientId,
                            retryTimes);
                    push(RpcPushTask.this);
                }
                
            });
    
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
            ASYNC_CONFIG_CHANGE_NOTIFY_EXECUTOR.schedule(retryTask, retryTask.tryTimes * 2, TimeUnit.SECONDS);
        } else {
            // client is already offline,ingnore task.
        }
        
    }
}

