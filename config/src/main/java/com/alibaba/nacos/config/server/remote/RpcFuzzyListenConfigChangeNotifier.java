/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.config.remote.request.FuzzyWatchNotifyChangeRequest;
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Notify remote clients about fuzzy listen configuration changes. Use subscriber mode to monitor local data changes,
 * and push notifications to remote clients accordingly.
 *
 * @author stone-98
 * @date 2024/3/18
 */
@Component(value = "rpcFuzzyListenConfigChangeNotifier")
public class RpcFuzzyListenConfigChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    private static final String POINT_FUZZY_LISTEN_CONFIG_PUSH = "POINT_FUZZY_LISTEN_CONFIG_PUSH";
    
    private static final String POINT_FUZZY_LISTEN_CONFIG_PUSH_SUCCESS = "POINT_FUZZY_LISTEN_CONFIG_PUSH_SUCCESS";
    
    private static final String POINT_FUZZY_LISTEN_CONFIG_PUSH_FAIL = "POINT_FUZZY_LISTEN_CONFIG_PUSH_FAIL";
    
    private final ConfigChangeListenContext configChangeListenContext;
    
    private final ConnectionManager connectionManager;
    
    private final RpcPushService rpcPushService;
    
    private final TpsControlManager tpsControlManager;
    
    private final ConfigFuzzyWatchContext configFuzzyWatchContext;
    /**
     * Constructs RpcFuzzyListenConfigChangeNotifier with the specified dependencies.
     *
     * @param configChangeListenContext The context for config change listening.
     * @param connectionManager         The manager for connections.
     * @param rpcPushService            The service for RPC push.
     */
    public RpcFuzzyListenConfigChangeNotifier(ConfigChangeListenContext configChangeListenContext,
            ConnectionManager connectionManager, RpcPushService rpcPushService,ConfigFuzzyWatchContext configFuzzyWatchContext) {
        this.configChangeListenContext = configChangeListenContext;
        this.connectionManager = connectionManager;
        this.rpcPushService = rpcPushService;
        this.tpsControlManager = ControlManagerCenter.getInstance().getTpsControlManager();
        this.configFuzzyWatchContext=configFuzzyWatchContext;
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        String groupKey = event.groupKey;
        String[] parseKey = GroupKey.parseKey(groupKey);
        String dataId = parseKey[0];
        String group = parseKey[1];
        String tenant = parseKey.length > 2 ? parseKey[2] : "";
        
        for (String clientId : configFuzzyWatchContext.getConnectIdMatchedPatterns(groupKey)) {
            Connection connection = connectionManager.getConnection(clientId);
            if (null == connection) {
                Loggers.REMOTE_PUSH.warn(
                        "clientId not found, Config change notification not sent. clientId={},keyGroupPattern={}",
                        clientId, event.groupKey);
                continue;
            }
            ConnectionMeta metaInfo = connection.getMetaInfo();
            String clientIp = metaInfo.getClientIp();
            String appName = metaInfo.getAppName();
            boolean exists = ConfigCacheService.getContentCache(groupKey)!=null;
            FuzzyWatchNotifyChangeRequest request = new FuzzyWatchNotifyChangeRequest(tenant, group, dataId, exists);
            int maxPushRetryTimes = ConfigCommonConfig.getInstance().getMaxPushRetryTimes();
            RpcPushTask rpcPushTask = new RpcPushTask(request, maxPushRetryTimes, clientId, clientIp, appName);
            push(rpcPushTask);
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
    
    /**
     * Pushes the notification to remote clients.
     *
     * @param retryTask The task for retrying to push notification.
     */
    private void push(RpcPushTask retryTask) {
        FuzzyWatchNotifyChangeRequest notifyRequest = retryTask.notifyRequest;
        if (retryTask.isOverTimes()) {
            Loggers.REMOTE_PUSH.warn(
                    "push callback retry fail over times. dataId={},group={},tenant={},clientId={}, will unregister client.",
                    notifyRequest.getDataId(), notifyRequest.getGroup(), notifyRequest.getTenant(),
                    retryTask.connectionId);
            connectionManager.unregister(retryTask.connectionId);
        } else if (connectionManager.getConnection(retryTask.connectionId) != null) {
            // First time: delay 0s; Second time: delay 2s; Third time: delay 4s
            ConfigExecutor.getClientConfigNotifierServiceExecutor()
                    .schedule(retryTask, retryTask.tryTimes * 2L, TimeUnit.SECONDS);
        } else {
            // Client is already offline, ignore the task.
            Loggers.REMOTE_PUSH.warn(
                    "Client is already offline, ignore the task. dataId={},group={},tenant={},clientId={}",
                    notifyRequest.getDataId(), notifyRequest.getGroup(), notifyRequest.getTenant(),
                    retryTask.connectionId);
        }
    }
    
    /**
     * Represents a task for pushing notification to remote clients.
     */
    class RpcPushTask implements Runnable {
        
        FuzzyWatchNotifyChangeRequest notifyRequest;
        
        int maxRetryTimes;
        
        int tryTimes = 0;
        
        String connectionId;
        
        String clientIp;
        
        String appName;
        
        /**
         * Constructs a RpcPushTask with the specified parameters.
         *
         * @param notifyRequest The notification request to be sent.
         * @param maxRetryTimes The maximum number of retry times.
         * @param connectionId  The ID of the connection.
         * @param clientIp      The IP address of the client.
         * @param appName       The name of the application.
         */
        public RpcPushTask(FuzzyWatchNotifyChangeRequest notifyRequest, int maxRetryTimes, String connectionId,
                String clientIp, String appName) {
            this.notifyRequest = notifyRequest;
            this.maxRetryTimes = maxRetryTimes;
            this.connectionId = connectionId;
            this.clientIp = clientIp;
            this.appName = appName;
        }
        
        /**
         * Checks if the number of retry times exceeds the maximum limit.
         *
         * @return {@code true} if the number of retry times exceeds the maximum limit; otherwise, {@code false}.
         */
        public boolean isOverTimes() {
            return maxRetryTimes > 0 && this.tryTimes >= maxRetryTimes;
        }
        
        @Override
        public void run() {
            tryTimes++;
            TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
            tpsCheckRequest.setPointName(POINT_FUZZY_LISTEN_CONFIG_PUSH);
            if (!tpsControlManager.check(tpsCheckRequest).isSuccess()) {
                push(this);
            } else {
                long timeout = ConfigCommonConfig.getInstance().getPushTimeout();
                rpcPushService.pushWithCallback(connectionId, notifyRequest, new AbstractPushCallBack(timeout) {
                    @Override
                    public void onSuccess() {
                        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
                        tpsCheckRequest.setPointName(POINT_FUZZY_LISTEN_CONFIG_PUSH_SUCCESS);
                        tpsControlManager.check(tpsCheckRequest);
                    }
                    
                    @Override
                    public void onFail(Throwable e) {
                        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
                        tpsCheckRequest.setPointName(POINT_FUZZY_LISTEN_CONFIG_PUSH_FAIL);
                        tpsControlManager.check(tpsCheckRequest);
                        Loggers.REMOTE_PUSH.warn("Push fail, dataId={}, group={}, tenant={}, clientId={}",
                                notifyRequest.getDataId(), notifyRequest.getGroup(), notifyRequest.getTenant(),
                                connectionId, e);
                        push(RpcPushTask.this);
                    }
                    
                }, ConfigExecutor.getClientConfigNotifierServiceExecutor());
            }
        }
    }
}
