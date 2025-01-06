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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchChangeEvent;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.common.utils.GroupKey;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;


/**
 * fuzzy wather context for a single group key pattern.
 *
 * <p>This class manages the context information for fuzzy listening, including environment name, task ID, data ID
 * pattern, group, tenant, listener set, and other related information.
 * </p>
 *
 * @author stone-98
 * @date 2024/3/4
 */
public class NamingFuzzyWatchContext {
    
    /**
     * Logger for FuzzyListenContext.
     */
    private static final Logger LOGGER = LogUtils.logger(NamingFuzzyWatchContext.class);
    
    /**
     * Environment name.
     */
    private String envName;
    
    /**
     * Task ID.
     */
    private int taskId;
    
    private String groupKeyPattern;
    
    /**
     * Set of service keys associated with the context.
     */
    private Set<String> receivedServiceKeys = new ConcurrentHashSet<>();
    
    /**
     * Flag indicating whether the context is consistent with the server.
     */
    private final AtomicBoolean isConsistentWithServer = new AtomicBoolean();
    
    /**
     * Condition object for waiting initialization completion.
     */
    final AtomicBoolean initializationCompleted = new AtomicBoolean(false);
    
    /**
     * Flag indicating whether the context is initializing.
     */
    private boolean isInitializing = false;
    
    /**
     * Flag indicating whether the context is discarded.
     */
    private volatile boolean isDiscard = false;
    
    /**
     * Set of listeners associated with the context.
     */
    private Set<FuzzyWatchEventWatcher> namingFuzzyWatchers = new HashSet<>();
    
    /**
     * Constructor with environment name, data ID pattern, and group.
     *
     * @param envName         Environment name
     * @param groupKeyPattern groupKeyPattern
     */
    public NamingFuzzyWatchContext(String envName, String groupKeyPattern) {
        this.envName = envName;
        this.groupKeyPattern = groupKeyPattern;
    }
    
    /**
     * Calculate the listeners to notify based on the given UUID.
     *
     * @param uuid UUID to filter listeners
     * @return Set of listeners to notify
     */
    public Set<FuzzyWatchEventWatcher> calculateListenersToNotify(String uuid) {
        Set<FuzzyWatchEventWatcher> listenersToNotify = new HashSet<>();
        if (StringUtils.isEmpty(uuid)) {
            listenersToNotify = namingFuzzyWatchers;
        } else {
            for (FuzzyWatchEventWatcher listener : namingFuzzyWatchers) {
                if (uuid.equals(listener.getUuid())) {
                    listenersToNotify.add(listener);
                }
            }
        }
        return listenersToNotify;
    }
    
    /**
     * Notify the listener with the specified data ID, type, and UUID.
     *
     * @param serviceName Data ID
     * @param type   Type of the event
     * @param uuid   UUID to filter listeners
     */
    public void notifyListener(final String serviceName, final String groupName, String namespace, final String type,
            final String uuid) {
        Set<FuzzyWatchEventWatcher> listenersToNotify = calculateListenersToNotify(uuid);
        doNotifyWatchers(serviceName, groupName, namespace, type, listenersToNotify);
    }
    
    /**
     * Perform the notification for the specified data ID, type, and listeners.
     *
     * @param serviceName            Data ID
     * @param changedType              Type of the event
     * @param listenersToNotify Set of listeners to notify
     */
    private void doNotifyWatchers(final String serviceName, final String groupName, String namespace, final String changedType,
            Set<FuzzyWatchEventWatcher> listenersToNotify) {
        for (FuzzyWatchEventWatcher watcher : listenersToNotify) {
            doNotifyWatcher(serviceName,groupName,namespace,changedType,watcher);
        }
    }
    
