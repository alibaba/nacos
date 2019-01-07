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
import com.alibaba.nacos.naming.healthcheck.HttpHealthCheckProcessor;
import com.alibaba.nacos.naming.healthcheck.MysqlHealthCheckProcessor;
import com.alibaba.nacos.naming.healthcheck.TcpSuperSenseProcessor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.raft.RaftCore;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.alibaba.nacos.naming.raft.RaftPeer.State.FOLLOWER;
import static com.alibaba.nacos.naming.raft.RaftPeer.State.LEADER;

/**
 * @author nacos
 */
@Component
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

    private static final long PERIOD = 5 * 60;
    private static final long HEALTH_CHECK_PERIOD = 5 * 60;

    @PostConstruct
    public void init() {
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

    @Scheduled(cron = "0 0 0 * * ?")
    public void refresh() {
        PushService.setFailedPush(0);
        PushService.setTotalPush(0);
        HttpHealthCheckProcessor.getHttpHealthCheck().set(0);
        MysqlHealthCheckProcessor.getMysqlHealthCheck().set(0);
        TcpSuperSenseProcessor.getTcpHealthCheck().set(0);
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
        private AtomicInteger domCount = new AtomicInteger(0);
        private AtomicInteger ipCount = new AtomicInteger(0);
        private AtomicLong maxPushCost = new AtomicLong(0);
        private AtomicLong avgPushCost = new AtomicLong(0);
        private AtomicLong leaderStatus = new AtomicLong(0);
        private AtomicInteger totalPush = new AtomicInteger(0);
        private AtomicInteger failedPush = new AtomicInteger(0);

        public PerformanceLogTask() {

            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "domCount"));
            Metrics.gauge("nacos_monitor", tags, domCount);

            tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "ipCount"));
            Metrics.gauge("nacos_monitor", tags, ipCount);

            tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "maxPushCost"));
            Metrics.gauge("nacos_monitor", tags, maxPushCost);

            tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "avgPushCost"));
            Metrics.gauge("nacos_monitor", tags, avgPushCost);

            tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "leaderStatus"));
            Metrics.gauge("nacos_monitor", tags, leaderStatus);

            tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "totalPush"));
            Metrics.gauge("nacos_monitor", tags, totalPush);

            tags = new ArrayList<>();
            tags.add(Tag.of("module", "naming"));
            tags.add(Tag.of("name", "failedPush"));
            Metrics.gauge("nacos_monitor", tags, failedPush);
        }

        @Override
        public void run() {
            try {
                domCount.set(domainsManager.getDomCount());
                ipCount.set(domainsManager.getIPCount());
                maxPushCost.set(getMaxPushCost());
                avgPushCost.set(getAvgPushCost());
                totalPush.set(PushService.getTotalPush());
                failedPush.set(PushService.getFailedPushCount());

                if (RaftCore.isLeader()) {
                    leaderStatus.set(1);
                } else if (RaftCore.getPeerSet().local().state == FOLLOWER) {
                    leaderStatus.set(0);
                } else {
                    leaderStatus.set(2);
                }

                Loggers.PERFORMANCE_LOG.info("PERFORMANCE:" + "|" + domCount + "|" + ipCount + "|" + maxPushCost + "|" + avgPushCost);
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
