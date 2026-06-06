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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watchdog for automatically renewing distributed lock leases.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class NacosLockWatchdog {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosLockWatchdog.class);
    
    private static final long DEFAULT_RENEW_INTERVAL_MS = 10000L;
    
    private final ScheduledExecutorService scheduler;
    
    private final Map<String, ScheduledFuture<?>> renewTasks = new ConcurrentHashMap<>();
    
    private final Map<String, LockInstance> lockInstances = new ConcurrentHashMap<>();
    
    private final long renewIntervalMs;
    
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    public NacosLockWatchdog() {
        this(DEFAULT_RENEW_INTERVAL_MS);
    }
    
    public NacosLockWatchdog(long renewIntervalMs) {
        this.renewIntervalMs = renewIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "nacos-lock-watchdog");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Register a lock for automatic lease renewal.
     *
     * @param key lock key
     * @param client gRPC client to send renew requests
     * @param instance lock instance with owner info
     */
    public void register(String key, LockGrpcClient client, LockInstance instance) {
        if (shutdown.get() || renewIntervalMs <= 0) {
            LOGGER.warn("Watchdog cannot register lock: {}, shutdown={}, renewIntervalMs={}",
                key, shutdown.get(), renewIntervalMs);
            return;
        }
        lockInstances.put(key, instance);
        long ttl = instance.getExpiredTime() > 0 ? instance.getExpiredTime()
            : DEFAULT_RENEW_INTERVAL_MS * 3;
        long interval = Math.max(1000L, Math.min(ttl / 3, renewIntervalMs));
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                LockInstance lockInstance = lockInstances.get(key);
                if (lockInstance == null) {
                    LOGGER.debug("Lock instance removed, skip renew: {}", key);
                    return;
                }
                Boolean renewed = client.renew(lockInstance);
                if (renewed == null || !renewed) {
                    LOGGER.warn("Failed to renew lock: {}, removing from watchdog", key);
                    unregister(key);
                }
            } catch (NacosException e) {
                LOGGER.error("Error renewing lock: {}, removing from watchdog", key, e);
                unregister(key);
            } catch (Exception e) {
                LOGGER.error("Unexpected error renewing lock: {}, removing from watchdog", key, e);
                unregister(key);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        renewTasks.put(key, future);
    }
    
    /**
     * Unregister a lock from automatic renewal.
     *
     * @param key lock key
     */
    public void unregister(String key) {
        lockInstances.remove(key);
        ScheduledFuture<?> future = renewTasks.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }
    
    /**
     * Shutdown the watchdog and cancel all renewal tasks.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            renewTasks.values().forEach(f -> f.cancel(false));
            renewTasks.clear();
            lockInstances.clear();
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public boolean isShutdown() {
        return shutdown.get();
    }
}
