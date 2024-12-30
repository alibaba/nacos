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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.listener.ConfigFuzzyWatchChangeEvent;
import com.alibaba.nacos.api.config.listener.ConfigFuzzyWatcher;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.common.utils.GroupKey;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


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
public class ConfigFuzzyWatchContext {
    
    /**
     * Logger for FuzzyListenContext.
     */
    private static final Logger LOGGER = LogUtils.logger(ConfigFuzzyWatchContext.class);
    
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
     * Set of data IDs associated with the context.
     */
    private Set<String> receivedGroupKeys = new ConcurrentHashSet<>();
    
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
    private Set<ConfigFuzzyWatcher> configFuzzyWatchers = new HashSet<>();
    
    /**
     * Constructor with environment name, data ID pattern, and group.
     *
     * @param envName         Environment name
     * @param groupKeyPattern groupKeyPattern
     */
    public ConfigFuzzyWatchContext(String envName, String groupKeyPattern) {
        this.envName = envName;
        this.groupKeyPattern = groupKeyPattern;
    }
    
    /**
     * Calculate the listeners to notify based on the given UUID.
     *
     * @param uuid UUID to filter listeners
     * @return Set of listeners to notify
     */
    public Set<ConfigFuzzyWatcher> calculateListenersToNotify(String uuid) {
        Set<ConfigFuzzyWatcher> listenersToNotify = new HashSet<>();
        if (StringUtils.isEmpty(uuid)) {
            listenersToNotify = configFuzzyWatchers;
        } else {
            for (ConfigFuzzyWatcher listener : configFuzzyWatchers) {
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
     * @param dataId Data ID
     * @param type   Type of the event
     * @param uuid   UUID to filter listeners
     */
    public void notifyListener(final String dataId, final String group, String tenant, final String type,
            final String uuid) {
        Set<ConfigFuzzyWatcher> listenersToNotify = calculateListenersToNotify(uuid);
        doNotifyWatchers(dataId, group, tenant, type, listenersToNotify);
    }
    
    /**
     * Perform the notification for the specified data ID, type, and listeners.
     *
     * @param dataId            Data ID
     * @param type              Type of the event
     * @param listenersToNotify Set of listeners to notify
     */
    private void doNotifyWatchers(final String dataId, final String group, String tenant, final String type,
            Set<ConfigFuzzyWatcher> listenersToNotify) {
        for (ConfigFuzzyWatcher watcher : listenersToNotify) {
            doNotifyWatcher(dataId,group,tenant,type,watcher);
        }
    }
    
    private void doNotifyWatcher(final String dataId, final String group, String tenant, final String type,
            ConfigFuzzyWatcher configFuzzyWatcher){
        AbstractFuzzyNotifyTask job = new AbstractFuzzyNotifyTask() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                ConfigFuzzyWatchChangeEvent event = ConfigFuzzyWatchChangeEvent.build(tenant,group, dataId, type);
                if (configFuzzyWatcher != null) {
                    configFuzzyWatcher.onEvent(event);
                }
                LOGGER.info(
                        "[{}] [notify-watcher-ok] dataId={}, group={}, tenant={}, watcher={}, job run cost={} millis.",
                        envName, dataId, group, tenant, configFuzzyWatcher, (System.currentTimeMillis() - start));
                if (type.equals(Constants.ConfigChangedType.DELETE_CONFIG)) {
                    configFuzzyWatcher.getSyncGroupKeys().remove(GroupKey.getKey(dataId, group, tenant));
                } else if (type.equals("FUZZY_WATCH_INIT_NOTIFY") || type.equals(
                        Constants.ConfigChangedType.ADD_CONFIG)) {
                    configFuzzyWatcher.getSyncGroupKeys().add(GroupKey.getKey(dataId, group, tenant));
                
                }
            }
        };
    
        try {
            if (null != configFuzzyWatcher.getExecutor()) {
                LOGGER.info(
                        "[{}] [notify-watcher] task submitted to user executor, dataId={}, group={}, tenant={}, listener={}.",
                        envName, dataId, group, tenant, configFuzzyWatcher);
                job.async = true;
                configFuzzyWatcher.getExecutor().execute(job);
            } else {
                LOGGER.info(
                        "[{}] [notify-watcher] task execute in nacos thread, dataId={}, group={}, tenant={}, listener={}.",
                        envName, dataId, group, tenant, configFuzzyWatcher);
                job.run();
            }
        } catch (Throwable t) {
            LOGGER.error("[{}] [notify-watcher-error] dataId={}, group={}, tenant={}, listener={}, throwable={}.",
                    envName, dataId, group, tenant, configFuzzyWatcher, t.getCause());
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
    public void removeWatcher(ConfigFuzzyWatcher watcher) {
        configFuzzyWatchers.remove(watcher);
        
        LOGGER.info("[{}] [remove-watcher-ok] groupKeyPattern={}, watcher={},uuid={} ", getEnvName(),
                this.groupKeyPattern, watcher, watcher.getUuid());
        
    }
    
    /**
     * Add a watcher to the context.
     *
     * @param watcher watcher to be added
     */
    public void addWatcher(ConfigFuzzyWatcher watcher) {
        configFuzzyWatchers.add(watcher);
        LOGGER.info("[{}] [add-watcher-ok] groupKeyPattern={}, watcher={},uuid={} ", getEnvName(), this.groupKeyPattern,
                watcher, watcher.getUuid());
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
    public Set<String> getReceivedGroupKeys() {
        return Collections.unmodifiableSet(receivedGroupKeys);
    }
    
    
    /**
     * Get the set of listeners associated with the context.
     *
     * @return Set of listeners
     */
    public Set<ConfigFuzzyWatcher> getConfigFuzzyWatchers() {
        return configFuzzyWatchers;
    }
    

    /**
     * Abstract task for fuzzy notification.
     */
    abstract static class AbstractFuzzyNotifyTask implements Runnable {
        
        /**
         * Flag indicating whether the task is asynchronous.
         */
        boolean async = false;
        
        /**
         * Check if the task is asynchronous.
         *
         * @return True if the task is asynchronous, otherwise false
         */
        public boolean isAsync() {
            return async;
        }
    }
    
    void syncFuzzyWatchers(){
        for(ConfigFuzzyWatcher configFuzzyWatcher:configFuzzyWatchers){
            Set<String> receivedGroupKeysContext = receivedGroupKeys;
            Set<String> syncGroupKeys = configFuzzyWatcher.getSyncGroupKeys();
            List<FuzzyGroupKeyPattern.GroupKeyState> groupKeyStates = FuzzyGroupKeyPattern.diffGroupKeys(
                    receivedGroupKeysContext, syncGroupKeys);
            for(FuzzyGroupKeyPattern.GroupKeyState groupKeyState:groupKeyStates){
                String[] groupKeyItems = GroupKey.parseKey(groupKeyState.getGroupKey());
                FuzzyWatchNotifyEvent fuzzyWatchNotifyEvent = FuzzyWatchNotifyEvent.buildNotifyPatternAllListenersEvent(
                        groupKeyItems[2], groupKeyItems[1], groupKeyItems[0],
                        this.groupKeyPattern, groupKeyState.isExist()? Constants.ConfigChangedType.ADD_CONFIG:Constants.ConfigChangedType.DELETE_CONFIG);
                fuzzyWatchNotifyEvent.setUuid(configFuzzyWatcher.getUuid());
                NotifyCenter.publishEvent(fuzzyWatchNotifyEvent);
            }
            
        }
    }
}

