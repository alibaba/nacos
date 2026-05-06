/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.redo.service;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.redo.data.RedoData;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Abstract redo service.
 *
 * @author xiweng.yy
 */
public abstract class AbstractRedoService implements ConnectionEventListener, Closeable {
    
    private static final String REDO_THREAD_NAME_PATTERN = "com.alibaba.nacos.client.%s.redo";
    
    private final Logger logger;
    
    private final ScheduledExecutorService redoExecutor;
    
    private final Map<Class<?>, Map<String, RedoData<?>>> redoDataMap;
    
    private int redoThreadCount;
    
    private long redoDelayTime;
    
    private volatile boolean connected = false;
    
    protected AbstractRedoService(Logger logger, NacosClientProperties properties, String module) {
        this.logger = logger;
        setProperties(properties);
        this.redoExecutor = new ScheduledThreadPoolExecutor(redoThreadCount,
                new NameThreadFactory(String.format(REDO_THREAD_NAME_PATTERN, module)));
        this.redoDataMap = new ConcurrentHashMap<>(2);
    }
    
    private void setProperties(NacosClientProperties properties) {
        redoDelayTime = properties.getLong(PropertyKeyConst.REDO_DELAY_TIME, Constants.DEFAULT_REDO_DELAY_TIME);
        redoThreadCount = properties.getInteger(PropertyKeyConst.REDO_DELAY_THREAD_COUNT,
                Constants.DEFAULT_REDO_THREAD_COUNT);
    }
    
    protected void startRedoTask() {
        this.redoExecutor.scheduleWithFixedDelay(buildRedoTask(), redoDelayTime, redoDelayTime,
                TimeUnit.MILLISECONDS);
    }
    
    /**
     * Build redo task to do redo work.
     *
     * @return redo task
     */
    protected abstract AbstractRedoTask buildRedoTask();
    
    @Override
    public void onConnected(Connection connection) {
        connected = true;
        logger.info("Grpc connection connect");
    }
    
    @Override
    public void onDisConnect(Connection connection) {
        connected = false;
        logger.warn("Grpc connection disconnect, mark to redo");
        for (Class<?> each : redoDataMap.keySet()) {
            Map<String, RedoData<?>> actualRedoData = this.redoDataMap.get(each);
            synchronized (actualRedoData) {
                actualRedoData.values().forEach(redoData -> redoData.setRegistered(false));
            }
        }
        logger.warn("mark to redo completed");
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutdown grpc redo service executor {}", redoExecutor);
        redoDataMap.clear();
        redoExecutor.shutdownNow();
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Cache the redo data by class and redo data key.
     *
     * @param key       key of redo data
     * @param redoData  the redo data
     * @param clazz     clazz of stored in {@link RedoData}.
     */
    public <T> void cachedRedoData(String key, RedoData<T> redoData, Class<T> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            actualRedoData.put(key, redoData);
        }
    }
    
    /**
     * Remove data for redo.
     *
     * @param key       key of redo data
     * @param clazz     clazz of stored in {@link RedoData}.
     */
    public <T> void removeRedoData(String key, Class<T> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            RedoData<?> redoData = actualRedoData.get(key);
            if (null != redoData && !redoData.isExpectedRegistered()) {
                actualRedoData.remove(key);
            }
        }
    }
    
    /**
     * Data register successfully, mark registered status as {@code true}.
     *
     * @param key   key of redo data
     * @param clazz clazz of stored in {@link RedoData}.
     */
    public <T> void dataRegistered(String key, Class<T> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            RedoData<?> redoData = actualRedoData.get(key);
            if (null != redoData) {
                redoData.registered();
            }
        }
    }
    
    /**
     * Data deregister, mark unregistering status as {@code true}.
     *
     * @param key   key of redo data
     * @param clazz clazz of stored in {@link RedoData}.
     */
    public <T> void dataDeregister(String key, Class<T> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            RedoData<?> redoData = actualRedoData.get(key);
            if (null != redoData) {
                redoData.setUnregistering(true);
                redoData.setExpectedRegistered(false);
            }
        }
    }
    
    /**
     * Data deregister finished, mark unregistering status as {@code true}.
     *
     * @param key   key of redo data
     * @param clazz clazz of stored in {@link RedoData}.
     */
    public <T> void dataDeregistered(String key, Class<T> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            RedoData<?> redoData = actualRedoData.get(key);
            if (null != redoData) {
                redoData.unregistered();
            }
        }
    }
    
    /**
     * Judge data has registered to server.
     *
     * @param key   key of redo data
     * @param clazz clazz of stored in {@link RedoData}.
     * @return {@code true} if registered, otherwise {@code false}
     */
    public boolean isDataRegistered(String key, Class<?> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            RedoData<?> redoData = actualRedoData.get(key);
            return null != redoData && redoData.isRegistered();
        }
    }
    
    /**
     * Find all redo data which need to do redo.
     *
     * @return set of {@link RedoData} need to do redo.
     */
    public <T> Set<RedoData<T>> findRedoData(Class<T> clazz) {
        Set<RedoData<T>> result = new HashSet<>();
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            for (RedoData<?> each : actualRedoData.values()) {
                if (each.isNeedRedo()) {
                    result.add((RedoData<T>) each);
                }
            }
        }
        return result;
    }
    
    /**
     * get Cache redo data.
     *
     * @param key   key of redo data
     * @param clazz clazz of stored in {@link RedoData}.
     * @return cache redo data
     */
    public <T> RedoData<T> getRedoData(String key, Class<?> clazz) {
        Map<String, RedoData<?>> actualRedoData = this.redoDataMap.computeIfAbsent(clazz,
                k -> new ConcurrentHashMap<>(2));
        synchronized (actualRedoData) {
            return (RedoData<T>) actualRedoData.get(key);
        }
    }
}
