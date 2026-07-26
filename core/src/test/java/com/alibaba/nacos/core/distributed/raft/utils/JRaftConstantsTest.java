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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JRaftConstantsTest {
    
    @Test
    void testExtendInfoKeyIsJRaftLogOperationCanonicalName() {
        assertEquals(JRaftLogOperation.class.getCanonicalName(),
            JRaftConstants.JRAFT_EXTEND_INFO_KEY);
    }
    
    @Test
    void testConstantValues() {
        assertEquals("groupId", JRaftConstants.GROUP_ID);
        assertEquals("command", JRaftConstants.COMMAND_NAME);
        assertEquals("value", JRaftConstants.COMMAND_VALUE);
        assertEquals("transferLeader", JRaftConstants.TRANSFER_LEADER);
        assertEquals("restRaftCluster", JRaftConstants.RESET_RAFT_CLUSTER);
        assertEquals("doSnapshot", JRaftConstants.DO_SNAPSHOT);
        assertEquals("removePeer", JRaftConstants.REMOVE_PEER);
        assertEquals("removePeers", JRaftConstants.REMOVE_PEERS);
        assertEquals("changePeers", JRaftConstants.CHANGE_PEERS);
        assertEquals("resetPeers", JRaftConstants.RESET_PEERS);
    }
    
    @Test
    void testConstantsAreNonBlank() {
        assertNotNull(JRaftConstants.JRAFT_EXTEND_INFO_KEY);
        assertNotNull(JRaftConstants.GROUP_ID);
        assertNotNull(JRaftConstants.COMMAND_NAME);
        assertNotNull(JRaftConstants.COMMAND_VALUE);
    }
}
