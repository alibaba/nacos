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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.option.ReadOnlyOption;
import org.apache.commons.lang3.StringUtils;

import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.APPLY_BATCH;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_APPLY_BATCH;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_DISRUPTOR_BUFFER_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_ELECTION_HEARTBEAT_FACTOR;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_ENABLE_LOG_ENTRY_CHECKSUM;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_MAX_APPEND_BUFFER_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_MAX_BODY_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_MAX_BYTE_COUNT_PER_RPC;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_MAX_ELECTION_DELAY_MS;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_MAX_ENTRIES_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_MAX_REPLICATOR_INFLIGHT_MSGS;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_REPLICATOR_PIPELINE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_SYNC;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DEFAULT_SYNC_META;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.DISRUPTOR_BUFFER_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.ELECTION_HEARTBEAT_FACTOR;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.ENABLE_LOG_ENTRY_CHECKSUM;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.MAX_APPEND_BUFFER_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.MAX_BODY_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.MAX_BYTE_COUNT_PER_RPC;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.MAX_ELECTION_DELAY_MS;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.MAX_ENTRIES_SIZE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.MAX_REPLICATOR_INFLIGHT_MSGS;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.REPLICATOR_PIPELINE;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.SYNC;
import static com.alibaba.nacos.core.distributed.raft.RaftSysConstants.SYNC_META;

/**
 * build {@link RaftOptions}.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RaftOptionsBuilder {
    
    /**
     * By {@link RaftConfig} creating a {@link RaftOptions}.
     *
     * @param config {@link RaftConfig}
     * @return {@link RaftOptions}
     */
    public static RaftOptions initRaftOptions(RaftConfig config) {
        RaftOptions raftOptions = new RaftOptions();
        raftOptions.setReadOnlyOptions(raftReadIndexType(config));
        
        raftOptions.setMaxByteCountPerRpc(
                ConvertUtils.toInt(config.getVal(MAX_BYTE_COUNT_PER_RPC), DEFAULT_MAX_BYTE_COUNT_PER_RPC));
        
        raftOptions.setMaxEntriesSize(ConvertUtils.toInt(config.getVal(MAX_ENTRIES_SIZE), DEFAULT_MAX_ENTRIES_SIZE));
        
        raftOptions.setMaxBodySize(ConvertUtils.toInt(config.getVal(MAX_BODY_SIZE), DEFAULT_MAX_BODY_SIZE));
        
        raftOptions.setMaxAppendBufferSize(
                ConvertUtils.toInt(config.getVal(MAX_APPEND_BUFFER_SIZE), DEFAULT_MAX_APPEND_BUFFER_SIZE));
        
        raftOptions.setMaxElectionDelayMs(
                ConvertUtils.toInt(config.getVal(MAX_ELECTION_DELAY_MS), DEFAULT_MAX_ELECTION_DELAY_MS));
        
        raftOptions.setElectionHeartbeatFactor(
                ConvertUtils.toInt(config.getVal(ELECTION_HEARTBEAT_FACTOR), DEFAULT_ELECTION_HEARTBEAT_FACTOR));
        
        raftOptions.setApplyBatch(ConvertUtils.toInt(config.getVal(APPLY_BATCH), DEFAULT_APPLY_BATCH));
        
        raftOptions.setSync(ConvertUtils.toBoolean(config.getVal(SYNC), DEFAULT_SYNC));
        
        raftOptions.setSyncMeta(ConvertUtils.toBoolean(config.getVal(SYNC_META), DEFAULT_SYNC_META));
        
        raftOptions.setDisruptorBufferSize(
                ConvertUtils.toInt(config.getVal(DISRUPTOR_BUFFER_SIZE), DEFAULT_DISRUPTOR_BUFFER_SIZE));
        
        raftOptions.setReplicatorPipeline(
                ConvertUtils.toBoolean(config.getVal(REPLICATOR_PIPELINE), DEFAULT_REPLICATOR_PIPELINE));
        
        raftOptions.setMaxReplicatorInflightMsgs(
                ConvertUtils.toInt(config.getVal(MAX_REPLICATOR_INFLIGHT_MSGS), DEFAULT_MAX_REPLICATOR_INFLIGHT_MSGS));
        
        raftOptions.setEnableLogEntryChecksum(
                ConvertUtils.toBoolean(config.getVal(ENABLE_LOG_ENTRY_CHECKSUM), DEFAULT_ENABLE_LOG_ENTRY_CHECKSUM));
        
        return raftOptions;
    }
    
    private static ReadOnlyOption raftReadIndexType(RaftConfig config) {
        String readOnySafe = "ReadOnlySafe";
        String readOnlyLeaseBased = "ReadOnlyLeaseBased";
        
        String val = config.getVal(RaftSysConstants.RAFT_READ_INDEX_TYPE);
        
        if (StringUtils.isBlank(val) || StringUtils.equals(readOnySafe, val)) {
            return ReadOnlyOption.ReadOnlySafe;
        }
        
        if (StringUtils.equals(readOnlyLeaseBased, val)) {
            return ReadOnlyOption.ReadOnlyLeaseBased;
        }
        throw new IllegalArgumentException("Illegal Raft system parameters => ReadOnlyOption" + " : [" + val
                + "], should be 'ReadOnlySafe' or 'ReadOnlyLeaseBased'");
        
    }
    
}
