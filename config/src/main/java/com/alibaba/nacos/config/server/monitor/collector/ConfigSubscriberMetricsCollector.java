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

package com.alibaba.nacos.config.server.monitor.collector;

import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.remote.ConfigChangeListenContext;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * v1 and v2 config subscriber metrics collector.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@Service
public class ConfigSubscriberMetricsCollector {
    
    private static final long DELAY_SECONDS = 5;
    
    @Autowired
    public ConfigSubscriberMetricsCollector(LongPollingService longPollingService, ConfigChangeListenContext configChangeListenContext) {
        ConfigExecutor.scheduleConfigTask(() -> {
            MetricsMonitor.getConfigSubscriberMonitor("v1").set(longPollingService.getSubscriberCount());
            MetricsMonitor.getConfigSubscriberMonitor("v2").set(configChangeListenContext.getConnectionCount());
        }, DELAY_SECONDS, DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
