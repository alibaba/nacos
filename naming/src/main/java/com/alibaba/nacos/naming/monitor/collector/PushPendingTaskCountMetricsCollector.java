/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.monitor.collector;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.push.v2.NamingSubscriberServiceV2Impl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * pending push task metrics collector.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@Service
public class PushPendingTaskCountMetricsCollector {
    
    private static final long DELAY_SECONDS = 2;
    
    private static ScheduledExecutorService executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
        Thread thread = new Thread(r, "nacos.naming.monitor.PushPendingTaskCountMetricsCollector");
        thread.setDaemon(true);
        return thread;
    });
    
    @Autowired
    public PushPendingTaskCountMetricsCollector(NamingSubscriberServiceV2Impl namingSubscriberServiceV2) {
        executorService.scheduleWithFixedDelay(() -> {
            MetricsMonitor.getPushPendingTaskCount().set(namingSubscriberServiceV2.getPushPendingTaskCount());
        }, DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
