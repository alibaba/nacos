/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Async refresher for service info disk cache.
 *
 * @author Zhengcy05
 */
public class ServiceInfoDiskCacheRefresher implements Closeable {
    
    static final long DEFAULT_FLUSH_INTERVAL_MILLISECONDS = 100L;
    
    static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS = 3000L;
    
    private static final String REFRESHER_THREAD_NAME =
        "com.alibaba.nacos.client.naming.disk.cache.refresher";
    
    private final ConcurrentMap<String, ServiceInfoDiskCacheRefreshEvent> pendingEvents;
    
    private final ScheduledThreadPoolExecutor refreshExecutor;
    
    private final DiskCacheWriter diskCacheWriter;
    
    private final long shutdownTimeoutMilliseconds;
    
    /**
     * Create a disk cache refresher with default flush and shutdown settings.
     */
    public ServiceInfoDiskCacheRefresher() {
        this(DEFAULT_FLUSH_INTERVAL_MILLISECONDS, DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS,
            DiskCache::writeWithResult);
    }
    
    /**
     * Create a disk cache refresher for tests or custom runtime settings.
     * This constructor keeps the production constructor simple while allowing deterministic tests.
     *
     * @param flushIntervalMilliseconds flush interval in milliseconds
     * @param shutdownTimeoutMilliseconds shutdown wait timeout in milliseconds
     * @param diskCacheWriter writer used to persist disk cache
     */
    ServiceInfoDiskCacheRefresher(long flushIntervalMilliseconds, long shutdownTimeoutMilliseconds,
        DiskCacheWriter diskCacheWriter) {
        this.pendingEvents = new ConcurrentHashMap<>(16);
        this.refreshExecutor = new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory(REFRESHER_THREAD_NAME));
        this.diskCacheWriter = diskCacheWriter;
        this.shutdownTimeoutMilliseconds = shutdownTimeoutMilliseconds;
        this.refreshExecutor.scheduleWithFixedDelay(this::safeFlushPendingEvents,
            flushIntervalMilliseconds, flushIntervalMilliseconds, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Publish a refresh event and keep only the latest snapshot for the service key.
     *
     * @param event refresh event
     */
    public void publishEvent(ServiceInfoDiskCacheRefreshEvent event) {
        pendingEvents.put(event.getServiceKey(), event);
    }
    
    /**
     * Flush pending refresh events immediately.
     */
    void flushNow() {
        safeFlushPendingEvents();
    }
    
    /**
     * Get pending refresh event size.
     *
     * @return pending refresh event size
     */
    int pendingEventSize() {
        return pendingEvents.size();
    }
    
    /**
     * Check whether refresher executor has been shutdown.
     *
     * @return {@code true} if shutdown, otherwise {@code false}
     */
    boolean isShutdown() {
        return refreshExecutor.isShutdown();
    }
    
    private void safeFlushPendingEvents() {
        try {
            flushPendingEvents();
        } catch (Throwable e) {
            NAMING_LOGGER.error("[NA] failed to flush service info disk cache refresh event", e);
        }
    }
    
    private void flushPendingEvents() {
        for (String serviceKey : pendingEvents.keySet()) {
            ServiceInfoDiskCacheRefreshEvent event = pendingEvents.get(serviceKey);
            if (null == event) {
                continue;
            }
            boolean writeResult =
                diskCacheWriter.write(event.getServiceInfo(), event.getCacheDir());
            if (writeResult) {
                pendingEvents.remove(serviceKey, event);
            }
        }
    }
    
    /**
     * Shutdown refresher after flushing pending events.
     *
     * @throws NacosException if interrupted during shutdown
     */
    @Override
    public void shutdown() throws NacosException {
        flushPendingEvents();
        refreshExecutor.shutdown();
        try {
            if (!refreshExecutor.awaitTermination(shutdownTimeoutMilliseconds,
                TimeUnit.MILLISECONDS)) {
                NAMING_LOGGER.warn("[NA] timeout while waiting service info disk cache refresher "
                    + "to shutdown, pending event size: {}", pendingEvents.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Interrupted while shutting down service info disk cache refresher", e);
        }
        flushPendingEvents();
    }
    
    @FunctionalInterface
    interface DiskCacheWriter {
        
        /**
         * Write service info to disk cache.
         *
         * @param serviceInfo service info
         * @param cacheDir cache dir
         * @return {@code true} if write success, otherwise {@code false}
         */
        boolean write(ServiceInfo serviceInfo, String cacheDir);
    }
}
