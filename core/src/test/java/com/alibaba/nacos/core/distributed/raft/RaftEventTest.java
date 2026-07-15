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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RaftEventTest {
    
    @Test
    void testBuilderCreatesInstance() {
        RaftEvent.RaftEventBuilder builder = RaftEvent.builder();
        assertNotNull(builder);
    }
    
    @Test
    void testBuilderBuildWithDefaults() {
        RaftEvent event = RaftEvent.builder().build();
        assertNull(event.getGroupId());
        assertNull(event.getLeader());
        assertNull(event.getTerm());
        assertEquals("", event.getErrMsg());
        assertEquals(Collections.emptyList(), event.getRaftClusterInfo());
    }
    
    @Test
    void testBuilderBuildWithAllFields() {
        List<String> clusterInfo = Arrays.asList("127.0.0.1:7848", "127.0.0.1:7849");
        RaftEvent event = RaftEvent.builder()
            .groupId("naming")
            .leader("127.0.0.1:7848")
            .term(5L)
            .raftClusterInfo(clusterInfo)
            .errMsg("no error")
            .build();
        assertEquals("naming", event.getGroupId());
        assertEquals("127.0.0.1:7848", event.getLeader());
        assertEquals(5L, event.getTerm());
        assertEquals(clusterInfo, event.getRaftClusterInfo());
        assertEquals("no error", event.getErrMsg());
    }
    
    @Test
    void testSettersAndGetters() {
        RaftEvent event = new RaftEvent();
        event.setGroupId("config");
        event.setLeader("leader");
        event.setTerm(10L);
        event.setErrMsg("err");
        List<String> info = Collections.singletonList("node1");
        event.setRaftClusterInfo(info);
        assertEquals("config", event.getGroupId());
        assertEquals("leader", event.getLeader());
        assertEquals(10L, event.getTerm());
        assertEquals("err", event.getErrMsg());
        assertEquals(info, event.getRaftClusterInfo());
    }
    
    @Test
    void testToString() {
        RaftEvent event = RaftEvent.builder()
            .groupId("g")
            .leader("l")
            .term(1L)
            .raftClusterInfo(Collections.singletonList("n1"))
            .build();
        String s = event.toString();
        assertNotNull(s);
        assertEquals("RaftEvent{groupId='g', leader='l', term=1, raftClusterInfo=[n1]}", s);
    }
}
