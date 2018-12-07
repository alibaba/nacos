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
package com.alibaba.nacos.config.server.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时任务服务
 *
 * @author Nacos
 */
public class TimerTaskService {
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private static ScheduledExecutorService scheduledExecutorService = Executors
        .newScheduledThreadPool(10, new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("com.alibaba.nacos.server.Timer-" + count.getAndIncrement());
                return t;
            }
        });

    static public void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                              TimeUnit unit) {
        scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

}
