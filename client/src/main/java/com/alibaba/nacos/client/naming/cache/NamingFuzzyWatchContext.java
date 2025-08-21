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

import com.alibaba.nacos.api.naming.listener.FuzzyWatchChangeEvent;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchLoadWatcher;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_DIFF_SYNC_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.DELETE_SERVICE;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;

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
    
    private String groupKeyPattern;
    
    /**
     * Set of service keys associated with the context.
     */
    private Set<String> receivedServiceKeys = new ConcurrentHashSet<>();
    
    private long syncVersion = 0;
    
    /**
     * Flag indicating whether the context is consistent with the server.
     */
    private final AtomicBoolean isConsistentWithServer = new AtomicBoolean();
    
    /**
     * Condition object for waiting initialization completion.
     */
    final AtomicBoolean initializationCompleted = new AtomicBoolean(false);
    
    /**
     * Flag indicating whether the context is discarded.
     */
    private volatile boolean isDiscard = false;
    
    /**
     * Set of listeners associated with the context.
     */
    private final Set<FuzzyWatchEventWatcherWrapper> fuzzyWatchEventWatcherWrappers = new HashSet<>();
    
    long patternLimitTs = 0;
    
    private static final long SUPPRESSED_PERIOD = 60 * 1000L;
    
    boolean patternLimitSuppressed() {
        return patternLimitTs > 0 && System.currentTimeMillis() - patternLimitTs < SUPPRESSED_PERIOD;
    }
    
    public void clearOverLimitTs() {
        this.patternLimitTs = 0;
    }
    
    public void refreshOverLimitTs() {
        this.patternLimitTs = System.currentTimeMillis();
    }
    
    public void refreshSyncVersion() {
        this.syncVersion = System.currentTimeMillis();
    }
    
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
    
    private void doNotifyWatcher(final String serviceKey, final String changedType, final String syncType,
            FuzzyWatchEventWatcherWrapper fuzzyWatchEventWatcherWrapper) {
        
        if (ADD_SERVICE.equals(changedType) && fuzzyWatchEventWatcherWrapper.getSyncServiceKeys()
                .contains(serviceKey)) {
            return;
        }
        
        if (DELETE_SERVICE.equals(changedType) && !fuzzyWatchEventWatcherWrapper.getSyncServiceKeys()
                .contains(serviceKey)) {
            return;
        }
        
        String[] serviceKeyItems = NamingUtils.parseServiceKey(serviceKey);
        String namespace = serviceKeyItems[0];
        String groupName = serviceKeyItems[1];
        String serviceName = serviceKeyItems[2];
        
        final String resetSyncType = !initializationCompleted.get() ? FUZZY_WATCH_INIT_NOTIFY : syncType;
        
        Runnable job = () -> {
            long start = System.currentTimeMillis();
            FuzzyWatchChangeEvent event = new FuzzyWatchChangeEvent(serviceName, groupName, namespace, changedType,
                    resetSyncType);
            if (fuzzyWatchEventWatcherWrapper != null) {
                fuzzyWatchEventWatcherWrapper.fuzzyWatchEventWatcher.onEvent(event);
            }
            LOGGER.info(
                    "[{}] [notify-watcher-ok] serviceName={}, groupName={}, namespace={}, watcher={},changedType={}, job run cost={} millis.",
                    envName, serviceName, groupName, namespace, fuzzyWatchEventWatcherWrapper.fuzzyWatchEventWatcher,
                    changedType, (System.currentTimeMillis() - start));
            if (changedType.equals(DELETE_SERVICE)) {
                fuzzyWatchEventWatcherWrapper.getSyncServiceKeys()
                        .remove(NamingUtils.getServiceKey(namespace, groupName, serviceName));
            } else if (changedType.equals(ADD_SERVICE)) {
                fuzzyWatchEventWatcherWrapper.getSyncServiceKeys()
                        .add(NamingUtils.getServiceKey(namespace, groupName, serviceName));
            }
        };
        
        try {
            if (null != fuzzyWatchEventWatcherWrapper.fuzzyWatchEventWatcher.getExecutor()) {
                LOGGER.info(
                        "[{}] [notify-watcher] task submitted to user executor, serviceName={}, groupName={}, namespace={}, listener={}.",
                        envName, serviceName, groupName, namespace, fuzzyWatchEventWatcherWrapper);
                fuzzyWatchEventWatcherWrapper.fuzzyWatchEventWatcher.getExecutor().execute(job);
            } else {
                LOGGER.info(
                        "[{}] [notify-watcher] task execute in nacos thread, serviceName={}, groupName={}, namespace={}, listener={}.",
                        envName, serviceName, groupName, namespace, fuzzyWatchEventWatcherWrapper);
                job.run();
            }
        } catch (Throwable t) {
            LOGGER.error(
                    "[{}] [notify-watcher-error] serviceName={}, groupName={}, namespace={}, listener={}, throwable={}.",
                    envName, serviceName, groupName, namespace, fuzzyWatchEventWatcherWrapper, t.getCause());
        }
    }
    
    /**
     * Mark initialization as complete and notify waiting threads.
     */
    public void markInitializationComplete() {
        LOGGER.info("[{}] [fuzzy-watch] pattern init notify finish pattern={},match service count {}", envName,
                groupKeyPattern, receivedServiceKeys.size());
        initializationCompleted.set(true);
        synchronized (this) {
            notifyAll();
        }
    }
    
    /**
     * Remove a watcher from the context.
     *
     * @param watcher watcher to be removed
     */
    public synchronized void removeWatcher(FuzzyWatchEventWatcher watcher) {
        Iterator<FuzzyWatchEventWatcherWrapper> iterator = fuzzyWatchEventWatcherWrappers.iterator();
        while (iterator.hasNext()) {
            FuzzyWatchEventWatcherWrapper next = iterator.next();
            if (next.fuzzyWatchEventWatcher.equals(watcher)) {
                iterator.remove();
                LOGGER.info("[{}] [remove-watcher-ok] groupKeyPattern={}, watcher={},uuid={} ", getEnvName(),
                        this.groupKeyPattern, watcher, next.getUuid());
            }
        }
        if (fuzzyWatchEventWatcherWrappers.isEmpty()) {
            this.setConsistentWithServer(false);
            this.setDiscard(true);
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
        return !initializationCompleted.get();
    }
    
    /**
     * Get the set of data IDs associated with the context.
     *
     * @return Set of data IDs
     */
    public Set<String> getReceivedServiceKeys() {
        return Collections.unmodifiableSet(receivedServiceKeys);
    }
    
    /**
     * add received service key.
     *
     * @param serviceKey service key.
     * @return
     */
    public boolean addReceivedServiceKey(String serviceKey) {
        boolean added = receivedServiceKeys.add(serviceKey);
        if (added) {
            refreshSyncVersion();
        }
        return added;
    }
    
    /**
     * remove received service key.
     *
     * @param serviceKey service key.
     * @return
     */
    public boolean removeReceivedServiceKey(String serviceKey) {
        
        boolean removed = receivedServiceKeys.remove(serviceKey);
        if (removed) {
            refreshSyncVersion();
        }
        return removed;
    }
    
    /**
     * Get the set of listeners associated with the context.
     *
     * @return Set of listeners
     */
    public Set<FuzzyWatchEventWatcherWrapper> getFuzzyWatchEventWatcherWrappers() {
        return fuzzyWatchEventWatcherWrappers;
    }
    
    void syncFuzzyWatchers() {
        for (FuzzyWatchEventWatcherWrapper namingFuzzyWatcher : fuzzyWatchEventWatcherWrappers) {
            
            if (namingFuzzyWatcher.syncVersion == this.syncVersion) {
                continue;
            }
            
            Set<String> receivedServiceKeysContext = new HashSet<>(this.getReceivedServiceKeys());
            Set<String> syncGroupKeys = namingFuzzyWatcher.getSyncServiceKeys();
            List<FuzzyGroupKeyPattern.GroupKeyState> groupKeyStates = FuzzyGroupKeyPattern.diffGroupKeys(
                    receivedServiceKeysContext, syncGroupKeys);
            if (CollectionUtils.isEmpty(groupKeyStates)) {
                namingFuzzyWatcher.syncVersion = this.syncVersion;
            } else {
                for (FuzzyGroupKeyPattern.GroupKeyState groupKeyState : groupKeyStates) {
                    String changedType = groupKeyState.isExist() ? ADD_SERVICE : DELETE_SERVICE;
                    doNotifyWatcher(groupKeyState.getGroupKey(), changedType, FUZZY_WATCH_DIFF_SYNC_NOTIFY,
                            namingFuzzyWatcher);
                }
            }
        }
    }
    
    void notifyFuzzyWatchers(String serviceKey, String changedType, String syncType, String watcherUuid) {
        for (FuzzyWatchEventWatcherWrapper namingFuzzyWatcher : filterWatchers(watcherUuid)) {
            doNotifyWatcher(serviceKey, changedType, syncType, namingFuzzyWatcher);
        }
    }
    
    void notifyOverLimitWatchers(int code) {
        
        if (this.patternLimitSuppressed()) {
            return;
        }
        boolean notify = false;
        
        for (FuzzyWatchEventWatcherWrapper namingFuzzyWatcherWrapper : filterWatchers(null)) {
            if (namingFuzzyWatcherWrapper.fuzzyWatchEventWatcher instanceof FuzzyWatchLoadWatcher) {
                
                if (FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode().equals(code)) {
                    ((FuzzyWatchLoadWatcher) namingFuzzyWatcherWrapper.fuzzyWatchEventWatcher).onServiceReachUpLimit();
                    notify = true;
                }
                if (FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode().equals(code)) {
                    ((FuzzyWatchLoadWatcher) namingFuzzyWatcherWrapper.fuzzyWatchEventWatcher).onPatternOverLimit();
                    notify = true;
                }
            }
        }
        if (notify) {
            this.refreshOverLimitTs();
        }
    }
    
    private Set<FuzzyWatchEventWatcherWrapper> filterWatchers(String uuid) {
        if (StringUtils.isBlank(uuid) || CollectionUtils.isEmpty(getFuzzyWatchEventWatcherWrappers())) {
            return getFuzzyWatchEventWatcherWrappers();
        } else {
            return getFuzzyWatchEventWatcherWrappers().stream().filter(a -> a.getUuid().equals(uuid))
                    .collect(Collectors.toSet());
        }
    }
    
    /**
     * create a new future of this context.
     *
     * @return
     */
    public Future<ListView<String>> createNewFuture() {
        Future<ListView<String>> completableFuture = new Future<ListView<String>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                throw new UnsupportedOperationException("not support to cancel fuzzy watch");
            }
            
            @Override
            public boolean isCancelled() {
                return false;
            }
            
            @Override
            public boolean isDone() {
                return NamingFuzzyWatchContext.this.initializationCompleted.get();
            }
            
            @Override
            public ListView<String> get() throws InterruptedException {
                synchronized (NamingFuzzyWatchContext.this) {
                    while (!NamingFuzzyWatchContext.this.initializationCompleted.get()) {
                        NamingFuzzyWatchContext.this.wait();
                    }
                }
                
                ListView<String> result = new ListView<>();
                result.setData(Arrays.asList(NamingFuzzyWatchContext.this.receivedServiceKeys.toArray(new String[0])));
                result.setCount(result.getData().size());
                return result;
            }
            
            @Override
            public ListView<String> get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
                
                if (!NamingFuzzyWatchContext.this.initializationCompleted.get()) {
                    synchronized (NamingFuzzyWatchContext.this) {
                        NamingFuzzyWatchContext.this.wait(unit.toMillis(timeout));
                    }
                }
                
                if (!NamingFuzzyWatchContext.this.initializationCompleted.get()) {
                    throw new TimeoutException(
                            "fuzzy watch result future timeout for " + unit.toMillis(timeout) + " millis");
                }
                
                ListView<String> result = new ListView<>();
                result.setData(Arrays.asList(NamingFuzzyWatchContext.this.receivedServiceKeys.toArray(new String[0])));
                result.setCount(result.getData().size());
                return result;
            }
        };
        return completableFuture;
    }
}
