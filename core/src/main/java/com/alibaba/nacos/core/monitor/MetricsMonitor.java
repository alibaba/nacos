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
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Metrics center.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class MetricsMonitor {
    
    private static final DistributionSummary RAFT_READ_INDEX_FAILED;
    
    private static final DistributionSummary RAFT_FROM_LEADER;
    
    private static final Timer RAFT_APPLY_LOG_TIMER;
    
    private static final Timer RAFT_APPLY_READ_TIMER;
    
    private static AtomicInteger longConnection = new AtomicInteger();
    
    static {
        RAFT_READ_INDEX_FAILED = NacosMeterRegistry.summary("protocol", "raft_read_index_failed");
        RAFT_FROM_LEADER = NacosMeterRegistry.summary("protocol", "raft_read_from_leader");
        
        RAFT_APPLY_LOG_TIMER = NacosMeterRegistry.timer("protocol", "raft_apply_log_timer");
        RAFT_APPLY_READ_TIMER = NacosMeterRegistry.timer("protocol", "raft_apply_read_timer");
        
        List<Tag> tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "longConnection"));
        Metrics.gauge("nacos_monitor", tags, longConnection);
        
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
}
