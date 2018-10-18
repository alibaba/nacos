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

import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.push.PushService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author nacos
 */
public class PerformanceLoggerThread {

    @Autowired
    private DomainsManager domainsManager;

    private ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("nacos-server-performance");
            return t;
        }
    });

    private static final long PERIOD = 1 * 60 * 60;
    private static final long HEALTH_CHECK_PERIOD = 5 * 60;

    public void init(DomainsManager domainsManager) {
        this.domainsManager = domainsManager;
        start();
    }

    private void freshHealthCheckSwitch() {
        Loggers.SRV_LOG.info("[HEALTH-CHECK] health check is " + Switch.isHealthCheckEnabled());
    }

    class HealthCheckSwitchTask implements Runnable {

        @Override
        public void run() {
            try {
                freshHealthCheckSwitch();
            } catch (Exception ignore) {

            }
        }
    }

    private void start() {
        PerformanceLogTask task = new PerformanceLogTask();
        executor.scheduleWithFixedDelay(task, 30, PERIOD, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(new HealthCheckSwitchTask(), 30, HEALTH_CHECK_PERIOD, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(new AllDomNamesTask(), 60, 60, TimeUnit.SECONDS);

    }

    class AllDomNamesTask implements Runnable {

        @Override
        public void run() {
            try {
                domainsManager.setAllDomNames(new ArrayList<String>(domainsManager.getAllDomNames()));
                Loggers.PERFORMANCE_LOG.debug("refresh all dom names: " + domainsManager.getAllDomNamesCache().size());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class PerformanceLogTask implements Runnable {

        @Override
        public void run() {
            try {
                int domCount = domainsManager.getDomCount();
                int ipCount = domainsManager.getIPCount();
                long maxPushMaxCost = getMaxPushCost();
                long avgPushCost = getAvgPushCost();
                Loggers.PERFORMANCE_LOG.info("PERFORMANCE:" + "|" + domCount + "|" + ipCount + "|" + maxPushMaxCost + "|" + avgPushCost);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("PERFORMANCE", "Exception while print performance log.", e);
            }

        }
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
}
