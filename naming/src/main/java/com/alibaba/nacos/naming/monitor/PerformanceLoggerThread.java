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
package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.PushService;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author nacos
 */

@Component
public class PerformanceLoggerThread {

    private static final long PERIOD = 5 * 60;
    @Autowired
    private ServiceManager serviceManager;
    @Autowired
    private PushService pushService;

    @PostConstruct
    public void init() {
        start();
    }

    private void start() {
        PerformanceLogTask task = new PerformanceLogTask();
        GlobalExecutor.schedulePerformance(task, 30, PERIOD, TimeUnit.SECONDS);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void refreshMetrics() {
        pushService.setFailedPush(0);
        pushService.setTotalPush(0);
        MetricsMonitor.getHttpHealthCheckMonitor().set(0);
        MetricsMonitor.getMysqlHealthCheckMonitor().set(0);
        MetricsMonitor.getTcpHealthCheckMonitor().set(0);
    }

    @Scheduled(cron = "0/15 * * * * ?")
    public void collectMetrics() {
        int serviceCount = serviceManager.getServiceCount();
        MetricsMonitor.getDomCountMonitor().set(serviceCount);

        int ipCount = serviceManager.getInstanceCount();
        MetricsMonitor.getIpCountMonitor().set(ipCount);

        long maxPushCost = getMaxPushCost();
        MetricsMonitor.getMaxPushCostMonitor().set(maxPushCost);

        long avgPushCost = getAvgPushCost();
        MetricsMonitor.getAvgPushCostMonitor().set(avgPushCost);

        MetricsMonitor.getTotalPushMonitor().set(pushService.getTotalPush());
        MetricsMonitor.getFailedPushMonitor().set(pushService.getFailedPushCount());
    }

    private long getMaxPushCost() {
        long max = -1;

        for (Map.Entry<String, Long> entry : PushService.pushCostMap.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
            }
        }

        return max;
    }

    private long getAvgPushCost() {
        int size = 0;
        long totalCost = 0;
        long avgCost = -1;

        for (Map.Entry<String, Long> entry : PushService.pushCostMap.entrySet()) {
            size += 1;
            totalCost += entry.getValue();
        }
        PushService.pushCostMap.clear();

        if (size > 0 && totalCost > 0) {
            avgCost = totalCost / size;
        }
        return avgCost;
    }

    class PerformanceLogTask implements Runnable {

        @Override
        public void run() {
            try {
                int serviceCount = serviceManager.getServiceCount();
                int ipCount = serviceManager.getInstanceCount();
                long maxPushCost = getMaxPushCost();
                long avgPushCost = getAvgPushCost();

                Loggers.PERFORMANCE_LOG.info("PERFORMANCE:" + "|" + serviceCount + "|" + ipCount + "|" + maxPushCost + "|" + avgPushCost);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[PERFORMANCE] Exception while print performance log.", e);
            }

        }
    }
}
