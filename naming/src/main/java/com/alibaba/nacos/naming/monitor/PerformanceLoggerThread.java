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
import com.alibaba.nacos.metrics.manager.MetricsManager;
import com.alibaba.nacos.metrics.manager.NamingMetricsConstant;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientDataProcessor;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    @Autowired
    private ServiceManager serviceManager;
    
    @Autowired
    private RaftCore raftCore;
    
    @Autowired
    private ClusterVersionJudgement versionJudgement;
    
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
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.SERVICE_COUNT)
                .set(com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().size());
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.AVG_PUSH_COST)
                .set(getAvgPushCost());
        metricsRaftLeader();
    }
    
    /**
     * Will deprecated after v1.4.x
     */
    @Deprecated
    private void metricsRaftLeader() {
        if (!versionJudgement.allMemberIsNewVersion()) {
            if (raftCore.isLeader()) {
                MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.LEADER_STATUS)
                        .set(1);
            } else if (raftCore.getPeerSet().local().state == RaftPeer.State.FOLLOWER) {
                MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.LEADER_STATUS)
                        .set(0);
            } else {
                MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.LEADER_STATUS)
                        .set(2);
            }
        }
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
                long ipCount = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.IP_COUNT).get();
                long subscribeCount = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.SUBSCRIBER_COUNT).get();
                long maxPushCost = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.MAX_PUSH_COST).get();
                long avgPushCost = getAvgPushCost();
                long totalPushCount = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH).longValue();
                long failPushCount = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.FAILED_PUSH).longValue();
                Loggers.PERFORMANCE_LOG
                        .info("PERFORMANCE:|{}|{}|{}|{}|{}|{}|{}", serviceCount, ipCount, subscribeCount, maxPushCost,
                                avgPushCost, totalPushCount, failPushCount);
                Loggers.PERFORMANCE_LOG
                        .info("Task worker status: \n" + NamingExecuteTaskDispatcher.getInstance().workersStatus());
                printDistroMonitor();
                logCount++;
                MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COUNT_FOR_AVG).set(0);
                MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COST_FOR_AVG).set(0);
                MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.MAX_PUSH_COST)
                        .set(-1);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[PERFORMANCE] Exception while print performance log.", e);
            }
            
        }
        
        private void printDistroMonitor() {
            Optional<DistroRecord> v1Record = DistroRecordsHolder.getInstance()
                    .getRecordIfExist(KeyBuilder.INSTANCE_LIST_KEY_PREFIX);
            long v1SyncDone = 0;
            long v1SyncFail = 0;
            if (v1Record.isPresent()) {
                v1SyncDone = v1Record.get().getSuccessfulSyncCount();
                v1SyncFail = v1Record.get().getFailedSyncCount();
            }
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
            Loggers.PERFORMANCE_LOG
                    .info("DISTRO:|{}|{}|{}|{}|{}|", v1SyncDone, v1SyncFail, v2SyncDone, v2SyncFail, v2VerifyFail);
        }
    }
    
    private long getAvgPushCost() {
        long size = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COUNT_FOR_AVG).get();
        long totalCost = MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COST_FOR_AVG).get();
        return (size > 0 && totalCost > 0) ? totalCost / size : -1;
    }
}
