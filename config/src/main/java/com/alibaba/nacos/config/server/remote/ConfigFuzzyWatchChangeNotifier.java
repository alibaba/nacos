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

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.ConfigFuzzyWatchContextService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
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

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.CONFIG_CHANGED;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;

/**
 * Notify remote clients about fuzzy listen configuration changes. Use subscriber mode to monitor local data changes,
 * and push notifications to remote clients accordingly.
 *
 * @author stone-98
 * @date 2024/3/18
 */
@Component(value = "fuzzyWatchConfigChangeNotifier")
public class ConfigFuzzyWatchChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    private static final String POINT_FUZZY_WATCH_CONFIG_PUSH = "POINT_FUZZY_WATCH_CONFIG_PUSH";
    
    private static final String POINT_FUZZY_WATCH_CONFIG_PUSH_SUCCESS = "POINT_FUZZY_WATCH_CONFIG_PUSH_SUCCESS";
    
    private static final String POINT_FUZZY_WATCH_CONFIG_PUSH_FAIL = "POINT_FUZZY_WATCH_CONFIG_PUSH_FAIL";
    
    private final ConnectionManager connectionManager;
    
    private final RpcPushService rpcPushService;
    
    private final TpsControlManager tpsControlManager;
    
    private final ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    /**
     * Constructs RpcFuzzyListenConfigChangeNotifier with the specified dependencies.
     *
     * @param connectionManager The manager for connections.
     * @param rpcPushService    The service for RPC push.
     */
    public ConfigFuzzyWatchChangeNotifier(ConnectionManager connectionManager, RpcPushService rpcPushService,
            ConfigFuzzyWatchContextService configFuzzyWatchContextService) {
        this.connectionManager = connectionManager;
        this.rpcPushService = rpcPushService;
        this.tpsControlManager = ControlManagerCenter.getInstance().getTpsControlManager();
        this.configFuzzyWatchContextService = configFuzzyWatchContextService;
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        
        boolean exists = ConfigCacheService.getContentCache(event.groupKey) != null;
        //can not recognize add or update,  set config_changed here
        String changedType = exists ? CONFIG_CHANGED : DELETE_CONFIG;
        boolean needNotify = configFuzzyWatchContextService.syncGroupKeyContext(event.groupKey, changedType);
        if (needNotify) {
            for (String clientId : configFuzzyWatchContextService.getMatchedClients(event.groupKey)) {
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
                
                ConfigFuzzyWatchChangeNotifyRequest request = new ConfigFuzzyWatchChangeNotifyRequest(event.groupKey,
                        changedType);
                int maxPushRetryTimes = ConfigCommonConfig.getInstance().getMaxPushRetryTimes();
                RpcPushTask rpcPushTask = new RpcPushTask(request, maxPushRetryTimes, clientId, clientIp, appName);
                push(rpcPushTask);
            }
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
        ConfigFuzzyWatchChangeNotifyRequest notifyRequest = retryTask.notifyRequest;
        if (retryTask.isOverTimes()) {
            Loggers.REMOTE_PUSH.warn(
                    "push callback retry fail over times.groupKey={},,clientId={}, will unregister client.",
                    notifyRequest.getGroupKey(), retryTask.connectionId);
            connectionManager.unregister(retryTask.connectionId);
        } else if (connectionManager.getConnection(retryTask.connectionId) != null) {
            // First time: delay 0s; Second time: delay 2s; Third time: delay 4s
            ConfigExecutor.getClientConfigNotifierServiceExecutor()
                    .schedule(retryTask, retryTask.tryTimes * 2L, TimeUnit.SECONDS);
        } else {
            // Client is already offline, ignore the task.
            Loggers.REMOTE_PUSH.warn(
                    "Client is already offline, ignore the task. dataId={},groupKey={},tenant={},clientId={}",
                    notifyRequest.getGroupKey(), retryTask.connectionId);
        }
    }
    
    /**
     * Represents a task for pushing notification to remote clients.
     */
    class RpcPushTask implements Runnable {
        
        ConfigFuzzyWatchChangeNotifyRequest notifyRequest;
        
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
        public RpcPushTask(ConfigFuzzyWatchChangeNotifyRequest notifyRequest, int maxRetryTimes, String connectionId,
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
            tpsCheckRequest.setPointName(POINT_FUZZY_WATCH_CONFIG_PUSH);
            if (!tpsControlManager.check(tpsCheckRequest).isSuccess()) {
                push(this);
            } else {
                long timeout = ConfigCommonConfig.getInstance().getPushTimeout();
                rpcPushService.pushWithCallback(connectionId, notifyRequest, new AbstractPushCallBack(timeout) {
                    @Override
                    public void onSuccess() {
                        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
                        tpsCheckRequest.setPointName(POINT_FUZZY_WATCH_CONFIG_PUSH_SUCCESS);
                        tpsControlManager.check(tpsCheckRequest);
                    }
                    
                    @Override
                    public void onFail(Throwable e) {
                        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
                        tpsCheckRequest.setPointName(POINT_FUZZY_WATCH_CONFIG_PUSH_FAIL);
                        tpsControlManager.check(tpsCheckRequest);
                        Loggers.REMOTE_PUSH.warn("Push fail,  groupKey={},  clientId={}", notifyRequest.getGroupKey(),
                                connectionId, e);
                        push(RpcPushTask.this);
                    }
                    
                }, ConfigExecutor.getClientConfigNotifierServiceExecutor());
            }
        }
    }
}
