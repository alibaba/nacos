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

package com.alibaba.nacos.core.distributed.raft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaftSysConstantsTest {
    
    @Test
    void testDefaultElectionAndSnapshotConstants() {
        assertEquals(5000, RaftSysConstants.DEFAULT_ELECTION_TIMEOUT);
        assertEquals(30 * 60, RaftSysConstants.DEFAULT_RAFT_SNAPSHOT_INTERVAL_SECS);
        assertEquals(4, RaftSysConstants.DEFAULT_RAFT_CLI_SERVICE_THREAD_NUM);
        assertEquals("ReadOnlySafe", RaftSysConstants.DEFAULT_READ_INDEX_TYPE);
        assertEquals(5000, RaftSysConstants.DEFAULT_RAFT_RPC_REQUEST_TIMEOUT_MS);
    }
    
    @Test
    void testDefaultSizeConstants() {
        assertEquals(128 * 1024, RaftSysConstants.DEFAULT_MAX_BYTE_COUNT_PER_RPC);
        assertEquals(1024, RaftSysConstants.DEFAULT_MAX_ENTRIES_SIZE);
        assertEquals(512 * 1024, RaftSysConstants.DEFAULT_MAX_BODY_SIZE);
        assertEquals(256 * 1024, RaftSysConstants.DEFAULT_MAX_APPEND_BUFFER_SIZE);
    }
    
    @Test
    void testDefaultElectionDelayAndHeartbeat() {
        assertEquals(1000, RaftSysConstants.DEFAULT_MAX_ELECTION_DELAY_MS);
        assertEquals(10, RaftSysConstants.DEFAULT_ELECTION_HEARTBEAT_FACTOR);
        assertEquals(32, RaftSysConstants.DEFAULT_APPLY_BATCH);
    }
    
    @Test
    void testDefaultSyncAndBufferConstants() {
        assertTrue(RaftSysConstants.DEFAULT_SYNC);
        assertFalse(RaftSysConstants.DEFAULT_SYNC_META);
        assertEquals(16384, RaftSysConstants.DEFAULT_DISRUPTOR_BUFFER_SIZE);
        assertTrue(RaftSysConstants.DEFAULT_REPLICATOR_PIPELINE);
        assertEquals(256, RaftSysConstants.DEFAULT_MAX_REPLICATOR_INFLIGHT_MSGS);
        assertFalse(RaftSysConstants.DEFAULT_ENABLE_LOG_ENTRY_CHECKSUM);
    }
    
    @Test
    void testRaftStateAndConfigPrefix() {
        assertEquals("raft", RaftSysConstants.RAFT_STATE);
        assertEquals("nacos.core.protocol.raft", RaftSysConstants.RAFT_CONFIG_PREFIX);
    }
    
    @Test
    void testSettingKeys() {
        assertEquals("election_timeout_ms", RaftSysConstants.RAFT_ELECTION_TIMEOUT_MS);
        assertEquals("snapshot_interval_secs", RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS);
        assertEquals("request_failoverRetries", RaftSysConstants.REQUEST_FAILOVER_RETRIES);
        assertEquals("core_thread_num", RaftSysConstants.RAFT_CORE_THREAD_NUM);
        assertEquals("cli_service_thread_num", RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM);
        assertEquals("read_index_type", RaftSysConstants.RAFT_READ_INDEX_TYPE);
        assertEquals("rpc_request_timeout_ms", RaftSysConstants.RAFT_RPC_REQUEST_TIMEOUT_MS);
        assertEquals("max_byte_count_per_rpc", RaftSysConstants.MAX_BYTE_COUNT_PER_RPC);
        assertEquals("max_entries_size", RaftSysConstants.MAX_ENTRIES_SIZE);
        assertEquals("max_body_size", RaftSysConstants.MAX_BODY_SIZE);
        assertEquals("max_append_buffer_size", RaftSysConstants.MAX_APPEND_BUFFER_SIZE);
        assertEquals("max_election_delay_ms", RaftSysConstants.MAX_ELECTION_DELAY_MS);
        assertEquals("election_heartbeat_factor", RaftSysConstants.ELECTION_HEARTBEAT_FACTOR);
        assertEquals("apply_batch", RaftSysConstants.APPLY_BATCH);
        assertEquals("sync", RaftSysConstants.SYNC);
        assertEquals("sync_meta", RaftSysConstants.SYNC_META);
        assertEquals("disruptor_buffer_size", RaftSysConstants.DISRUPTOR_BUFFER_SIZE);
        assertEquals("replicator_pipeline", RaftSysConstants.REPLICATOR_PIPELINE);
        assertEquals("max_replicator_inflight_msgs", RaftSysConstants.MAX_REPLICATOR_INFLIGHT_MSGS);
        assertEquals("enable_log_entry_checksum", RaftSysConstants.ENABLE_LOG_ENTRY_CHECKSUM);
    }
}
