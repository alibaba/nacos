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
import com.alibaba.nacos.api.config.remote.request.FuzzyListenNotifyDiffRequest;
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.GroupKeyPattern;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.model.event.ConfigBatchFuzzyListenEvent;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles batch fuzzy listen events and pushes corresponding notifications to clients.
 *
 * @author stone-98
 * @date 2024/3/18
 */
@Component(value = "rpcFuzzyListenConfigDiffNotifier")
public class RpcFuzzyListenConfigDiffNotifier extends Subscriber<ConfigBatchFuzzyListenEvent> {
    
    private static final String FUZZY_LISTEN_CONFIG_DIFF_PUSH = "FUZZY_LISTEN_CONFIG_DIFF_PUSH_COUNT";
    
    private static final String FUZZY_LISTEN_CONFIG_DIFF_PUSH_SUCCESS = "FUZZY_LISTEN_CONFIG_DIFF_PUSH_SUCCESS";
    
    private static final String FUZZY_LISTEN_CONFIG_DIFF_PUSH_FAIL = "FUZZY_LISTEN_CONFIG_DIFF_PUSH_FAIL";
    
    private final ConnectionManager connectionManager;
    
    private final TpsControlManager tpsControlManager;
    
    private final RpcPushService rpcPushService;
    
    public RpcFuzzyListenConfigDiffNotifier(ConnectionManager connectionManager, RpcPushService rpcPushService) {
        this.connectionManager = connectionManager;
        this.tpsControlManager = ControlManagerCenter.getInstance().getTpsControlManager();
        this.rpcPushService = rpcPushService;
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * Pushes the retry task to the client connection manager for retrying the RPC push operation.
     *
     * @param retryTask         The retry task containing the RPC push request
     * @param connectionManager The connection manager for managing client connections
     */
    private static void push(RpcPushTask retryTask, ConnectionManager connectionManager) {
        FuzzyListenNotifyDiffRequest notifyRequest = retryTask.notifyRequest;
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
    public void onEvent(ConfigBatchFuzzyListenEvent event) {
        // Get the connection for the client
        Connection connection = connectionManager.getConnection(event.getClientId());
        if (connection == null) {
            // If connection is not available, return
            return;
        }
        
        // Retrieve meta information for the connection
        ConnectionMeta metaInfo = connection.getMetaInfo();
        String clientIp = metaInfo.getClientIp();
        String clientTag = metaInfo.getTag();
        
        // Match client effective group keys based on the event pattern, client IP, and tag
        Set<String> matchGroupKeys = ConfigCacheService.matchClientEffectiveGroupKeys(event.getKeyGroupPattern(),
                clientIp, clientTag);
        
        // Retrieve existing group keys for the client from the event
        Set<String> clientExistingGroupKeys = event.getClientExistingGroupKeys();
        
        // Check if both matched and existing group keys are empty, if so, return
        if (CollectionUtils.isEmpty(matchGroupKeys) && CollectionUtils.isEmpty(clientExistingGroupKeys)) {
            return;
        }
        
        // Calculate and merge configuration states based on matched and existing group keys
        List<ConfigState> configStates = calculateAndMergeToConfigState(matchGroupKeys, clientExistingGroupKeys);
        
        // If no config states are available, return
        if (CollectionUtils.isEmpty(configStates)) {
            return;
        }
        
        int batchSize = ConfigCommonConfig.getInstance().getBatchSize();
        // Divide config states into batches
        List<List<ConfigState>> divideConfigStatesIntoBatches = divideConfigStatesIntoBatches(configStates, batchSize);
        
        // Calculate the number of batches and initialize push batch finish count
        int originBatchSize = divideConfigStatesIntoBatches.size();
        AtomicInteger pushBatchFinishCount = new AtomicInteger(0);
        
        // Iterate over each batch of config states
        for (List<ConfigState> configStateList : divideConfigStatesIntoBatches) {
            // Map config states to FuzzyListenNotifyDiffRequest.Context objects
            Set<FuzzyListenNotifyDiffRequest.Context> contexts = configStateList.stream().map(state -> {
                String[] parseKey = GroupKey.parseKey(state.getGroupKey());
                String dataId = parseKey[0];
                String group = parseKey[1];
                String tenant = parseKey.length > 2 ? parseKey[2] : Constants.DEFAULT_NAMESPACE_ID;
                String changeType = event.isInitializing() ? Constants.ConfigChangeType.LISTEN_INIT
                        : (state.isExist() ? Constants.ConfigChangeType.ADD_CONFIG
                                : Constants.ConfigChangeType.DELETE_CONFIG);
                return FuzzyListenNotifyDiffRequest.Context.build(tenant, group, dataId, changeType);
            }).collect(Collectors.toSet());
            
            // Remove namespace from the pattern
            String patternWithoutNameSpace = GroupKeyPattern.getPatternRemovedNamespace(event.getKeyGroupPattern());
            
            // Build FuzzyListenNotifyDiffRequest with contexts and pattern
            FuzzyListenNotifyDiffRequest request = FuzzyListenNotifyDiffRequest.buildInitRequest(contexts,
                    patternWithoutNameSpace);
            
            int maxPushRetryTimes = ConfigCommonConfig.getInstance().getMaxPushRetryTimes();
            // Create RPC push task and push the request to the client
            RpcPushTask rpcPushTask = new RpcPushTask(request, pushBatchFinishCount, originBatchSize, maxPushRetryTimes,
                    event.getClientId(), clientIp, metaInfo.getAppName());
            push(rpcPushTask, connectionManager);
        }
    }
    
    /**
     * Calculates and merges the differences between the matched group keys and the client's existing group keys into a
     * list of ConfigState objects.
     *
     * @param matchGroupKeys          The matched group keys set
     * @param clientExistingGroupKeys The client's existing group keys set
     * @return The merged list of ConfigState objects representing the states to be added or removed
     */
    private List<ConfigState> calculateAndMergeToConfigState(Set<String> matchGroupKeys,
            Set<String> clientExistingGroupKeys) {
        // Calculate the set of group keys to be added and removed
        Set<String> addGroupKeys = new HashSet<>();
        if (CollectionUtils.isNotEmpty(matchGroupKeys)) {
            addGroupKeys.addAll(matchGroupKeys);
        }
        if (CollectionUtils.isNotEmpty(clientExistingGroupKeys)) {
            addGroupKeys.removeAll(clientExistingGroupKeys);
        }
        
        Set<String> removeGroupKeys = new HashSet<>();
        if (CollectionUtils.isNotEmpty(clientExistingGroupKeys)) {
            removeGroupKeys.addAll(clientExistingGroupKeys);
        }
        if (CollectionUtils.isNotEmpty(matchGroupKeys)) {
            removeGroupKeys.removeAll(matchGroupKeys);
        }
        
        // Convert the group keys to be added and removed into corresponding ConfigState objects and merge them into a list
        return Stream.concat(addGroupKeys.stream().map(groupKey -> new ConfigState(groupKey, true)),
                        removeGroupKeys.stream().map(groupKey -> new ConfigState(groupKey, false)))
                .collect(Collectors.toList());
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ConfigBatchFuzzyListenEvent.class;
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
     * ConfigState.
     */
    public static class ConfigState {
        
        /**
         * The group key associated with the configuration.
         */
        private String groupKey;
        
        /**
         * Indicates whether the configuration exists or not.
         */
        private boolean exist;
        
        /**
         * Constructs a new ConfigState instance with the given group key and existence flag.
         *
         * @param groupKey The group key associated with the configuration.
         * @param exist    {@code true} if the configuration exists, {@code false} otherwise.
         */
        public ConfigState(String groupKey, boolean exist) {
            this.groupKey = groupKey;
            this.exist = exist;
        }
        
        /**
         * Retrieves the group key associated with the configuration.
         *
         * @return The group key.
         */
        public String getGroupKey() {
            return groupKey;
        }
        
        /**
         * Sets the group key associated with the configuration.
         *
         * @param groupKey The group key to set.
         */
        public void setGroupKey(String groupKey) {
            this.groupKey = groupKey;
        }
        
        /**
         * Checks whether the configuration exists or not.
         *
         * @return {@code true} if the configuration exists, {@code false} otherwise.
         */
        public boolean isExist() {
            return exist;
        }
        
        /**
         * Sets the existence flag of the configuration.
         *
         * @param exist {@code true} if the configuration exists, {@code false} otherwise.
         */
        public void setExist(boolean exist) {
            this.exist = exist;
        }
    }
    
    /**
     * Represents a task for pushing FuzzyListenNotifyDiffRequest to clients.
     */
    class RpcPushTask implements Runnable {
        
        /**
         * The FuzzyListenNotifyDiffRequest to be pushed.
         */
        FuzzyListenNotifyDiffRequest notifyRequest;
        
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
        public RpcPushTask(FuzzyListenNotifyDiffRequest notifyRequest, AtomicInteger pushBatchFinishCount,
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
                        new RpcPushCallback(this, tpsControlManager, connectionManager, pushBatchFinishCount,
                                originBatchSize), ConfigExecutor.getClientConfigNotifierServiceExecutor());
            }
        }
    }
    
    /**
     * Represents a callback for handling the result of an RPC push operation.
     */
    class RpcPushCallback extends AbstractPushCallBack {
        
        /**
         * The RpcPushTask associated with the callback.
         */
        RpcPushTask rpcPushTask;
        
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
         * @param rpcPushTask       The RpcPushTask associated with the callback
         * @param tpsControlManager The TpsControlManager for checking TPS limits
         * @param connectionManager The ConnectionManager for managing client connections
         * @param pushBatchCount    The counter for tracking the number of pushed batches
         * @param originBatchSize   The original size of the batch before splitting
         */
        public RpcPushCallback(RpcPushTask rpcPushTask, TpsControlManager tpsControlManager,
                ConnectionManager connectionManager, AtomicInteger pushBatchCount, int originBatchSize) {
            // Set the timeout for the callback
            super(3000L);
            this.rpcPushTask = rpcPushTask;
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
                FuzzyListenNotifyDiffRequest request = FuzzyListenNotifyDiffRequest.buildInitFinishRequest(
                        rpcPushTask.notifyRequest.getGroupKeyPattern());
                push(new RpcPushTask(request, pushBatchCount, originBatchSize, 50, rpcPushTask.connectionId,
                        rpcPushTask.clientIp, rpcPushTask.appName), connectionManager);
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
                    rpcPushTask.notifyRequest.getGroupKeyPattern(), rpcPushTask.connectionId, e);
            push(rpcPushTask, connectionManager);
        }
    }
}
