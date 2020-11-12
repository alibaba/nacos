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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.core.utils.ClassUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Config executor.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ConfigExecutor {
    
    private static final Executor DUMP_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(Config.class),
                    new NameThreadFactory("com.alibaba.nacos.config.embedded.dump"));
    
    private static final ScheduledExecutorService TIMER_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(Config.class), 10,
                    new NameThreadFactory("com.alibaba.nacos.config.server.timer"));
    
    private static final ScheduledExecutorService CAPACITY_MANAGEMENT_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(Config.class),
                    new NameThreadFactory("com.alibaba.nacos.config.CapacityManagement"));
    
    private static final ScheduledExecutorService ASYNC_NOTIFY_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(Config.class), 100,
                    new NameThreadFactory("com.alibaba.nacos.config.AsyncNotifyService"));
    
    private static final ScheduledExecutorService CONFIG_SUB_SERVICE_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(Config.class),
                    ThreadUtils.getSuitableThreadCount(),
                    new NameThreadFactory("com.alibaba.nacos.config.ConfigSubService"));
    
    private static final ScheduledExecutorService LONG_POLLING_EXECUTOR = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(Config.class),
                    new NameThreadFactory("com.alibaba.nacos.config.LongPolling"));
    
    private static final ScheduledExecutorService ASYNC_CONFIG_CHANGE_NOTIFY_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(Config.class),
                    ThreadUtils.getSuitableThreadCount(),
                    new NameThreadFactory("com.alibaba.nacos.config.server.remote.ConfigChangeNotifier"));
    
    public static void scheduleConfigTask(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        TIMER_EXECUTOR.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
    
    public static void executeEmbeddedDump(Runnable runnable) {
        DUMP_EXECUTOR.execute(runnable);
    }
    
    public static void scheduleCorrectUsageTask(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        CAPACITY_MANAGEMENT_EXECUTOR.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
    }
    
    public static void executeAsyncNotify(Runnable runnable) {
        ASYNC_NOTIFY_EXECUTOR.execute(runnable);
    }
    
    public static void scheduleAsyncNotify(Runnable command, long delay, TimeUnit unit) {
        ASYNC_NOTIFY_EXECUTOR.schedule(command, delay, unit);
    }
    
    public static int asyncNotifyQueueSize() {
        return ((ScheduledThreadPoolExecutor) ASYNC_NOTIFY_EXECUTOR).getQueue().size();
    }
    
    public static int asyncCofigChangeClientNotifyQueueSize() {
        return ((ScheduledThreadPoolExecutor) ASYNC_CONFIG_CHANGE_NOTIFY_EXECUTOR).getQueue().size();
    }
    
    public static ScheduledExecutorService getConfigSubServiceExecutor() {
        return CONFIG_SUB_SERVICE_EXECUTOR;
    }
    
    public static ScheduledExecutorService getClientConfigNotifierServiceExecutor() {
        return ASYNC_CONFIG_CHANGE_NOTIFY_EXECUTOR;
    }
    
    public static void scheduleLongPolling(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        LONG_POLLING_EXECUTOR.scheduleWithFixedDelay(runnable, initialDelay, period, unit);
    }
    
    public static ScheduledFuture<?> scheduleLongPolling(Runnable runnable, long period, TimeUnit unit) {
        return LONG_POLLING_EXECUTOR.schedule(runnable, period, unit);
    }
    
    public static void executeLongPolling(Runnable runnable) {
        LONG_POLLING_EXECUTOR.execute(runnable);
    }
}
