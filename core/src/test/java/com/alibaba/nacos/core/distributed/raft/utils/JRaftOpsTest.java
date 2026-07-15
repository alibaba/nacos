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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The sub command unit tests see {@link JRaftOpsExecuteTest}
 */
class JRaftOpsTest {
    
    @Test
    void testSourceOfReturnsCorrectEnumForEachCommand() {
        assertEquals(JRaftOps.TRANSFER_LEADER, JRaftOps.sourceOf(JRaftConstants.TRANSFER_LEADER));
        assertEquals(JRaftOps.RESET_RAFT_CLUSTER,
            JRaftOps.sourceOf(JRaftConstants.RESET_RAFT_CLUSTER));
        assertEquals(JRaftOps.DO_SNAPSHOT, JRaftOps.sourceOf(JRaftConstants.DO_SNAPSHOT));
        assertEquals(JRaftOps.REMOVE_PEER, JRaftOps.sourceOf(JRaftConstants.REMOVE_PEER));
        assertEquals(JRaftOps.REMOVE_PEERS, JRaftOps.sourceOf(JRaftConstants.REMOVE_PEERS));
        assertEquals(JRaftOps.CHANGE_PEERS, JRaftOps.sourceOf(JRaftConstants.CHANGE_PEERS));
        assertEquals(JRaftOps.RESET_PEERS, JRaftOps.sourceOf(JRaftConstants.RESET_PEERS));
    }
    
    @Test
    void testSourceOfReturnsNullForUnknownCommand() {
        assertNull(JRaftOps.sourceOf("unknown"));
        assertNull(JRaftOps.sourceOf(null));
        assertNull(JRaftOps.sourceOf(""));
    }
}
