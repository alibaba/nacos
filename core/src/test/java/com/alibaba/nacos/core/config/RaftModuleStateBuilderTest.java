/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.config;

import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * raft module state builder test.
 * @author 985492783@qq.com
 * @date 2023/4/8 0:18
 */
public class RaftModuleStateBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    @Before
    public void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    public void testBuild() {
        ModuleState actual = new RaftModuleStateBuilder().build();
        Map<String, Object> states = actual.getStates();
        assertEquals(RaftSysConstants.RAFT_STATE, actual.getModuleName());
        assertEquals(RaftSysConstants.DEFAULT_ELECTION_TIMEOUT, states.get(RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS));
        assertEquals(RaftSysConstants.DEFAULT_RAFT_SNAPSHOT_INTERVAL_SECS, states.get(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS));
        assertEquals(RaftSysConstants.DEFAULT_RAFT_CLI_SERVICE_THREAD_NUM, states.get(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM));
        assertNull(states.get(RaftSysConstants.RAFT_READ_INDEX_TYPE));
        assertEquals(RaftSysConstants.DEFAULT_RAFT_RPC_REQUEST_TIMEOUT_MS, states.get(RaftSysConstants.RAFT_RPC_REQUEST_TIMEOUT_MS));
        assertEquals(RaftSysConstants.DEFAULT_MAX_BYTE_COUNT_PER_RPC, states.get(RaftSysConstants.MAX_BYTE_COUNT_PER_RPC));
        assertEquals(RaftSysConstants.DEFAULT_MAX_ENTRIES_SIZE, states.get(RaftSysConstants.MAX_ENTRIES_SIZE));
        assertEquals(RaftSysConstants.DEFAULT_MAX_BODY_SIZE, states.get(RaftSysConstants.MAX_BODY_SIZE));
        assertEquals(RaftSysConstants.DEFAULT_MAX_APPEND_BUFFER_SIZE, states.get(RaftSysConstants.MAX_APPEND_BUFFER_SIZE));
        assertEquals(RaftSysConstants.DEFAULT_MAX_ELECTION_DELAY_MS, states.get(RaftSysConstants.MAX_ELECTION_DELAY_MS));
        assertEquals(RaftSysConstants.DEFAULT_ELECTION_HEARTBEAT_FACTOR, states.get(RaftSysConstants.ELECTION_HEARTBEAT_FACTOR));
        assertEquals(RaftSysConstants.DEFAULT_APPLY_BATCH, states.get(RaftSysConstants.APPLY_BATCH));
        assertEquals(RaftSysConstants.DEFAULT_SYNC, states.get(RaftSysConstants.SYNC));
        assertEquals(RaftSysConstants.DEFAULT_SYNC_META, states.get(RaftSysConstants.SYNC_META));
        assertEquals(RaftSysConstants.DEFAULT_DISRUPTOR_BUFFER_SIZE, states.get(RaftSysConstants.DISRUPTOR_BUFFER_SIZE));
        assertEquals(RaftSysConstants.DEFAULT_REPLICATOR_PIPELINE, states.get(RaftSysConstants.REPLICATOR_PIPELINE));
        assertEquals(RaftSysConstants.DEFAULT_MAX_REPLICATOR_INFLIGHT_MSGS, states.get(RaftSysConstants.MAX_REPLICATOR_INFLIGHT_MSGS));
        assertEquals(RaftSysConstants.DEFAULT_ENABLE_LOG_ENTRY_CHECKSUM, states.get(RaftSysConstants.ENABLE_LOG_ENTRY_CHECKSUM));
    }
}
