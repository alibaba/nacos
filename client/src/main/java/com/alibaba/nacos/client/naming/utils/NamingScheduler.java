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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.api.LifeCycle;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.NameThreadFactory;
import com.alibaba.nacos.common.utils.ThreadHelper;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:liaochuntao@youzan.com">liaochuntao</a>
 * @Created at 2019-11-12 13:59
 */
public class NamingScheduler implements LifeCycle {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private static final NamingScheduler INSTANCE = new NamingScheduler();

    private int clientBeatThreadCount = UtilAndComs.DEFAULT_CLIENT_BEAT_THREAD_COUNT;
    private int pollingThreadCount = UtilAndComs.DEFAULT_POLLING_THREAD_COUNT;

    public static NamingScheduler getInstance() {
        return INSTANCE;
    }

    private NamingScheduler() {
    }

    private ScheduledExecutorService beatReactorExecutor;
    private ExecutorService eventDispatcherExecutor;
    private ScheduledExecutorService namingProxyExecutor;
    private ScheduledExecutorService hostReactorExecutor;
    private ScheduledExecutorService failoverReactorExecutor;
    private ScheduledExecutorService pushReceiverExecutor;

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isDestroyed() {
        return destroyed.get();
    }

    public void initClientBeatThreadCount(Properties properties) {
        if (properties != null) {
            clientBeatThreadCount = NumberUtils.toInt(properties.getProperty(PropertyKeyConst.NAMING_CLIENT_BEAT_THREAD_COUNT),
                    UtilAndComs.DEFAULT_CLIENT_BEAT_THREAD_COUNT);
        }
    }

    public void initPollingThreadCount(Properties properties) {
        if (properties != null) {
            pollingThreadCount = NumberUtils.toInt(properties.getProperty(PropertyKeyConst.NAMING_POLLING_THREAD_COUNT),
                    UtilAndComs.DEFAULT_POLLING_THREAD_COUNT);
        }
    }

    @Override
    public void start() throws NacosException {
        if (started.compareAndSet(false, true)) {
            beatReactorExecutor = new ScheduledThreadPoolExecutor(clientBeatThreadCount,
                    new NameThreadFactory("com.alibaba.nacos.naming.beat.sender-"));

            eventDispatcherExecutor = Executors.newSingleThreadExecutor(
                    new NameThreadFactory("com.alibaba.nacos.naming.client.listener"));

            namingProxyExecutor = new ScheduledThreadPoolExecutor(1,
                    new NameThreadFactory("com.alibaba.nacos.client.naming.serverlist.updater"));

            hostReactorExecutor = new ScheduledThreadPoolExecutor(pollingThreadCount,
                    new NameThreadFactory("com.alibaba.nacos.client.naming.updater-"));

            failoverReactorExecutor = Executors.newSingleThreadScheduledExecutor(
                    new NameThreadFactory("com.alibaba.nacos.naming.failover"));

            pushReceiverExecutor = new ScheduledThreadPoolExecutor(1,
                    new NameThreadFactory("com.alibaba.nacos.naming.push.receiver"));
        }
    }

    @Override
    public void destroy() throws NacosException {
        if (isStarted() && destroyed.compareAndSet(false, true)) {
            ThreadHelper.invokeShutdown(eventDispatcherExecutor);
            ThreadHelper.invokeShutdown(beatReactorExecutor);
            ThreadHelper.invokeShutdown(hostReactorExecutor);
            ThreadHelper.invokeShutdown(namingProxyExecutor);
        }
    }

    public ScheduledExecutorService getBeatReactorExecutor() {
        return beatReactorExecutor;
    }

    public ExecutorService getEventDispatcherExecutor() {
        return eventDispatcherExecutor;
    }

    public ScheduledExecutorService getNamingProxyExecutor() {
        return namingProxyExecutor;
    }

    public ScheduledExecutorService getHostReactorExecutor() {
        return hostReactorExecutor;
    }

    public ScheduledExecutorService getFailoverReactorExecutor() {
        return failoverReactorExecutor;
    }

    public ScheduledExecutorService getPushReceiverExecutor() {
        return pushReceiverExecutor;
    }

    public void execute(ExecutorService executor, Runnable runnable) {
        if (ThreadHelper.isShutdown(executor)) {
            return;
        }
        executor.execute(runnable);
    }

    public void execute(ThreadPoolExecutor executor, Runnable runnable) {
        if (ThreadHelper.isShutdown(executor)) {
            return;
        }
        executor.execute(runnable);
    }

    public void submit(ExecutorService executor, Runnable runnable) {
        if (ThreadHelper.isShutdown(executor)) {
            return;
        }
        executor.submit(runnable);
    }

    public void submit(ThreadPoolExecutor executor, Runnable runnable) {
        if (ThreadHelper.isShutdown(executor)) {
            return;
        }
        executor.submit(runnable);
    }

    public ScheduledFuture<?> schedule(ScheduledExecutorService executor, Runnable runnable, long delay, TimeUnit unit) {
        if (ThreadHelper.isShutdown(executor)) {
            return null;
        }
        return executor.schedule(runnable, delay, unit);
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(ScheduledExecutorService executor,
                                                     Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit) {
        if (ThreadHelper.isShutdown(executor)) {
            return null;
        }
        return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

}
