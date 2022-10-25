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

import com.alibaba.nacos.core.distributed.distro.monitor.DistroRecord;
import com.alibaba.nacos.core.distributed.distro.monitor.DistroRecordsHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientDataProcessor;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Logger thread for print performance.
 *
 * @author nacos
 */

@Component
public class PerformanceLoggerThread {
    
    private static final long PERIOD = 60;
    
    @PostConstruct
    public void init() {
        start();
    }
    
    private void start() {
        PerformanceLogTask task = new PerformanceLogTask();
        GlobalExecutor.schedulePerformanceLogger(task, 30, PERIOD, TimeUnit.SECONDS);
    }
    
    /**
     * Refresh metrics.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void refreshMetrics() {
        MetricsMonitor.resetAll();
    }
    
    /**
     * collect metrics.
     */
    @Scheduled(cron = "0/15 * * * * ?")
    public void collectMetrics() {
        MetricsMonitor.getDomCountMonitor().set(com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().size());
        MetricsMonitor.getAvgPushCostMonitor().set(getAvgPushCost());
    }
    
    class PerformanceLogTask implements Runnable {
        
        private int logCount = 0;
        
        @Override
        public void run() {
            try {
                logCount %= 10;
                if (logCount == 0) {
                    Loggers.PERFORMANCE_LOG
                            .info("PERFORMANCE:|serviceCount|ipCount|subscribeCount|maxPushCost|avgPushCost|totalPushCount|failPushCount");
                    Loggers.PERFORMANCE_LOG.info("DISTRO:|V1SyncDone|V1SyncFail|V2SyncDone|V2SyncFail|V2VerifyFail|");
                }
                int serviceCount = com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().size();
                int ipCount = MetricsMonitor.getIpCountMonitor().get();
                int subscribeCount = MetricsMonitor.getSubscriberCount().get();
                long maxPushCost = MetricsMonitor.getMaxPushCostMonitor().get();
                long avgPushCost = getAvgPushCost();
                long totalPushCount = MetricsMonitor.getTotalPushMonitor().longValue();
                long failPushCount = MetricsMonitor.getFailedPushMonitor().longValue();
                Loggers.PERFORMANCE_LOG
                        .info("PERFORMANCE:|{}|{}|{}|{}|{}|{}|{}", serviceCount, ipCount, subscribeCount, maxPushCost,
                                avgPushCost, totalPushCount, failPushCount);
                Loggers.PERFORMANCE_LOG
                        .info("Task worker status: \n" + NamingExecuteTaskDispatcher.getInstance().workersStatus());
                printDistroMonitor();
                logCount++;
                MetricsMonitor.getTotalPushCountForAvg().set(0);
                MetricsMonitor.getTotalPushCostForAvg().set(0);
                MetricsMonitor.getMaxPushCostMonitor().set(-1);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[PERFORMANCE] Exception while print performance log.", e);
            }
            
        }
        
        private void printDistroMonitor() {
            Optional<DistroRecord> v2Record = DistroRecordsHolder.getInstance()
                    .getRecordIfExist(DistroClientDataProcessor.TYPE);
            long v2SyncDone = 0;
            long v2SyncFail = 0;
            int v2VerifyFail = 0;
            if (v2Record.isPresent()) {
                v2SyncDone = v2Record.get().getSuccessfulSyncCount();
                v2SyncFail = v2Record.get().getFailedSyncCount();
                v2VerifyFail = v2Record.get().getFailedVerifyCount();
            }
            Loggers.PERFORMANCE_LOG.info("DISTRO:|{}|{}|{}|", v2SyncDone, v2SyncFail, v2VerifyFail);
        }
    }
    
    private long getAvgPushCost() {
        int size = MetricsMonitor.getTotalPushCountForAvg().get();
        long totalCost = MetricsMonitor.getTotalPushCostForAvg().get();
        return (size > 0 && totalCost > 0) ? totalCost / size : -1;
    }
}
