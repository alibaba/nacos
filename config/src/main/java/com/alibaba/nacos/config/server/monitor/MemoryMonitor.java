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
package com.alibaba.nacos.config.server.monitor;

import com.alibaba.nacos.config.server.service.ClientTrackService;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.notify.AsyncNotifyService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.memoryLog;

/**
 * Memory monitor
 *
 * @author Nacos
 */
@Service
public class MemoryMonitor {

    @Autowired
    public MemoryMonitor(AsyncNotifyService notifySingleService) {

        ConfigExecutor.scheduleWithFixedDelay(new PrintMemoryTask(), DELAY_SECONDS,
            DELAY_SECONDS, TimeUnit.SECONDS);

        ConfigExecutor.scheduleWithFixedDelay(new PrintGetConfigResponeTask(), DELAY_SECONDS,
            DELAY_SECONDS, TimeUnit.SECONDS);

        ConfigExecutor.scheduleWithFixedDelay(new NotifyTaskQueueMonitorTask(notifySingleService), DELAY_SECONDS,
            DELAY_SECONDS, TimeUnit.SECONDS);

    }

    private static final long DELAY_SECONDS = 10;

    @Scheduled(cron = "0 0 0 * * ?")
    public void clear() {
        MetricsMonitor.getConfigMonitor().set(0);
        MetricsMonitor.getPublishMonitor().set(0);
    }
}

class PrintGetConfigResponeTask implements Runnable {
    @Override
    public void run() {
        memoryLog.info(ResponseMonitor.getStringForPrint());
    }
}

class PrintMemoryTask implements Runnable {

    @Override
    public void run() {
        int groupCount = ConfigCacheService.groupCount();
        int subClientCount = ClientTrackService.subscribeClientCount();
        long subCount = ClientTrackService.subscriberCount();
        memoryLog.info("groupCount={}, subscriberClientCount={}, subscriberCount={}", groupCount, subClientCount,
            subCount);
        MetricsMonitor.getConfigCountMonitor().set(groupCount);
    }
}

class NotifyTaskQueueMonitorTask implements Runnable {
    final private AsyncNotifyService notifySingleService;

    NotifyTaskQueueMonitorTask(AsyncNotifyService notifySingleService) {
        this.notifySingleService = notifySingleService;
    }

    @Override
    public void run() {
        int size = ((ScheduledThreadPoolExecutor)notifySingleService.getExecutor()).getQueue().size();
        memoryLog.info("toNotifyTaskSize={}", size);
        MetricsMonitor.getNotifyTaskMonitor().set(size);
    }
}
