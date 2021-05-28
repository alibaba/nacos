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

import com.alibaba.nacos.config.server.service.notify.AsyncNotifyService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Memory monitor.
 *
 * @author Nacos
 */
@Service
public class MemoryMonitor {
    
    @Autowired
    public MemoryMonitor(AsyncNotifyService notifySingleService) {
        
        ConfigExecutor.scheduleConfigTask(new PrintMemoryTask(), DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
        
        ConfigExecutor
                .scheduleConfigTask(new PrintGetConfigResponeTask(), DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
        
        ConfigExecutor
                .scheduleConfigTask(new ThreadTaskQueueMonitorTask(notifySingleService), DELAY_SECONDS, DELAY_SECONDS,
                        TimeUnit.SECONDS);
        
    }
    
    private static final long DELAY_SECONDS = 10;
    
    @Scheduled(cron = "0 0 0 * * ?")
    public void clear() {
        MetricsMonitor.getConfigMonitor().set(0);
        MetricsMonitor.getPublishMonitor().set(0);
    }
}