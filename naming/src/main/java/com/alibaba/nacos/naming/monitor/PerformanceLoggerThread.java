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

import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author nacos
 */

@Component
public class PerformanceLoggerThread {

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private PushService pushService;

    @Autowired
    private RaftCore raftCore;

    private ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("nacos-server-performance");
            return t;
        }
    });

    private static final long PERIOD = 5 * 60;
    private static final long HEALTH_CHECK_PERIOD = 5 * 60;

    @PostConstruct
    public void init() {
        start();
    }

    private void freshHealthCheckSwitch() {
        Loggers.SRV_LOG.info("[HEALTH-CHECK] health check is {}", switchDomain.isHealthCheckEnabled());
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
    public void collectmetrics() {
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

        if (raftCore.isLeader()) {
            MetricsMonitor.getLeaderStatusMonitor().set(1);
        } else if (raftCore.getPeerSet().local().state == RaftPeer.State.FOLLOWER) {
            MetricsMonitor.getLeaderStatusMonitor().set(0);
        } else {
            MetricsMonitor.getLeaderStatusMonitor().set(2);
        }
    }

    class PerformanceLogTask implements Runnable {

        @Override
        public void run() {
            try {
                int serviceCount = serviceManager.getServiceCount();
                int ipCount = serviceManager.getInstanceCount();
                long maxPushMaxCost = getMaxPushCost();
                long maxPushCost = getMaxPushCost();
                long avgPushCost = getAvgPushCost();

                Loggers.PERFORMANCE_LOG.info("PERFORMANCE:" + "|" + serviceCount + "|" + ipCount + "|" + maxPushCost + "|" + avgPushCost);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[PERFORMANCE] Exception while print performance log.", e);
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
