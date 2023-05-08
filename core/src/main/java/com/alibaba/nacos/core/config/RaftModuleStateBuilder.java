/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.config;

import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;

/**
 * raft state builder.
 * @author 985492783@qq.com
 * @date 2023/4/6 11:26
 */
public class RaftModuleStateBuilder implements ModuleStateBuilder {
    
    public static final String SPLICE_CHARACTER = RaftSysConstants.RAFT_CONFIG_PREFIX + ".";
    
    @Override
    public ModuleState build() {
        ModuleState moduleState = new ModuleState(RaftSysConstants.RAFT_STATE);
        moduleState.newState(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS,
                Math.max(stringToInt(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS, RaftSysConstants.DEFAULT_ELECTION_TIMEOUT),
                        RaftSysConstants.DEFAULT_ELECTION_TIMEOUT));
        
        moduleState.newState(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS,
                stringToInt(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS, RaftSysConstants.DEFAULT_RAFT_SNAPSHOT_INTERVAL_SECS));
        
        moduleState.newState(RaftSysConstants.RAFT_CORE_THREAD_NUM,
                stringToInt(RaftSysConstants.RAFT_CORE_THREAD_NUM, 8));
        
        moduleState.newState(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM,
                stringToInt(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM, RaftSysConstants.DEFAULT_RAFT_CLI_SERVICE_THREAD_NUM));
        
        moduleState.newState(RaftSysConstants.RAFT_READ_INDEX_TYPE,
                getProperty(RaftSysConstants.RAFT_READ_INDEX_TYPE));
        
        moduleState.newState(RaftSysConstants.RAFT_READ_INDEX_TYPE,
                getProperty(RaftSysConstants.RAFT_READ_INDEX_TYPE));
        
        moduleState.newState(RaftSysConstants.RAFT_RPC_REQUEST_TIMEOUT_MS,
                stringToInt(RaftSysConstants.RAFT_RPC_REQUEST_TIMEOUT_MS, RaftSysConstants.DEFAULT_RAFT_RPC_REQUEST_TIMEOUT_MS));
        
        moduleState.newState(RaftSysConstants.MAX_BYTE_COUNT_PER_RPC,
                stringToInt(RaftSysConstants.MAX_BYTE_COUNT_PER_RPC, RaftSysConstants.DEFAULT_MAX_BYTE_COUNT_PER_RPC));
        
        moduleState.newState(RaftSysConstants.MAX_ENTRIES_SIZE,
                stringToInt(RaftSysConstants.MAX_ENTRIES_SIZE, RaftSysConstants.DEFAULT_MAX_ENTRIES_SIZE));
        
        moduleState.newState(RaftSysConstants.MAX_BODY_SIZE,
                stringToInt(RaftSysConstants.MAX_BODY_SIZE, RaftSysConstants.DEFAULT_MAX_BODY_SIZE));
        
        moduleState.newState(RaftSysConstants.MAX_APPEND_BUFFER_SIZE,
                stringToInt(RaftSysConstants.MAX_APPEND_BUFFER_SIZE, RaftSysConstants.DEFAULT_MAX_APPEND_BUFFER_SIZE));
        
        moduleState.newState(RaftSysConstants.MAX_ELECTION_DELAY_MS,
                stringToInt(RaftSysConstants.MAX_ELECTION_DELAY_MS, RaftSysConstants.DEFAULT_MAX_ELECTION_DELAY_MS));
        
        moduleState.newState(RaftSysConstants.ELECTION_HEARTBEAT_FACTOR,
                stringToInt(RaftSysConstants.ELECTION_HEARTBEAT_FACTOR, RaftSysConstants.DEFAULT_ELECTION_HEARTBEAT_FACTOR));
        
        moduleState.newState(RaftSysConstants.APPLY_BATCH,
                stringToInt(RaftSysConstants.APPLY_BATCH, RaftSysConstants.DEFAULT_APPLY_BATCH));
    
        moduleState.newState(RaftSysConstants.SYNC,
                stringToBoolean(RaftSysConstants.SYNC, RaftSysConstants.DEFAULT_SYNC));
        
        moduleState.newState(RaftSysConstants.SYNC_META,
                stringToBoolean(RaftSysConstants.SYNC_META, RaftSysConstants.DEFAULT_SYNC_META));
        
        moduleState.newState(RaftSysConstants.DISRUPTOR_BUFFER_SIZE,
                stringToInt(RaftSysConstants.DISRUPTOR_BUFFER_SIZE, RaftSysConstants.DEFAULT_DISRUPTOR_BUFFER_SIZE));
        
        moduleState.newState(RaftSysConstants.REPLICATOR_PIPELINE,
                stringToBoolean(RaftSysConstants.REPLICATOR_PIPELINE, RaftSysConstants.DEFAULT_REPLICATOR_PIPELINE));
        
        moduleState.newState(RaftSysConstants.MAX_REPLICATOR_INFLIGHT_MSGS,
                stringToInt(RaftSysConstants.MAX_REPLICATOR_INFLIGHT_MSGS, RaftSysConstants.DEFAULT_MAX_REPLICATOR_INFLIGHT_MSGS));
        
        moduleState.newState(RaftSysConstants.ENABLE_LOG_ENTRY_CHECKSUM,
                stringToBoolean(RaftSysConstants.ENABLE_LOG_ENTRY_CHECKSUM, RaftSysConstants.DEFAULT_ENABLE_LOG_ENTRY_CHECKSUM));
        return moduleState;
    }
    
    public static int stringToInt(String key, int defaultValue) {
        return ConvertUtils.toInt(getProperty(key), defaultValue);
    }
    
    public static boolean stringToBoolean(String key, boolean defaultValue) {
        return ConvertUtils.toBoolean(getProperty(key), defaultValue);
    }
    
    public static String getProperty(String key) {
        return EnvUtil.getProperty(SPLICE_CHARACTER + key);
    }
    
    @Override
    public boolean isIgnore() {
        return EnvUtil.getStandaloneMode();
    }
}
