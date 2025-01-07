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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.model.event.ConfigFuzzyWatchEvent;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Handles batch fuzzy listen events and pushes corresponding notifications to clients.
 *
 * @author stone-98
 * @date 2024/3/18
 */
@Component(value = "configFuzzyWatchSyncNotifier")
public class ConfigFuzzyWatchSyncNotifier extends Subscriber<ConfigFuzzyWatchEvent> {
    
    private static final String FUZZY_LISTEN_CONFIG_DIFF_PUSH = "FUZZY_LISTEN_CONFIG_DIFF_PUSH_COUNT";
    
    private static final String FUZZY_LISTEN_CONFIG_DIFF_PUSH_SUCCESS = "FUZZY_LISTEN_CONFIG_DIFF_PUSH_SUCCESS";
    
    private static final String FUZZY_LISTEN_CONFIG_DIFF_PUSH_FAIL = "FUZZY_LISTEN_CONFIG_DIFF_PUSH_FAIL";
    
    private final ConnectionManager connectionManager;
    
    private final TpsControlManager tpsControlManager;
    
    private final RpcPushService rpcPushService;
    
    private final ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    public ConfigFuzzyWatchSyncNotifier(ConnectionManager connectionManager, RpcPushService rpcPushService,
            ConfigFuzzyWatchContextService configFuzzyWatchContextService) {
        this.connectionManager = connectionManager;
        this.tpsControlManager = ControlManagerCenter.getInstance().getTpsControlManager();
        this.rpcPushService = rpcPushService;
        this.configFuzzyWatchContextService = configFuzzyWatchContextService;
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * Pushes the retry task to the client connection manager for retrying the RPC push operation.
     *
     * @param retryTask         The retry task containing the RPC push request
     * @param connectionManager The connection manager for managing client connections
     */
    private static void push(FuzzyWatchRpcPushTask retryTask, ConnectionManager connectionManager) {
        ConfigFuzzyWatchSyncRequest notifyRequest = retryTask.notifyRequest;
        // Check if the maximum retry times have been reached
        if (retryTask.isOverTimes()) {
            // If over the maximum retry times, log a warning and unregister the client connection
            Loggers.REMOTE_PUSH.warn(
                    "Push callback retry failed over times. groupKeyPattern={}, clientId={}, will unregister client.",
                    notifyRequest.getGroupKeyPattern(), retryTask.connectionId);
            connectionManager.unregister(retryTask.connectionId);
        } else if (connectionManager.getConnection(retryTask.connectionId) != null) {
            // Schedule a retry task with an increasing delay based on the number of retries
            // First time: delay 0s; second time: delay 2s; third time: delay 4s, and so on
            ConfigExecutor.scheduleClientConfigNotifier(retryTask, retryTask.tryTimes * 2L, TimeUnit.SECONDS);
        } else {
            // If the client is already offline, ignore the task
            Loggers.REMOTE_PUSH.warn("Client is already offline, ignore the task. groupKeyPattern={}, clientId={}",
                    notifyRequest.getGroupKeyPattern(), retryTask.connectionId);
        }
    }
    
    /**
     * Handles the ConfigBatchFuzzyListenEvent. This method is responsible for processing batch fuzzy listen events and
     * pushing corresponding notifications to clients.
     *
     * @param event The ConfigBatchFuzzyListenEvent to handle
     */
    @Override
    public void onEvent(ConfigFuzzyWatchEvent event) {
        // Get the connection for the client
        Connection connection = connectionManager.getConnection(event.getConnectionId());
        if (connection == null) {
            Loggers.REMOTE_PUSH.warn(
                    "connectionId not found, Config diff notification not sent. connectionId={},keyGroupPattern={}",
                    event.getConnectionId(), event.getGroupKeyPattern());
            // If connection is not available, return
            return;
        }
        
        // Retrieve meta information for the connection
        ConnectionMeta metaInfo = connection.getMetaInfo();
        String clientIp = metaInfo.getClientIp();
        
        // Match client effective group keys based on the event pattern, client IP, and tag
        Set<String> matchGroupKeys = configFuzzyWatchContextService.matchGroupKeys(event.getGroupKeyPattern());
        
        // Retrieve existing group keys for the client from the event
        Set<String> clientExistingGroupKeys = event.getClientExistingGroupKeys();
        
        // Check if both matched and existing group keys are empty, if so, return
        if (CollectionUtils.isEmpty(matchGroupKeys) && CollectionUtils.isEmpty(clientExistingGroupKeys)) {
            return;
        }
        
        // Calculate and merge configuration states based on matched and existing group keys
        List<FuzzyGroupKeyPattern.GroupKeyState> configStates = FuzzyGroupKeyPattern.diffGroupKeys(matchGroupKeys, clientExistingGroupKeys);
        
        // If no diff config states are available, return
        if (CollectionUtils.isEmpty(configStates)) {
            return;
        }
        
        int batchSize = ConfigCommonConfig.getInstance().getBatchSize();
        // Divide config states into batches
        List<List<FuzzyGroupKeyPattern.GroupKeyState>> divideConfigStatesIntoBatches = divideConfigStatesIntoBatches(configStates, batchSize);
        
        // Calculate the number of batches and initialize push batch finish count
        int originBatchSize = divideConfigStatesIntoBatches.size();
        AtomicInteger pushBatchFinishCount = new AtomicInteger(0);
        
        // Iterate over each batch of config states
        for (List<FuzzyGroupKeyPattern.GroupKeyState> configStateList : divideConfigStatesIntoBatches) {
            // Map config states to FuzzyListenNotifyDiffRequest.Context objects
            Set<ConfigFuzzyWatchSyncRequest.Context> contexts = configStateList.stream().map(state -> {
                
                String changeType = state.isExist() ? Constants.ConfigChangedType.ADD_CONFIG
                        : Constants.ConfigChangedType.DELETE_CONFIG;
                return ConfigFuzzyWatchSyncRequest.Context.build(state.getGroupKey(), changeType);
            }).collect(Collectors.toSet());
            
            // Build FuzzyListenNotifyDiffRequest with contexts and pattern
            ConfigFuzzyWatchSyncRequest request =
                    event.isInitializing() ? ConfigFuzzyWatchSyncRequest.buildInitRequest(contexts,
                            event.getGroupKeyPattern())
                            : ConfigFuzzyWatchSyncRequest.buildDiffSyncRequest(contexts, event.getGroupKeyPattern());
            
            int maxPushRetryTimes = ConfigCommonConfig.getInstance().getMaxPushRetryTimes();
            // Create RPC push task and push the request to the client
            FuzzyWatchRpcPushTask fuzzyWatchRpcPushTask = new FuzzyWatchRpcPushTask(request, pushBatchFinishCount,
                    originBatchSize, maxPushRetryTimes, event.getConnectionId(), clientIp, metaInfo.getAppName());
            push(fuzzyWatchRpcPushTask, connectionManager);
        }
    }
    
 
    @Override
    public Class<? extends Event> subscribeType() {
        return ConfigFuzzyWatchEvent.class;
    }
    
    /**
     * Divides a collection of items into batches.
     *
     * @param configStates The collection of items to be divided into batches
     * @param batchSize    The size of each batch
     * @param <T>          The type of items in the collection
     * @return A list of batches, each containing a sublist of items
     */
    private <T> List<List<T>> divideConfigStatesIntoBatches(Collection<T> configStates, int batchSize) {
        // Initialize an index to track the current batch number
        AtomicInteger index = new AtomicInteger();
        
        // Group the elements into batches based on their index divided by the batch size
        return new ArrayList<>(
                configStates.stream().collect(Collectors.groupingBy(e -> index.getAndIncrement() / batchSize))
                        .values());
    }
    
    
    /**
     * Represents a task for pushing FuzzyListenNotifyDiffRequest to clients.
     */
    class FuzzyWatchRpcPushTask implements Runnable {
        
        /**
         * The FuzzyListenNotifyDiffRequest to be pushed.
         */
        ConfigFuzzyWatchSyncRequest notifyRequest;
        
        /**
         * The maximum number of times to retry pushing the request.
         */
        int maxRetryTimes;
        
        /**
         * The current number of attempts made to push the request.
         */
        int tryTimes = 0;
        
        /**
         * The ID of the connection associated with the client.
         */
        String connectionId;
        
        /**
         * The IP address of the client.
         */
        String clientIp;
        
        /**
         * The name of the client's application.
         */
        String appName;
        
        /**
         * The counter for tracking the number of finished push batches.
         */
        AtomicInteger pushBatchFinishCount;
        
        /**
         * The original size of the batch before splitting.
         */
        int originBatchSize;
        
        /**
         * Constructs a new RpcPushTask with the specified parameters.
         *
         * @param notifyRequest        The FuzzyListenNotifyDiffRequest to be pushed
         * @param pushBatchFinishCount The counter for tracking the number of finished push batches
         * @param originBatchSize      The original size of the batch before splitting
         * @param maxRetryTimes        The maximum number of times to retry pushing the request
         * @param connectionId         The ID of the connection associated with the client
         * @param clientIp             The IP address of the client
         * @param appName              The name of the client's application
         */
        public FuzzyWatchRpcPushTask(ConfigFuzzyWatchSyncRequest notifyRequest, AtomicInteger pushBatchFinishCount,
                int originBatchSize, int maxRetryTimes, String connectionId, String clientIp, String appName) {
            this.notifyRequest = notifyRequest;
            this.pushBatchFinishCount = pushBatchFinishCount;
            this.originBatchSize = originBatchSize;
            this.maxRetryTimes = maxRetryTimes;
            this.connectionId = connectionId;
            this.clientIp = clientIp;
            this.appName = appName;
        }
        
        /**
         * Checks if the maximum number of retry times has been reached.
         *
         * @return true if the maximum number of retry times has been reached, otherwise false
         */
        public boolean isOverTimes() {
            return maxRetryTimes > 0 && this.tryTimes >= maxRetryTimes;
        }
        
        /**
         * Executes the task, attempting to push the request to the client.
         */
        @Override
        public void run() {
            tryTimes++;
            TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
            
            tpsCheckRequest.setPointName(FUZZY_LISTEN_CONFIG_DIFF_PUSH);
            if (!tpsControlManager.check(tpsCheckRequest).isSuccess()) {
                push(this, connectionManager);
            } else {
                rpcPushService.pushWithCallback(connectionId, notifyRequest,
                        new FuzzyWatchRpcPushCallback(this, tpsControlManager, connectionManager, pushBatchFinishCount,
                                originBatchSize), ConfigExecutor.getClientConfigNotifierServiceExecutor());
            }
        }
    }
    
    /**
     * Represents a callback for handling the result of an RPC push operation.
     */
    class FuzzyWatchRpcPushCallback extends AbstractPushCallBack {
        
        /**
         * The RpcPushTask associated with the callback.
         */
        FuzzyWatchRpcPushTask fuzzyWatchRpcPushTask;
        
        /**
         * The TpsControlManager for checking TPS limits.
         */
        TpsControlManager tpsControlManager;
        
        /**
         * The ConnectionManager for managing client connections.
         */
        ConnectionManager connectionManager;
        
        /**
         * The counter for tracking the number of pushed batches.
         */
        AtomicInteger pushBatchCount;
        
        /**
         * The original size of the batch before splitting.
         */
        int originBatchSize;
        
        /**
         * Constructs a new RpcPushCallback with the specified parameters.
         *
         * @param fuzzyWatchRpcPushTask The RpcPushTask associated with the callback
         * @param tpsControlManager     The TpsControlManager for checking TPS limits
         * @param connectionManager     The ConnectionManager for managing client connections
         * @param pushBatchCount        The counter for tracking the number of pushed batches
         * @param originBatchSize       The original size of the batch before splitting
         */
        public FuzzyWatchRpcPushCallback(FuzzyWatchRpcPushTask fuzzyWatchRpcPushTask,
                TpsControlManager tpsControlManager, ConnectionManager connectionManager, AtomicInteger pushBatchCount,
                int originBatchSize) {
            // Set the timeout for the callback
            super(3000L);
            this.fuzzyWatchRpcPushTask = fuzzyWatchRpcPushTask;
            this.tpsControlManager = tpsControlManager;
            this.connectionManager = connectionManager;
            this.pushBatchCount = pushBatchCount;
            this.originBatchSize = originBatchSize;
        }
        
        /**
         * Handles the successful completion of the RPC push operation.
         */
        @Override
        public void onSuccess() {
            // Check TPS limits
            TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
            tpsCheckRequest.setPointName(FUZZY_LISTEN_CONFIG_DIFF_PUSH_SUCCESS);
            tpsControlManager.check(tpsCheckRequest);
            
            if (pushBatchCount.get() < originBatchSize) {
                pushBatchCount.incrementAndGet();
            } else if (pushBatchCount.get() == originBatchSize) {
                ConfigFuzzyWatchSyncRequest request = ConfigFuzzyWatchSyncRequest.buildInitFinishRequest(
                        fuzzyWatchRpcPushTask.notifyRequest.getGroupKeyPattern());
                push(new FuzzyWatchRpcPushTask(request, pushBatchCount, originBatchSize, 50,
                        fuzzyWatchRpcPushTask.connectionId, fuzzyWatchRpcPushTask.clientIp,
                        fuzzyWatchRpcPushTask.appName), connectionManager);
            }
        }
        
        /**
         * Handles the failure of the RPC push operation.
         *
         * @param e The exception thrown during the operation
         */
        @Override
        public void onFail(Throwable e) {
            // Check TPS limits
            TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
            tpsCheckRequest.setPointName(FUZZY_LISTEN_CONFIG_DIFF_PUSH_FAIL);
            tpsControlManager.check(tpsCheckRequest);
            
            // Log the failure and retry the task
            Loggers.REMOTE_PUSH.warn("Push fail, groupKeyPattern={}, clientId={}",
                    fuzzyWatchRpcPushTask.notifyRequest.getGroupKeyPattern(), fuzzyWatchRpcPushTask.connectionId, e);
            push(fuzzyWatchRpcPushTask, connectionManager);
        }
    }
}
