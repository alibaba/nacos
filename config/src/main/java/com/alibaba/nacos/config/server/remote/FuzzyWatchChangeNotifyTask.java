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
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

import java.util.concurrent.TimeUnit;

/**
 * Represents a task for pushing notification to remote clients.
 */
class FuzzyWatchChangeNotifyTask implements Runnable {
    
    private static final String POINT_FUZZY_WATCH_CONFIG_PUSH = "POINT_FUZZY_WATCH_CONFIG_PUSH";
    
    private static final String POINT_FUZZY_WATCH_CONFIG_PUSH_SUCCESS = "POINT_FUZZY_WATCH_CONFIG_PUSH_SUCCESS";
    
    private static final String POINT_FUZZY_WATCH_CONFIG_PUSH_FAIL = "POINT_FUZZY_WATCH_CONFIG_PUSH_FAIL";
    
    ConfigFuzzyWatchChangeNotifyRequest notifyRequest;
    
    final ConnectionManager connectionManager;
    
    final RpcPushService rpcPushService;
    
    int maxRetryTimes;
    
    int tryTimes = 0;
    
    String connectionId;
    
    /**
     * Constructs a RpcPushTask with the specified parameters.
     *
     * @param notifyRequest The notification request to be sent.
     * @param maxRetryTimes The maximum number of retry times.
     * @param connectionId  The ID of the connection.
     */
    public FuzzyWatchChangeNotifyTask(ConnectionManager connectionManager, RpcPushService rpcPushService,
            ConfigFuzzyWatchChangeNotifyRequest notifyRequest, int maxRetryTimes, String connectionId) {
        this.connectionManager = connectionManager;
        this.rpcPushService = rpcPushService;
        this.notifyRequest = notifyRequest;
        this.maxRetryTimes = maxRetryTimes;
        this.connectionId = connectionId;
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
        
        if (isOverTimes()) {
            Loggers.REMOTE_PUSH.warn(
                    "push callback retry fail over times.groupKey={},,clientId={}, will unregister client.",
                    notifyRequest.getGroupKey(), connectionId);
            connectionManager.unregister(connectionId);
        } else if (connectionManager.getConnection(connectionId) == null) {
            // Client is already offline, ignore the task.
            Loggers.REMOTE_PUSH.warn(
                    "Client is already offline, ignore the task. dataId={},groupKey={},tenant={},clientId={}",
                    notifyRequest.getGroupKey(), connectionId);
            return;
        }
        
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setPointName(POINT_FUZZY_WATCH_CONFIG_PUSH);
        if (!ControlManagerCenter.getInstance().getTpsControlManager().check(tpsCheckRequest).isSuccess()) {
            scheduleSelf();
        } else {
            long timeout = ConfigCommonConfig.getInstance().getPushTimeout();
            rpcPushService.pushWithCallback(connectionId, notifyRequest, new AbstractPushCallBack(timeout) {
                @Override
                public void onSuccess() {
                    TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
                    tpsCheckRequest.setPointName(POINT_FUZZY_WATCH_CONFIG_PUSH_SUCCESS);
                    ControlManagerCenter.getInstance().getTpsControlManager().check(tpsCheckRequest);
                }
                
                @Override
                public void onFail(Throwable e) {
                    TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
                    tpsCheckRequest.setPointName(POINT_FUZZY_WATCH_CONFIG_PUSH_FAIL);
                    ControlManagerCenter.getInstance().getTpsControlManager().check(tpsCheckRequest);
                    Loggers.REMOTE_PUSH.warn("Push fail,  groupKey={},  clientId={}", notifyRequest.getGroupKey(),
                            connectionId, e);
                    FuzzyWatchChangeNotifyTask.this.scheduleSelf();
                }
                
            }, ConfigExecutor.getClientConfigNotifierServiceExecutor());
        }
    }
    
    void scheduleSelf() {
        ConfigExecutor.scheduleClientConfigNotifier(this, tryTimes * 2L, TimeUnit.SECONDS);
    }
}
