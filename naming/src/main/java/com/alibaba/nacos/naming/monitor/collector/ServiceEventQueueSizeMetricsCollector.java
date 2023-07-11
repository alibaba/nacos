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
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ServiceEvent queue size metrics collector.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@Service
public class ServiceEventQueueSizeMetricsCollector {
    
    private static final long DELAY_SECONDS = 2;
    
    private static ScheduledExecutorService executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
        Thread thread = new Thread(r, "nacos.naming.monitor.ServiceEventQueueSizeMetricsCollector");
        thread.setDaemon(true);
        return thread;
    });
    
    public ServiceEventQueueSizeMetricsCollector() {
        executorService.scheduleWithFixedDelay(() -> {
            MetricsMonitor.getServiceSubscribedEventQueueSize().set(
                    (int) NotifyCenter.getPublisher(ServiceEvent.ServiceSubscribedEvent.class).currentEventSize());
            MetricsMonitor.getServiceChangedEventQueueSize().set(
                    (int) NotifyCenter.getPublisher(ServiceEvent.ServiceChangedEvent.class).currentEventSize());
        }, DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
