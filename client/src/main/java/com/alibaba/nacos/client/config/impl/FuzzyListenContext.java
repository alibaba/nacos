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

import com.alibaba.nacos.api.config.listener.AbstractFuzzyListenListener;
import com.alibaba.nacos.api.config.listener.FuzzyListenConfigChangeEvent;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Context for fuzzy listening.
 *
 * <p>This class manages the context information for fuzzy listening, including environment name, task ID, data ID
 * pattern, group, tenant, listener set, and other related information.
 * </p>
 *
 * @author stone-98
 * @date 2024/3/4
 */
public class FuzzyListenContext {
    
    /**
     * Logger for FuzzyListenContext.
     */
    private static final Logger LOGGER = LogUtils.logger(FuzzyListenContext.class);
    
    /**
     * Environment name.
     */
    private String envName;
    
    /**
     * Task ID.
     */
    private int taskId;
    
    /**
     * Data ID pattern.
     */
    private String dataIdPattern;
    
    /**
     * Group name.
     */
    private String group;
    
    /**
     * Tenant name.
     */
    private String tenant;
    
    /**
     * Flag indicating whether the context is consistent with the server.
     */
    private final AtomicBoolean isConsistentWithServer = new AtomicBoolean();
    
    /**
     * Lock object for synchronization of initialization.
     */
    private final Lock initializationLock = new ReentrantLock();
    
    /**
     * Condition object for waiting initialization completion.
     */
    private final Condition initializationCompleted = initializationLock.newCondition();
    
    /**
     * Flag indicating whether the context is initializing.
     */
    private boolean isInitializing = false;
    
    /**
     * Flag indicating whether the context is discarded.
     */
    private volatile boolean isDiscard = false;
    
    /**
     * Set of data IDs associated with the context.
     */
    private Set<String> dataIds = new ConcurrentHashSet<>();
    
    /**
     * Set of listeners associated with the context.
     */
    private Set<AbstractFuzzyListenListener> listeners = new HashSet<>();
    
    /**
     * Constructor with environment name, data ID pattern, and group.
     *
     * @param envName       Environment name
     * @param dataIdPattern Data ID pattern
     * @param group         Group name
     */
    public FuzzyListenContext(String envName, String dataIdPattern, String group) {
        this.envName = envName;
        this.dataIdPattern = dataIdPattern;
        this.group = group;
    }
    
