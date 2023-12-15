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

package com.alibaba.nacos.core.monitor;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Metrics center.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class MetricsMonitor {
    
    private static final String METER_REGISTRY = NacosMeterRegistryCenter.CORE_STABLE_REGISTRY;
    
    private static final DistributionSummary RAFT_READ_INDEX_FAILED;
    
    private static final DistributionSummary RAFT_FROM_LEADER;
    
    private static final Timer RAFT_APPLY_LOG_TIMER;
    
    private static final Timer RAFT_APPLY_READ_TIMER;
    
    private static AtomicInteger longConnection = new AtomicInteger();

    private static GrpcServerExecutorMetric sdkServerExecutorMetric = new GrpcServerExecutorMetric("grpcSdkServer");

    private static GrpcServerExecutorMetric clusterServerExecutorMetric = new GrpcServerExecutorMetric("grpcClusterServer");

    private static Map<String, AtomicInteger> moduleConnectionCnt = new ConcurrentHashMap<>();

    static {
        ImmutableTag immutableTag = new ImmutableTag("module", "core");
        List<Tag> tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "raft_read_index_failed"));
        RAFT_READ_INDEX_FAILED = NacosMeterRegistryCenter.summary(METER_REGISTRY, "nacos_monitor", tags);
    
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "raft_read_from_leader"));
        RAFT_FROM_LEADER = NacosMeterRegistryCenter.summary(METER_REGISTRY, "nacos_monitor", tags);
    
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "raft_apply_log_timer"));
        RAFT_APPLY_LOG_TIMER = NacosMeterRegistryCenter.timer(METER_REGISTRY, "nacos_monitor", tags);
    
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "raft_apply_read_timer"));
        RAFT_APPLY_READ_TIMER = NacosMeterRegistryCenter.timer(METER_REGISTRY, "nacos_monitor", tags);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "longConnection"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, longConnection);

        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("type", sdkServerExecutorMetric.getType()));
        initGrpcServerExecutorMetric(tags, sdkServerExecutorMetric);

        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("type", clusterServerExecutorMetric.getType()));
        initGrpcServerExecutorMetric(tags, clusterServerExecutorMetric);
    }

    private static void initGrpcServerExecutorMetric(List<Tag> tags, GrpcServerExecutorMetric metric) {
        List<Tag> snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "activeCount"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getActiveCount());

        snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "poolSize"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getPoolSize());

        snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "corePoolSize"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getCorePoolSize());

        snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "maximumPoolSize"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getMaximumPoolSize());

        snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "inQueueTaskCount"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getInQueueTaskCount());

        snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "taskCount"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getTaskCount());

        snapshotTags = new ArrayList<>();
        snapshotTags.add(new ImmutableTag("name", "completedTaskCount"));
        snapshotTags.addAll(tags);
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "grpc_server_executor", snapshotTags, metric.getCompletedTaskCount());
    }
    
    public static AtomicInteger getLongConnectionMonitor() {
        return longConnection;
    }
    
    public static void raftReadIndexFailed() {
        RAFT_READ_INDEX_FAILED.record(1);
    }
    
    public static void raftReadFromLeader() {
        RAFT_FROM_LEADER.record(1);
    }
    
    public static Timer getRaftApplyLogTimer() {
        return RAFT_APPLY_LOG_TIMER;
    }
    
    public static Timer getRaftApplyReadTimer() {
        return RAFT_APPLY_READ_TIMER;
    }
    
    public static DistributionSummary getRaftReadIndexFailed() {
        return RAFT_READ_INDEX_FAILED;
    }
    
    public static DistributionSummary getRaftFromLeader() {
        return RAFT_FROM_LEADER;
    }

    public static GrpcServerExecutorMetric getSdkServerExecutorMetric() {
        return sdkServerExecutorMetric;
    }

    public static GrpcServerExecutorMetric getClusterServerExecutorMetric() {
        return clusterServerExecutorMetric;
    }

    public static class GrpcServerExecutorMetric {

        private String type;

        /**
         * cout of thread are running job.
         */
        private AtomicInteger activeCount = new AtomicInteger();

        /**
         * core thread count.
         */
        private AtomicInteger corePoolSize = new AtomicInteger();

        /**
         * current thread count.
         */
        private AtomicInteger poolSize = new AtomicInteger();

        /**
         * max thread count.
         */
        private AtomicInteger maximumPoolSize = new AtomicInteger();

        /**
         * task count in queue.
         */
        private AtomicInteger inQueueTaskCount = new AtomicInteger();

        /**
         * completed task count.
         */
        private AtomicLong completedTaskCount = new AtomicLong();

        /**
         * task count.
         */
        private AtomicLong taskCount = new AtomicLong();

        private GrpcServerExecutorMetric(String type) {
            this.type = type;
        }

        public AtomicInteger getActiveCount() {
            return activeCount;
        }

        public AtomicInteger getCorePoolSize() {
            return corePoolSize;
        }

        public AtomicInteger getPoolSize() {
            return poolSize;
        }

        public AtomicInteger getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public AtomicInteger getInQueueTaskCount() {
            return inQueueTaskCount;
        }

        public AtomicLong getCompletedTaskCount() {
            return completedTaskCount;
        }

        public AtomicLong getTaskCount() {
            return taskCount;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * refresh all module connection count.
     *
     * @param connectionCnt new connection count.
     */
    public static void refreshModuleConnectionCount(Map<String, Integer> connectionCnt) {
        // refresh all existed module connection cnt and add new module connection count
        connectionCnt.forEach((module, cnt) -> {
            AtomicInteger integer = moduleConnectionCnt.get(module);
            // if exists
            if (integer != null) {
                integer.set(cnt);
            } else {
                // new module comes
                AtomicInteger newModuleConnCnt = new AtomicInteger(cnt);
                moduleConnectionCnt.put(module, newModuleConnCnt);
                NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor",
                        Arrays.asList(
                                new ImmutableTag("module", module),
                                new ImmutableTag("name", "longConnection")
                        ),
                        moduleConnectionCnt.get(module));
            }
        });
        // reset the outdated module connection cnt
        moduleConnectionCnt.forEach((module, cnt) -> {
            if (connectionCnt.containsKey(module)) {
                return;
            }
            cnt.set(0);
        });
    }

    /**
     * getter.
     *
     * @return moduleConnectionCnt.
     */
    public static Map<String, AtomicInteger> getModuleConnectionCnt() {
        return moduleConnectionCnt;
    }
}
