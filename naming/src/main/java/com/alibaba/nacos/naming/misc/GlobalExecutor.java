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

package com.alibaba.nacos.naming.misc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Global executor for naming.
 *
 * @author nacos
 */
@SuppressWarnings({"checkstyle:indentation", "PMD.ThreadPoolCreationRule"})
public class GlobalExecutor {
    
    public static final long HEARTBEAT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5L);
    
    public static final long LEADER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15L);
    
    public static final long RANDOM_MS = TimeUnit.SECONDS.toMillis(5L);
    
    public static final long TICK_PERIOD_MS = TimeUnit.MILLISECONDS.toMillis(500L);
    
    private static final long PARTITION_DATA_TIMED_SYNC_INTERVAL = TimeUnit.SECONDS.toMillis(5);
    
    private static final long SERVER_STATUS_UPDATE_PERIOD = TimeUnit.SECONDS.toMillis(5);
    
    private static ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2, registerMaster -> {
        Thread t = new Thread(registerMaster);
        
        t.setDaemon(true);
        t.setName("com.alibaba.nacos.naming.timer");
        
        return t;
    });
    
    private static ScheduledExecutorService taskDispatchExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(), taskDispatch -> {
        Thread t = new Thread(taskDispatch);
        
        t.setDaemon(true);
        t.setName("com.alibaba.nacos.naming.distro.task.dispatcher");
        
        return t;
    });
    
    private static ScheduledExecutorService dataSyncExecutor = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(), dataSync -> {
        Thread t = new Thread(dataSync);
        
        t.setDaemon(true);
        t.setName("com.alibaba.nacos.naming.distro.data.syncer");
        
        return t;
    });
    
    private static ScheduledExecutorService notifyServerListExecutor = new ScheduledThreadPoolExecutor(1, notifyServer -> {
        Thread t = new Thread(notifyServer);
        
        t.setDaemon(true);
        t.setName("com.alibaba.nacos.naming.server.list.notifier");
        
        return t;
    });
    
    private static final ScheduledExecutorService SERVER_STATUS_EXECUTOR = new ScheduledThreadPoolExecutor(1, serverStatus -> {
        Thread t = new Thread(serverStatus);
        t.setName("nacos.naming.status.worker");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * thread pool that processes getting service detail from other server asynchronously.
     */
    private static ExecutorService serviceUpdateExecutor = Executors.newFixedThreadPool(2, serviceUpdate -> {
        Thread t = new Thread(serviceUpdate);
        t.setName("com.alibaba.nacos.naming.service.update.http.handler");
        t.setDaemon(true);
        return t;
    });
    
    private static ScheduledExecutorService emptyServiceAutoCleanExecutor = Executors
            .newSingleThreadScheduledExecutor(emptyService -> {
                Thread t = new Thread(emptyService);
                t.setName("com.alibaba.nacos.naming.service.empty.auto-clean");
                t.setDaemon(true);
                return t;
            });
    
    private static ScheduledExecutorService distroNotifyExecutor = new ScheduledThreadPoolExecutor(1, distroNotify -> {
        Thread t = new Thread(distroNotify);
        
        t.setDaemon(true);
        t.setName("com.alibaba.nacos.naming.distro.notifier");
        
        return t;
    });
    
    public static void submitDataSync(Runnable runnable, long delay) {
        dataSyncExecutor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
    
    public static void schedulePartitionDataTimedSync(Runnable runnable) {
        dataSyncExecutor.scheduleWithFixedDelay(runnable, PARTITION_DATA_TIMED_SYNC_INTERVAL,
                PARTITION_DATA_TIMED_SYNC_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    public static void registerMasterElection(Runnable runnable) {
        executorService.scheduleAtFixedRate(runnable, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }
    
    public static void registerServerInfoUpdater(Runnable runnable) {
        executorService.scheduleAtFixedRate(runnable, 0, 2, TimeUnit.SECONDS);
    }
    
    public static void registerServerStatusReporter(Runnable runnable, long delay) {
        SERVER_STATUS_EXECUTOR.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
    
    public static void registerServerStatusUpdater(Runnable runnable) {
        executorService.scheduleAtFixedRate(runnable, 0, SERVER_STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    }
    
    public static void registerHeartbeat(Runnable runnable) {
        executorService.scheduleWithFixedDelay(runnable, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }
    
    public static void schedule(Runnable runnable, long period) {
        executorService.scheduleAtFixedRate(runnable, 0, period, TimeUnit.MILLISECONDS);
    }
    
    public static void schedule(Runnable runnable, long initialDelay, long period) {
        executorService.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.MILLISECONDS);
    }
    
    public static void notifyServerListChange(Runnable runnable) {
        notifyServerListExecutor.submit(runnable);
    }
    
    public static void submitTaskDispatch(Runnable runnable) {
        taskDispatchExecutor.submit(runnable);
    }
    
    public static void submit(Runnable runnable) {
        executorService.submit(runnable);
    }
    
    public static void submit(Runnable runnable, long delay) {
        executorService.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
    
    public static void submitDistroNotifyTask(Runnable runnable) {
        distroNotifyExecutor.submit(runnable);
    }
    
    public static void submitServiceUpdate(Runnable runnable) {
        serviceUpdateExecutor.execute(runnable);
    }
    
    public static void scheduleServiceAutoClean(Runnable runnable, long initialDelay, long period) {
        emptyServiceAutoCleanExecutor.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.MILLISECONDS);
    }
}
