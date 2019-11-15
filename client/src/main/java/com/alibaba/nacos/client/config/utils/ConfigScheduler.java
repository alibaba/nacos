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

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.api.LifeCycle;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.common.utils.NameThreadFactory;
import com.alibaba.nacos.common.utils.ThreadHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:liaochuntao@youzan.com">liaochuntao</a>
 * @Created at 2019-11-12 13:59
 */
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class ConfigScheduler implements LifeCycle {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private static final ConfigScheduler INSTANCE = new ConfigScheduler();

    private ConfigScheduler() {
    }

    public static ConfigScheduler getInstance() {
        return INSTANCE;
    }

    private HttpAgent agent;

    private ScheduledExecutorService clientTimerSchedule;
    private ScheduledExecutorService checkConfigInfoSchedule;
    private ScheduledExecutorService longPollSchedule;

    public void setAgent(HttpAgent agent) {
        this.agent = agent;
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isDestroyed() {
        return destroyed.get();
    }

    @Override
    public void start() throws NacosException {
        if (started.compareAndSet(false, true)) {
            clientTimerSchedule = Executors
                .newSingleThreadScheduledExecutor(new NameThreadFactory("com.alibaba.nacos.client.Timer", true));

            checkConfigInfoSchedule = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.Worker." + agent.getName(), true));

            longPollSchedule = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                new NameThreadFactory("com.alibaba.nacos.client.Worker.longPolling." + agent.getName(), true));

        }
    }

    public ScheduledFuture<?> scheduleWithFixedDelayByClientTimer(Runnable command, long initialDelay,
                                                                  long delay, TimeUnit unit) {
        needStarted();
        return clientTimerSchedule.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    public ScheduledFuture<?> scheduleWithFixedDelayByCheckConfigInfoSchedule(Runnable command, long initialDelay,
                                                                              long delay, TimeUnit unit) {
        needStarted();
        return checkConfigInfoSchedule.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    public void executeByLongPollSchedule(Runnable runnable) {
        longPollSchedule.execute(runnable);
    }

    public ScheduledFuture<?> scheduldByLongPollSchedule(Runnable runnable, long delay, TimeUnit unit) {
        return longPollSchedule.schedule(runnable, delay, unit);
    }

    public boolean isShutdown4LongPollSchedule() {
        return longPollSchedule.isShutdown();
    }

    @Override
    public void destroy() throws NacosException {
        if (isStarted() && destroyed.compareAndSet(false, true)) {
            ThreadHelper.invokeShutdown(clientTimerSchedule);
            ThreadHelper.invokeShutdown(checkConfigInfoSchedule);
            ThreadHelper.invokeShutdown(longPollSchedule);
        }
    }

    private void needStarted() {
        if (!isStarted()) {
            throw new IllegalStateException("");
        }
    }
}