    private void doNotifyWatcher(final String serviceName, final String groupName, String namespace, final String changedType,
            FuzzyWatchEventWatcher namingFuzzyWatcher){
        Runnable job = () -> {
            long start = System.currentTimeMillis();
            FuzzyWatchChangeEvent event = new FuzzyWatchChangeEvent(namespace,groupName, serviceName, changedType);
            if (namingFuzzyWatcher != null) {
                namingFuzzyWatcher.onEvent(event);
            }
            LOGGER.info(
                    "[{}] [notify-watcher-ok] serviceName={}, groupName={}, namespace={}, watcher={}, job run cost={} millis.",
                    envName, serviceName, groupName, namespace, namingFuzzyWatcher, (System.currentTimeMillis() - start));
            if (changedType.equals(Constants.ConfigChangedType.DELETE_CONFIG)) {
                namingFuzzyWatcher.getSyncGroupKeys().remove(GroupKey.getKey(serviceName, groupName, namespace));
            } else if (changedType.equals(FUZZY_WATCH_INIT_NOTIFY) || changedType.equals(
                    Constants.ConfigChangedType.ADD_CONFIG)) {
                namingFuzzyWatcher.getSyncGroupKeys().add(GroupKey.getKey(serviceName, groupName, namespace));
            }
        };
    
        try {
            if (null != namingFuzzyWatcher.getExecutor()) {
                LOGGER.info(
                        "[{}] [notify-watcher] task submitted to user executor, serviceName={}, groupName={}, namespace={}, listener={}.",
                        envName, serviceName, groupName, namespace, namingFuzzyWatcher);
                namingFuzzyWatcher.getExecutor().execute(job);
            } else {
                LOGGER.info(
                        "[{}] [notify-watcher] task execute in nacos thread, serviceName={}, groupName={}, namespace={}, listener={}.",
                        envName, serviceName, groupName, namespace, namingFuzzyWatcher);
                job.run();
            }
        } catch (Throwable t) {
            LOGGER.error("[{}] [notify-watcher-error] serviceName={}, groupName={}, namespace={}, listener={}, throwable={}.",
                    envName, serviceName, groupName, namespace, namingFuzzyWatcher, t.getCause());
        }
    }
    /**
     * Mark initialization as complete and notify waiting threads.
     */
    public void markInitializationComplete() {
        initializationCompleted.set(true);
    }
    
    /**
     * Remove a watcher from the context.
     *
     * @param watcher watcher to be removed
     */
    public void removeWatcher(FuzzyWatchEventWatcher watcher) {
        if(namingFuzzyWatchers.remove(watcher)){
            LOGGER.info("[{}] [remove-watcher-ok] groupKeyPattern={}, watcher={},uuid={} ", getEnvName(),
                    this.groupKeyPattern, watcher, watcher.getUuid());
    
        }
    }
    
    
    /**
     * Add a watcher to the context.
     *
     * @param watcher watcher to be added
     */
    public void registerWatcher(FuzzyWatchEventWatcher watcher) {
        if(namingFuzzyWatchers.add(watcher)){
            LOGGER.info("[{}] [add-watcher-ok] groupKeyPattern={}, watcher={},uuid={} ", getEnvName(), this.groupKeyPattern,
                    watcher, watcher.getUuid());
        }
        
    }
    
    /**
     * Get the environment name.
     *
     * @return Environment name
     */
    public String getEnvName() {
        return envName;
    }
    
    /**
     * Set the environment name.
     *
     * @param envName Environment name to be set
     */
    public void setEnvName(String envName) {
        this.envName = envName;
    }
    
    /**
     * Get the task ID.
     *
     * @return Task ID
     */
    public int getTaskId() {
        return taskId;
    }
    
