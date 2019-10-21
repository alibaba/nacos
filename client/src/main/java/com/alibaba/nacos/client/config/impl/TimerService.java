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
package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.LifeCycle;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Time Service
 *
 * @author Nacos
 */
public class TimerService implements LifeCycle {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private static final TimerService singleton = new TimerService();

    public static TimerService getSingleton() {
        return singleton;
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                                                     long delay, TimeUnit unit) {
        return scheduledExecutor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private ScheduledExecutorService scheduledExecutor;

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
            scheduledExecutor = Executors
                    .newSingleThreadScheduledExecutor(new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r);
                            t.setName("com.alibaba.nacos.client.Timer");
                            t.setDaemon(true);
                            return t;
                        }
                    });
        }
    }

    @Override
    public void destroy() throws NacosException {
        if (isStarted() && destroyed.compareAndSet(false, true)) {
            scheduledExecutor.shutdown();
        }
    }
}
