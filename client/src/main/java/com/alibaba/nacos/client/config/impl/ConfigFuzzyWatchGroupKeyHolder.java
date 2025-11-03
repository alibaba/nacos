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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchChangeNotifyResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchSyncResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.client.config.common.GroupKey;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.ADD_CONFIG;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.CONFIG_CHANGED;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_RESOURCE_CHANGED;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_CANCEL_WATCH;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_WATCH;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;

/**
 * config fuzzy watch context holder.
 *
 * @author shiyiyue
 */
public class ConfigFuzzyWatchGroupKeyHolder extends SmartSubscriber implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(ClientWorker.class);
    
    private final ClientWorker.ConfigRpcTransportClient agent;
    
    private final String clientUuid;
    
    /**
     * fuzzyListenExecuteBell.
     */
    private final BlockingQueue<Object> fuzzyListenExecuteBell = new ArrayBlockingQueue<>(1);
    
    private final Object bellItem = new Object();
    
    private final AtomicLong fuzzyListenLastAllSyncTime = new AtomicLong(System.currentTimeMillis());
    
    private static final long FUZZY_LISTEN_ALL_SYNC_INTERNAL = 3 * 60 * 1000;

    private ExecutorService fuzzyWatcherExecutor;

    private String taskId = "0";
    
    /**
     * fuzzyListenGroupKey -> fuzzyListenContext.
     */
    private final AtomicReference<Map<String, ConfigFuzzyWatchContext>> fuzzyListenContextMap = new AtomicReference<>(
            new HashMap<>());
    
    public ConfigFuzzyWatchGroupKeyHolder(ClientWorker.ConfigRpcTransportClient agent, String clientUuid) {
        this.clientUuid = clientUuid;
        this.agent = agent;
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * start.
     */
    public void start() {
        fuzzyWatcherExecutor = Executors.newSingleThreadScheduledExecutor(
                new NameThreadFactory("com.alibaba.nacos.client.fuzzy-watcher-executor")
        );
        fuzzyWatcherExecutor.submit(() -> {
            while (!fuzzyWatcherExecutor.isShutdown() && !fuzzyWatcherExecutor.isTerminated()) {
                try {
                    fuzzyListenExecuteBell.poll(5L, TimeUnit.SECONDS);
                    if (fuzzyWatcherExecutor.isShutdown() || fuzzyWatcherExecutor.isTerminated()) {
                        continue;
                    }
                    executeConfigFuzzyListen();
                } catch (Throwable e) {
                    LOGGER.error("[rpc-fuzzy-listen-execute] rpc fuzzy listen exception", e);
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException interruptedException) {
                        //ignore
                    }
                    notifyFuzzyWatchSync();
                }
            }
        });
    }

    /**
     * Deregistering it from the NotifyCenter and shutting down the executor.
     */
    @Override
    public void shutdown() {
        // deregister subscriber which registered in constructor
        NotifyCenter.deregisterSubscriber(this);
        if (fuzzyWatcherExecutor != null && !fuzzyWatcherExecutor.isShutdown()) {
            fuzzyWatcherExecutor.shutdown();
        }
    }
    
    /**
     * Removes the fuzzy listen context for the specified data ID pattern and group.
     *
     * @param groupKeyPattern The pattern of the data ID.
     */
    public void removeFuzzyListenContext(String groupKeyPattern) {
        synchronized (fuzzyListenContextMap) {
            Map<String, ConfigFuzzyWatchContext> copy = new HashMap<>(fuzzyListenContextMap.get());
            copy.remove(groupKeyPattern);
            fuzzyListenContextMap.set(copy);
        }
        LOGGER.info("[{}] [fuzzy-watch-unsubscribe] {}", agent.getName(), groupKeyPattern);
    }
    
    /**
     * register fuzzy watcher.
     *
     * @param dataIdPattern          dataIdPattern.
     * @param groupPattern           groupPattern.
     * @param fuzzyWatchEventWatcher fuzzyWatchEventWatcher.
     * @return ConfigFuzzyWatchContext
     */
    public ConfigFuzzyWatchContext registerFuzzyWatcher(String dataIdPattern, String groupPattern,
            FuzzyWatchEventWatcher fuzzyWatchEventWatcher) {
        if (!agent.isAbilitySupportedByServer(AbilityKey.SERVER_FUZZY_WATCH)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support fuzzy watch feature.");
        }
        ConfigFuzzyWatchContext configFuzzyWatchContext = initFuzzyWatchContextIfAbsent(dataIdPattern, groupPattern);
        ConfigFuzzyWatcherWrapper configFuzzyWatcherWrapper = new ConfigFuzzyWatcherWrapper(fuzzyWatchEventWatcher);
        if (configFuzzyWatchContext.addWatcher(configFuzzyWatcherWrapper)) {
            if (configFuzzyWatchContext.getReceivedGroupKeys() != null) {
                for (String groupKey : configFuzzyWatchContext.getReceivedGroupKeys()) {
                    ConfigFuzzyWatchNotifyEvent configFuzzyWatchNotifyEvent = ConfigFuzzyWatchNotifyEvent.buildEvent(
                            groupKey, configFuzzyWatchContext.getGroupKeyPattern(), ADD_CONFIG, FUZZY_WATCH_INIT_NOTIFY,
                            configFuzzyWatcherWrapper.getUuid());
                    NotifyCenter.publishEvent(configFuzzyWatchNotifyEvent);
                }
            }
        }
        return configFuzzyWatchContext;
    }
    
    /**
     * Retrieves the FuzzyListenContext for the given data ID pattern and group.
     *
     * @param dataIdPattern The data ID pattern.
     * @param groupPattern  The group name pattern.
     * @return The corresponding FuzzyListenContext, or null if not found.
     */
    public ConfigFuzzyWatchContext getFuzzyListenContext(String dataIdPattern, String groupPattern) {
        return fuzzyListenContextMap.get()
                .get(FuzzyGroupKeyPattern.generatePattern(dataIdPattern, groupPattern, agent.getTenant()));
    }
    
    /**
     * Handles a fuzzy listen init notify request.
     *
     * <p>This method processes the incoming fuzzy listen init notify request from a client. It updates the fuzzy
     * listen context based on the request's information, and publishes events if necessary.
     *
     * @param request The fuzzy listen init notify request to handle.
     * @return A {@link ConfigFuzzyWatchSyncResponse} indicating the result of handling the request.
     */
    ConfigFuzzyWatchSyncResponse handleFuzzyWatchSyncNotifyRequest(ConfigFuzzyWatchSyncRequest request) {
        String groupKeyPattern = request.getGroupKeyPattern();
        ConfigFuzzyWatchContext context = fuzzyListenContextMap.get().get(groupKeyPattern);
        if (Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY.equals(request.getSyncType())) {
            LOGGER.info("[{}] [fuzzy-watch] init-notify-finished, pattern ->{}, match group keys count {}",
                    agent.getName(), request.getGroupKeyPattern(), context.getReceivedGroupKeysCount());
            context.markInitializationComplete();
            return new ConfigFuzzyWatchSyncResponse();
        }
        
        LOGGER.info(
                "[{}] [fuzzy-watch] sync notify , pattern ->{},syncType={},,syncCount={},totalBatch={},currentBatch={}",
                agent.getName(), request.getGroupKeyPattern(), request.getSyncType(), request.getContexts().size(),
                request.getTotalBatch(), request.getCurrentBatch());
        
        for (ConfigFuzzyWatchSyncRequest.Context requestContext : request.getContexts()) {
            switch (requestContext.getChangedType()) {
                case ADD_CONFIG:
                    if (context.addReceivedGroupKey(requestContext.getGroupKey())) {
                        LOGGER.info("[{}] [fuzzy-watch-diff-sync-push] local match group key added ,pattern ->{}, "
                                        + "group key  ->{},publish fuzzy watch notify event", agent.getName(),
                                request.getGroupKeyPattern(), requestContext.getGroupKey());
                        NotifyCenter.publishEvent(ConfigFuzzyWatchNotifyEvent.buildEvent(requestContext.getGroupKey(),
                                request.getGroupKeyPattern(), requestContext.getChangedType(), request.getSyncType(),
                                this.clientUuid));
                    }
                    break;
                case DELETE_CONFIG:
                    if (context.removeReceivedGroupKey(requestContext.getGroupKey())) {
                        LOGGER.info("[{}] [fuzzy-watch-diff-sync-push] local match group key remove ,pattern ->{}, "
                                        + "group key  ->{},publish fuzzy watch notify event", agent.getName(),
                                request.getGroupKeyPattern(), requestContext.getGroupKey());
                        NotifyCenter.publishEvent(ConfigFuzzyWatchNotifyEvent.buildEvent(requestContext.getGroupKey(),
                                request.getGroupKeyPattern(), requestContext.getChangedType(), request.getSyncType(),
                                this.clientUuid));
                    }
                    break;
                default:
                    LOGGER.warn("Invalid config change type: {}", requestContext.getChangedType());
                    break;
            }
        }
        return new ConfigFuzzyWatchSyncResponse();
    }
    
    /**
     * Removes a fuzzy listen listener for the specified data ID pattern, group, and listener.
     *
     * @param dataIdPattern The pattern of the data ID.
     * @param groupPattern  The group of the configuration.
     * @param watcher       The listener to remove.
     * @throws NacosException If an error occurs while removing the listener.
     */
    public void removeFuzzyWatcher(String dataIdPattern, String groupPattern, FuzzyWatchEventWatcher watcher) {
        ConfigFuzzyWatchContext configFuzzyWatchContext = getFuzzyListenContext(dataIdPattern, groupPattern);
        if (configFuzzyWatchContext != null) {
            synchronized (configFuzzyWatchContext) {
                configFuzzyWatchContext.removeWatcher(watcher);
                if (configFuzzyWatchContext.getConfigFuzzyWatcherWrappers().isEmpty()) {
                    configFuzzyWatchContext.setDiscard(true);
                    configFuzzyWatchContext.setConsistentWithServer(false);
                }
            }
        }
    }
    
    /**
     * Handles a fuzzy listen notify change request.
     *
     * <p>This method processes the incoming fuzzy listen notify change request from a client. It updates the fuzzy
     * listen context based on the request's information, and publishes events if necessary.
     *
     * @param request The fuzzy listen notify change request to handle.
     */
    ConfigFuzzyWatchChangeNotifyResponse handlerFuzzyWatchChangeNotifyRequest(
            ConfigFuzzyWatchChangeNotifyRequest request) {
        
        LOGGER.info("[{}] [fuzzy-watch-change-notify-push] changeType={},groupKey={}", agent.getName(),
                request.getChangeType(), request.getGroupKey());
        
        Map<String, ConfigFuzzyWatchContext> listenContextMap = fuzzyListenContextMap.get();
        String[] groupItems = GroupKey.parseKey(request.getGroupKey());
        Set<String> matchedPatterns = FuzzyGroupKeyPattern.filterMatchedPatterns(listenContextMap.keySet(),
                groupItems[0], groupItems[1], groupItems[2]);
        for (String matchedPattern : matchedPatterns) {
            ConfigFuzzyWatchContext context = listenContextMap.get(matchedPattern);
            if (ADD_CONFIG.equals(request.getChangeType()) || CONFIG_CHANGED.equals(request.getChangeType())) {
                if (context.addReceivedGroupKey(request.getGroupKey())) {
                    LOGGER.info("[{}] [fuzzy-watch-change-notify-push] match group key added ,pattern={},groupKey={}",
                            agent.getName(), request.getChangeType(), request.getGroupKey());
                    
                    NotifyCenter.publishEvent(
                            ConfigFuzzyWatchNotifyEvent.buildEvent(request.getGroupKey(), matchedPattern, ADD_CONFIG,
                                    FUZZY_WATCH_RESOURCE_CHANGED, this.clientUuid));
                }
            } else if (DELETE_CONFIG.equals(request.getChangeType()) && context.removeReceivedGroupKey(
                    request.getGroupKey())) {
                NotifyCenter.publishEvent(ConfigFuzzyWatchNotifyEvent.buildEvent(request.getGroupKey(), matchedPattern,
                        Constants.ConfigChangedType.DELETE_CONFIG, FUZZY_WATCH_RESOURCE_CHANGED, this.clientUuid));
                
            }
        }
        return new ConfigFuzzyWatchChangeNotifyResponse();
    }
    
    void notifyFuzzyWatchSync() {
        fuzzyListenExecuteBell.offer(bellItem);
        
    }
    
    /**
     * Execute fuzzy listen configuration changes.
     *
     * <p>This method iterates through all fuzzy listen contexts and determines whether they need to be added or
     * removed based on their consistency with the server and discard status. It then calls the appropriate method to
     * execute the fuzzy listen operation.
     *
     * @throws NacosException If an error occurs during the execution of fuzzy listen configuration changes.
     */
    public void executeConfigFuzzyListen() throws NacosException {
        
        // Obtain the current timestamp
        long now = System.currentTimeMillis();
        
        // Determine whether a full synchronization is needed
        boolean needAllSync = now - fuzzyListenLastAllSyncTime.get() >= FUZZY_LISTEN_ALL_SYNC_INTERNAL;
        
        List<ConfigFuzzyWatchContext> needSyncContexts = new ArrayList<>();
        // Iterate through all fuzzy listen contexts
        for (ConfigFuzzyWatchContext context : fuzzyListenContextMap.get().values()) {
            // Check if the context is consistent with the server
            if (context.isConsistentWithServer()) {
                context.syncFuzzyWatchers();
                // Skip if a full synchronization is not needed
                if (!needAllSync) {
                    continue;
                }
            }
            
            needSyncContexts.add(context);
        }
        
        // Execute fuzzy listen operation for addition
        doExecuteConfigFuzzyListen(needSyncContexts);
        
        // Update last all sync time if a full synchronization was performed
        if (needAllSync) {
            fuzzyListenLastAllSyncTime.set(now);
        }
    }
    
    void resetConsistenceStatus() {
        Collection<ConfigFuzzyWatchContext> configFuzzyWatchContexts = fuzzyListenContextMap.get().values();
        
        for (ConfigFuzzyWatchContext context : configFuzzyWatchContexts) {
            context.setConsistentWithServer(false);
        }
    }
    
    /**
     * Execute fuzzy listen configuration changes for a specific map of contexts.
     *
     * <p>This method submits tasks to execute fuzzy listen operations asynchronously for the provided contexts. It
     * waits for all tasks to complete and logs any errors that occur.
     *
     * @param contextLists The map of contexts to execute fuzzy listen operations for.
     * @throws NacosException If an error occurs during the execution of fuzzy listen configuration changes.
     */
    private void doExecuteConfigFuzzyListen(List<ConfigFuzzyWatchContext> contextLists) throws NacosException {
        // Return if the context map is null or empty
        if (CollectionUtils.isEmpty(contextLists)) {
            return;
        }
        
        // List to hold futures for asynchronous tasks
        List<Future<?>> listenFutures = new ArrayList<>();
        
        RpcClient rpcClient = agent.ensureRpcClient(taskId);
        
        // Iterate through the context map and submit tasks for execution
        for (ConfigFuzzyWatchContext context : contextLists) {
            ExecutorService executorService = agent.getExecutor();
            // Submit task for execution
            Future<?> future = executorService.submit(() -> executeFuzzyWatchRequest(context, rpcClient));
            listenFutures.add(future);
        }
        
        // Wait for all tasks to complete
        for (Future<?> future : listenFutures) {
            try {
                future.get();
            } catch (Throwable throwable) {
                // Log async listen error
                LOGGER.error("Async fuzzy listen config change error.", throwable);
            }
        }
    }
    
    void executeFuzzyWatchRequest(ConfigFuzzyWatchContext context, RpcClient rpcClient) {
        ConfigFuzzyWatchRequest configFuzzyWatchRequest = buildFuzzyListenConfigRequest(context);
        try {
            // Execute the fuzzy listen operation
            ConfigFuzzyWatchResponse listenResponse = (ConfigFuzzyWatchResponse) agent.requestProxy(rpcClient,
                    configFuzzyWatchRequest);
            if (listenResponse != null && listenResponse.isSuccess()) {
                
                if (context.isDiscard()) {
                    removeFuzzyListenContext(context.getGroupKeyPattern());
                } else {
                    context.setConsistentWithServer(true);
                }
                
                context.clearOverLimitTs();
            } else if (listenResponse != null) {
                if (handleOverLoadEvent(context.getGroupKeyPattern(), listenResponse.getErrorCode())) {
                    return;
                }
                LOGGER.error("Execute  fuzzy watch config change error,code={},msg={}", listenResponse.getErrorCode(),
                        listenResponse.getMessage());
            }
            
        } catch (NacosException e) {
            if (handleOverLoadEvent(context.getGroupKeyPattern(), e.getErrCode())) {
                return;
            }
            // Log error and retry after a short delay
            LOGGER.error("Execute  fuzzy watch config change error.", e);
            // Retry notification
            notifyFuzzyWatchSync();
            
        }
    }
    
    private boolean handleOverLoadEvent(String pattern, int errorCode) {
        if (FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode() == errorCode
                || FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode() == errorCode) {
            LOGGER.warn(" fuzzy watch pattern over limit,pattern ->{} ,fuzzy watch will be suppressed", pattern);
            NotifyCenter.publishEvent(ConfigFuzzyWatchLoadEvent.buildEvent(errorCode, pattern, this.clientUuid));
            return true;
        }
        return false;
    }
    
    /**
     * Builds a request for fuzzy listen configuration.
     *
     * @param context The list of fuzzy listen contexts.
     * @return A {@code ConfigBatchFuzzyListenRequest} object representing the request.
     */
    private ConfigFuzzyWatchRequest buildFuzzyListenConfigRequest(ConfigFuzzyWatchContext context) {
        ConfigFuzzyWatchRequest request = new ConfigFuzzyWatchRequest();
        request.setGroupKeyPattern(context.getGroupKeyPattern());
        request.setInitializing(context.isInitializing());
        request.setWatchType((context.isDiscard() && CollectionUtils.isEmpty(context.getConfigFuzzyWatcherWrappers()))
                ? WATCH_TYPE_CANCEL_WATCH : WATCH_TYPE_WATCH);
        request.setReceivedGroupKeys(context.getReceivedGroupKeys());
        return request;
    }
    
    /**
     * Adds a fuzzy listen context if it doesn't already exist for the specified data ID pattern and group. If the
     * context already exists, returns the existing context.
     *
     * @param dataIdPattern The pattern of the data ID.
     * @param groupPattern  The group of the configuration.
     * @return The fuzzy listen context for the specified data ID pattern and group.
     */
    private ConfigFuzzyWatchContext initFuzzyWatchContextIfAbsent(String dataIdPattern, String groupPattern) {
        ConfigFuzzyWatchContext context = getFuzzyListenContext(dataIdPattern, groupPattern);
        if (context != null) {
            return context;
        }
        synchronized (fuzzyListenContextMap) {
            ConfigFuzzyWatchContext contextFromMap = getFuzzyListenContext(dataIdPattern, groupPattern);
            if (contextFromMap != null) {
                context = contextFromMap;
            } else {
                String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern(dataIdPattern, groupPattern,
                        agent.getTenant());
                context = new ConfigFuzzyWatchContext(agent.getName(), groupKeyPattern);
                context.setConsistentWithServer(false);
                Map<String, ConfigFuzzyWatchContext> copy = new HashMap<>(fuzzyListenContextMap.get());
                copy.put(groupKeyPattern, context);
                LOGGER.info("[{}][fuzzy-watch] init fuzzy watch context , groupKeyPattern={} ,notify fuzzy watch sync ",
                        agent.getName(), groupKeyPattern);
                fuzzyListenContextMap.set(copy);
                notifyFuzzyWatchSync();
            }
        }
        
        return context;
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ConfigFuzzyWatchNotifyEvent.class);
        result.add(ConfigFuzzyWatchLoadEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        
        if (event instanceof ConfigFuzzyWatchNotifyEvent) {
            
            ConfigFuzzyWatchNotifyEvent configFuzzyWatchNotifyEvent = (ConfigFuzzyWatchNotifyEvent) event;
            if (!configFuzzyWatchNotifyEvent.getClientUuid().equals(clientUuid)) {
                return;
            }
            
            ConfigFuzzyWatchContext context = fuzzyListenContextMap.get()
                    .get(configFuzzyWatchNotifyEvent.getGroupKeyPattern());
            if (context == null) {
                return;
            }
            
            context.notifyWatcher(configFuzzyWatchNotifyEvent.getGroupKey(),
                    configFuzzyWatchNotifyEvent.getChangedType(), configFuzzyWatchNotifyEvent.getSyncType(),
                    configFuzzyWatchNotifyEvent.getWatcherUuid());
        }
        
        if (event instanceof ConfigFuzzyWatchLoadEvent) {
            ConfigFuzzyWatchLoadEvent loadEvent = (ConfigFuzzyWatchLoadEvent) event;
            
            //instance check
            if (!loadEvent.getClientUuid().equals(clientUuid)) {
                return;
            }
            
            ConfigFuzzyWatchContext context = fuzzyListenContextMap.get().get(loadEvent.getGroupKeyPattern());
            if (context == null) {
                return;
            }
            
            context.notifyLoaderWatcher(loadEvent.getCode());
        }
        
    }
}
