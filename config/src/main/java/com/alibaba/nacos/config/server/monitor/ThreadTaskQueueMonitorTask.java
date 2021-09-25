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
import com.alibaba.nacos.metrics.manager.ConfigMetricsConstant;
import com.alibaba.nacos.metrics.manager.MetricsManager;

import static com.alibaba.nacos.config.server.utils.LogUtil.MEMORY_LOG;

/**
 * NotifyTaskQueueMonitorTask.
 *
 * @author zongtanghu
 */
public class ThreadTaskQueueMonitorTask implements Runnable {
    
    private final AsyncNotifyService notifySingleService;
    
    ThreadTaskQueueMonitorTask(AsyncNotifyService notifySingleService) {
        this.notifySingleService = notifySingleService;
    }
    
    @Override
    public void run() {
        int size = ConfigExecutor.asyncNotifyQueueSize();
        int notifierClientSize = ConfigExecutor.asyncCofigChangeClientNotifyQueueSize();
        MEMORY_LOG.info("toNotifyTaskSize = {}", size);
        MEMORY_LOG.info("toClientNotifyTaskSize = {}", notifierClientSize);
        MetricsManager.gauge(ConfigMetricsConstant.NACOS_MONITOR,
                        ConfigMetricsConstant.MODULE, ConfigMetricsConstant.CONFIG,
                        ConfigMetricsConstant.NAME, ConfigMetricsConstant.NOTIFY_TASK)
                .set(size);
        MetricsManager.gauge(ConfigMetricsConstant.NACOS_MONITOR,
                        ConfigMetricsConstant.MODULE, ConfigMetricsConstant.CONFIG,
                        ConfigMetricsConstant.NAME, ConfigMetricsConstant.NOTIFY_CLIENT_TASK)
                .set(notifierClientSize);
    }
}