    /**
     * Calculate the listeners to notify based on the given UUID.
     *
     * @param uuid UUID to filter listeners
     * @return Set of listeners to notify
     */
    public Set<AbstractFuzzyListenListener> calculateListenersToNotify(String uuid) {
        Set<AbstractFuzzyListenListener> listenersToNotify = new HashSet<>();
        if (StringUtils.isEmpty(uuid)) {
            listenersToNotify = listeners;
        } else {
            for (AbstractFuzzyListenListener listener : listeners) {
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
    public void notifyListener(final String dataId, final String type, final String uuid) {
        Set<AbstractFuzzyListenListener> listenersToNotify = calculateListenersToNotify(uuid);
        doNotifyListener(dataId, type, listenersToNotify);
    }
    
    /**
     * Perform the notification for the specified data ID, type, and listeners.
     *
     * @param dataId            Data ID
     * @param type              Type of the event
     * @param listenersToNotify Set of listeners to notify
     */
    private void doNotifyListener(final String dataId, final String type,
            Set<AbstractFuzzyListenListener> listenersToNotify) {
        for (AbstractFuzzyListenListener listener : listenersToNotify) {
            AbstractFuzzyNotifyTask job = new AbstractFuzzyNotifyTask() {
                @Override
                public void run() {
                    long start = System.currentTimeMillis();
                    FuzzyListenConfigChangeEvent event = FuzzyListenConfigChangeEvent.build(group, dataId, type);
                    if (listener != null) {
                        listener.onEvent(event);
                    }
                    LOGGER.info("[{}] [notify-ok] dataId={}, group={}, tenant={}, listener={}, job run cost={} millis.",
                            envName, dataId, group, tenant, listener, (System.currentTimeMillis() - start));
                }
            };
            
            try {
                if (null != listener.getExecutor()) {
                    LOGGER.info(
                            "[{}] [notify-listener] task submitted to user executor, dataId={}, group={}, tenant={}, listener={}.",
                            envName, dataId, group, tenant, listener);
                    job.async = true;
                    listener.getExecutor().execute(job);
                } else {
                    LOGGER.info(
                            "[{}] [notify-listener] task execute in nacos thread, dataId={}, group={}, tenant={}, listener={}.",
                            envName, dataId, group, tenant, listener);
                    job.run();
                }
            } catch (Throwable t) {
                LOGGER.error("[{}] [notify-listener-error] dataId={}, group={}, tenant={}, listener={}, throwable={}.",
                        envName, dataId, group, tenant, listener, t.getCause());
            }
        }
    }
    
    
    /**
     * Wait for initialization to be complete.
     *
     * @return CompletableFuture<Collection < String>> Completes with the collection of data IDs if initialization is
     * @return CompletableFuture<Collection < String>> Completes with the collection of data IDs if initialization is
     * complete, or completes exceptionally if an error occurs
     */
    public CompletableFuture<Collection<String>> waitForInitializationComplete(
            CompletableFuture<Collection<String>> future) {
        initializationLock.lock();
        try {
            while (isInitializing) {
                initializationCompleted.await();
            }
            future.complete(Collections.unmodifiableCollection(dataIds));
        } catch (InterruptedException e) {
            future.completeExceptionally(e);
        } finally {
            initializationLock.unlock();
        }
        return future;
    }
    
    /**
     * Mark initialization as complete and notify waiting threads.
     */
    public void markInitializationComplete() {
        initializationLock.lock();
        try {
            isInitializing = false;
            initializationCompleted.signalAll();
        } finally {
            initializationLock.unlock();
        }
    }
    
    /**
     * Remove a listener from the context.
     *
     * @param listener Listener to be removed
     */
    public void removeListener(AbstractFuzzyListenListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Add a listener to the context.
     *
     * @param listener Listener to be added
     */
    public void addListener(AbstractFuzzyListenListener listener) {
        listeners.add(listener);
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
    
    /**
     * Get the data ID pattern.
     *
     * @return Data ID pattern
     */
    public String getDataIdPattern() {
        return dataIdPattern;
    }
    
    /**
     * Set the data ID pattern.
     *
     * @param dataIdPattern Data ID pattern to be set
     */
    public void setDataIdPattern(String dataIdPattern) {
        this.dataIdPattern = dataIdPattern;
    }
    
    /**
     * Get the group name.
     *
     * @return Group name
     */
    public String getGroup() {
        return group;
    }
    
    /**
     * Set the group name.
     *
     * @param group Group name to be set
     */
    public void setGroup(String group) {
        this.group = group;
    }
    
    /**
     * Get the tenant name.
     *
     * @return Tenant name
     */
    public String getTenant() {
        return tenant;
    }
    
    /**
     * Set the tenant name.
     *
     * @param tenant Tenant name to be set
     */
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    /**
     * Get the flag indicating whether the context is consistent with the server.
     *
     * @return AtomicBoolean indicating whether the context is consistent with the server
     */
    public AtomicBoolean getIsConsistentWithServer() {
        return isConsistentWithServer;
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
    public Set<String> getDataIds() {
        return Collections.unmodifiableSet(dataIds);
    }
    
    /**
     * Set the set of data IDs associated with the context.
     *
     * @param dataIds Set of data IDs to be set
     */
    public void setDataIds(Set<String> dataIds) {
        this.dataIds = dataIds;
    }
    
    /**
     * Get the set of listeners associated with the context.
     *
     * @return Set of listeners
     */
    public Set<AbstractFuzzyListenListener> getListeners() {
        return listeners;
    }
    
    /**
     * Set the set of listeners associated with the context.
     *
     * @param listeners Set of listeners to be set
     */
    public void setListeners(Set<AbstractFuzzyListenListener> listeners) {
        this.listeners = listeners;
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
}