    /**
     * Set the task ID.
     *
     * @param taskId Task ID to be set
     */
    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }
    
    public String getGroupKeyPattern() {
        return groupKeyPattern;
    }
    
    /**
     * Get the flag indicating whether the context is consistent with the server.
     *
     * @return AtomicBoolean indicating whether the context is consistent with the server
     */
    public boolean isConsistentWithServer() {
        return isConsistentWithServer.get();
    }
    
    public void setConsistentWithServer(boolean isConsistentWithServer) {
        this.isConsistentWithServer.set(isConsistentWithServer);
    }
    
    /**
     * Check if the context is discarded.
     *
     * @return True if the context is discarded, otherwise false
     */
    public boolean isDiscard() {
        return isDiscard;
    }
    
    /**
     * Set the flag indicating whether the context is discarded.
     *
     * @param discard True to mark the context as discarded, otherwise false
     */
    public void setDiscard(boolean discard) {
        isDiscard = discard;
    }
    
    /**
     * Check if the context is initializing.
     *
     * @return True if the context is initializing, otherwise false
     */
    public boolean isInitializing() {
        return isInitializing;
    }
    
    /**
     * Set the flag indicating whether the context is initializing.
     *
     * @param initializing True to mark the context as initializing, otherwise false
     */
    public void setInitializing(boolean initializing) {
        isInitializing = initializing;
    }
    
    /**
     * Get the set of data IDs associated with the context.
     *
     * @return Set of data IDs
     */
    public Set<String> getReceivedServiceKeys() {
        return Collections.unmodifiableSet(receivedServiceKeys);
    }
    
    
    public boolean addReceivedServiceKey(String serviceKey){
        return receivedServiceKeys.add(serviceKey);
    }
    
    
    public boolean removeReceivedServiceKey(String serviceKey){
        return receivedServiceKeys.remove(serviceKey);
    }
    
    /**
     * Get the set of listeners associated with the context.
     *
     * @return Set of listeners
     */
    public Set<FuzzyWatchEventWatcher> getNamingFuzzyWatchers() {
        return namingFuzzyWatchers;
    }
    
    void syncFuzzyWatchers(){
        for(FuzzyWatchEventWatcher namingFuzzyWatcher: namingFuzzyWatchers){
            Set<String> receivedGroupKeysContext = receivedServiceKeys;
            Set<String> syncGroupKeys = namingFuzzyWatcher.getSyncGroupKeys();
            List<FuzzyGroupKeyPattern.GroupKeyState> groupKeyStates = FuzzyGroupKeyPattern.diffGroupKeys(
                    receivedGroupKeysContext, syncGroupKeys);
            for(FuzzyGroupKeyPattern.GroupKeyState groupKeyState:groupKeyStates){
                String[] serviceKeyItems = NamingUtils.parseServiceKey(groupKeyState.getGroupKey());
                String changedType=groupKeyState.isExist()? Constants.ConfigChangedType.ADD_CONFIG:Constants.ConfigChangedType.DELETE_CONFIG;
                doNotifyWatcher(serviceKeyItems[2], serviceKeyItems[1], serviceKeyItems[0],changedType,namingFuzzyWatcher);
            }
            
        }
    }
    
    void notifyFuzzyWatchers(String serviceKey,String changedType){
        for(FuzzyWatchEventWatcher namingFuzzyWatcher: namingFuzzyWatchers){
                String[] serviceKeyItems = NamingUtils.parseServiceKey(serviceKey);
                doNotifyWatcher(serviceKeyItems[2], serviceKeyItems[1], serviceKeyItems[0],changedType,namingFuzzyWatcher);
        }
    }
    
    public CompletableFuture<ListView<String>> createNewFuture(){
        CompletableFuture<ListView<String>> completableFuture=new CompletableFuture<ListView<String>>(){
            @Override
            public boolean isDone() {
                return NamingFuzzyWatchContext.this.initializationCompleted.get();
            }
            
            @Override
            public ListView<String> get() throws InterruptedException, ExecutionException {
    
                ListView<String> result = new ListView<>();
                result.setData(Arrays.asList(NamingFuzzyWatchContext.this.receivedServiceKeys.toArray(new String[0])));
                result.setCount(result.getData().size());
                return result;
            }
        };
        return completableFuture;
    }
}

