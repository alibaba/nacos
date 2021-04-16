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

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.naming.NamingApp;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
    
    private static final long SERVER_STATUS_UPDATE_PERIOD = TimeUnit.SECONDS.toMillis(5);
    
    public static final int DEFAULT_THREAD_COUNT = EnvUtil.getAvailableProcessors(0.5);
    
    private static final ScheduledExecutorService NAMING_TIMER_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    EnvUtil.getAvailableProcessors(2), new NameThreadFactory("com.alibaba.nacos.naming.timer"));
    
    private static final ScheduledExecutorService SERVER_STATUS_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.status.worker"));
    
    /**
     * Service synchronization executor.
     *
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    private static final ScheduledExecutorService SERVICE_SYNCHRONIZATION_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.service.worker"));
    
    /**
     * Service update manager executor.
     *
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    public static final ScheduledExecutorService SERVICE_UPDATE_MANAGER_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.service.update.processor"));
    
    /**
     * thread pool that processes getting service detail from other server asynchronously.
     *
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    private static final ExecutorService SERVICE_UPDATE_EXECUTOR = ExecutorFactory.Managed
            .newFixedExecutorService(ClassUtils.getCanonicalName(NamingApp.class), 2,
                    new NameThreadFactory("com.alibaba.nacos.naming.service.update.http.handler"));
    
    /**
     * Empty service auto clean executor.
     *
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    private static final ScheduledExecutorService EMPTY_SERVICE_AUTO_CLEAN_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.service.empty.auto-clean"));
    
    private static final ScheduledExecutorService DISTRO_NOTIFY_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.distro.notifier"));
    
    private static final ScheduledExecutorService NAMING_HEALTH_CHECK_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.health-check.notifier"));
    
    private static final ExecutorService MYSQL_CHECK_EXECUTOR = ExecutorFactory.Managed
            .newFixedExecutorService(ClassUtils.getCanonicalName(NamingApp.class), DEFAULT_THREAD_COUNT,
                    new NameThreadFactory("com.alibaba.nacos.naming.mysql.checker"));
    
    private static final ScheduledExecutorService TCP_SUPER_SENSE_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class), DEFAULT_THREAD_COUNT,
                    new NameThreadFactory("com.alibaba.nacos.naming.supersense.checker"));
    
    private static final ExecutorService TCP_CHECK_EXECUTOR = ExecutorFactory.Managed
            .newFixedExecutorService(ClassUtils.getCanonicalName(NamingApp.class), 2,
                    new NameThreadFactory("com.alibaba.nacos.naming.tcp.check.worker"));
    
    private static final ScheduledExecutorService NAMING_HEALTH_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    Integer.max(Integer.getInteger("com.alibaba.nacos.naming.health.thread.num", DEFAULT_THREAD_COUNT),
                            1), new NameThreadFactory("com.alibaba.nacos.naming.health"));
    
    private static final ScheduledExecutorService RETRANSMITTER_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.push.retransmitter"));
    
    private static final ScheduledExecutorService UDP_SENDER_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.push.udpSender"));
    
    private static final ScheduledExecutorService SERVER_PERFORMANCE_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.nacos-server-performance"));
    
    private static final ScheduledExecutorService EXPIRED_CLIENT_CLEANER_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(NamingApp.class),
                    new NameThreadFactory("com.alibaba.nacos.naming.remote-connection-manager"));
    
    private static final ExecutorService PUSH_CALLBACK_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService("Push", new NameThreadFactory("com.alibaba.nacos.naming.push.callback"));
    
    /**
     * Register raft leader election executor.
     *
     * @param runnable leader election executor
     * @return future
     * @deprecated will removed with old raft
     */
    @Deprecated
    public static ScheduledFuture registerMasterElection(Runnable runnable) {
        return NAMING_TIMER_EXECUTOR.scheduleAtFixedRate(runnable, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }
    
    public static void registerServerInfoUpdater(Runnable runnable) {
        NAMING_TIMER_EXECUTOR.scheduleAtFixedRate(runnable, 0, 2, TimeUnit.SECONDS);
    }
    
    public static void registerServerStatusReporter(Runnable runnable, long delay) {
        SERVER_STATUS_EXECUTOR.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
    
    public static void registerServerStatusUpdater(Runnable runnable) {
        NAMING_TIMER_EXECUTOR.scheduleAtFixedRate(runnable, 0, SERVER_STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Register raft heart beat executor.
     *
     * @param runnable heart beat executor
     * @return future
     * @deprecated will removed with old raft
     */
    @Deprecated
    public static ScheduledFuture registerHeartbeat(Runnable runnable) {
        return NAMING_TIMER_EXECUTOR.scheduleWithFixedDelay(runnable, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }
    
    public static void scheduleMcpPushTask(Runnable runnable, long initialDelay, long period) {
        NAMING_TIMER_EXECUTOR.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.MILLISECONDS);
    }
    
    public static ScheduledFuture submitClusterVersionJudge(Runnable runnable, long delay) {
        return NAMING_TIMER_EXECUTOR.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
    
    public static void submitDistroNotifyTask(Runnable runnable) {
        DISTRO_NOTIFY_EXECUTOR.submit(runnable);
    }
    
    /**
     * Submit service update for v1.x.
     *
     * @param runnable runnable
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    public static void submitServiceUpdate(Runnable runnable) {
        SERVICE_UPDATE_EXECUTOR.execute(runnable);
    }
    
    /**
     * Schedule empty service auto clean for v1.x.
     *
     * @param runnable     runnable
     * @param initialDelay initial delay milliseconds
     * @param period       period between twice clean
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    public static void scheduleServiceAutoClean(Runnable runnable, long initialDelay, long period) {
        EMPTY_SERVICE_AUTO_CLEAN_EXECUTOR.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.MILLISECONDS);
    }
    
    /**
     * submitServiceUpdateManager.
     *
     * @param runnable runnable
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    public static void submitServiceUpdateManager(Runnable runnable) {
        SERVICE_UPDATE_MANAGER_EXECUTOR.submit(runnable);
    }
    
    /**
     * scheduleServiceReporter.
     *
     * @param command command
     * @param delay   delay
     * @param unit    time unit
     * @deprecated will remove in v2.1.x.
     */
    @Deprecated
    public static void scheduleServiceReporter(Runnable command, long delay, TimeUnit unit) {
        SERVICE_SYNCHRONIZATION_EXECUTOR.schedule(command, delay, unit);
    }
    
    public static void scheduleNamingHealthCheck(Runnable command, long delay, TimeUnit unit) {
        NAMING_HEALTH_CHECK_EXECUTOR.schedule(command, delay, unit);
    }
    
    public static void executeMysqlCheckTask(Runnable runnable) {
        MYSQL_CHECK_EXECUTOR.execute(runnable);
    }
    
    public static void submitTcpCheck(Runnable runnable) {
        TCP_CHECK_EXECUTOR.submit(runnable);
    }
    
    public static <T> List<Future<T>> invokeAllTcpSuperSenseTask(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return TCP_SUPER_SENSE_EXECUTOR.invokeAll(tasks);
    }
    
    public static void executeTcpSuperSense(Runnable runnable) {
        TCP_SUPER_SENSE_EXECUTOR.execute(runnable);
    }
    
    public static void scheduleTcpSuperSenseTask(Runnable runnable, long delay, TimeUnit unit) {
        TCP_SUPER_SENSE_EXECUTOR.schedule(runnable, delay, unit);
    }
    
    public static ScheduledFuture<?> scheduleNamingHealth(Runnable command, long delay, TimeUnit unit) {
        return NAMING_HEALTH_EXECUTOR.schedule(command, delay, unit);
    }
    
    public static ScheduledFuture<?> scheduleNamingHealth(Runnable command, long initialDelay, long delay,
            TimeUnit unit) {
        return NAMING_HEALTH_EXECUTOR.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
    
    public static void scheduleRetransmitter(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        RETRANSMITTER_EXECUTOR.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
    }
    
    public static void scheduleRetransmitter(Runnable runnable, long delay, TimeUnit unit) {
        RETRANSMITTER_EXECUTOR.schedule(runnable, delay, unit);
    }
    
    public static ScheduledFuture<?> scheduleUdpSender(Runnable runnable, long delay, TimeUnit unit) {
        return UDP_SENDER_EXECUTOR.schedule(runnable, delay, unit);
    }
    
    public static void scheduleUdpReceiver(Runnable runnable) {
        NAMING_TIMER_EXECUTOR.submit(runnable);
    }
    
    public static void schedulePerformanceLogger(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        SERVER_PERFORMANCE_EXECUTOR.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
    }
    
    public static void scheduleExpiredClientCleaner(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        EXPIRED_CLIENT_CLEANER_EXECUTOR.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
    }
    
    public static ExecutorService getCallbackExecutor() {
        return PUSH_CALLBACK_EXECUTOR;
    }
}
