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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembersChangeEventTest {
    
    private Member member1;
    private Member member2;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        member1 = Member.builder().ip("127.0.0.1").port(8848).state(NodeState.UP).build();
        member2 = Member.builder().ip("127.0.0.2").port(8848).state(NodeState.UP).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void builderWithMembersAndTriggers() {
        List<Member> members = Arrays.asList(member1, member2);
        List<Member> triggers = Collections.singletonList(member1);
        MembersChangeEvent event = MembersChangeEvent.builder()
            .members(members)
            .triggers(triggers)
            .build();
        assertNotNull(event);
        assertEquals(2, event.getMembers().size());
        assertTrue(event.getMembers().contains(member1));
        assertTrue(event.getMembers().contains(member2));
        assertTrue(event.hasTriggers());
        assertEquals(1, event.getTriggers().size());
        assertTrue(event.getTriggers().contains(member1));
    }
    
    @Test
    void builderWithTriggerSingle() {
        MembersChangeEvent event = MembersChangeEvent.builder()
            .members(Collections.singletonList(member1))
            .trigger(member1)
            .build();
        assertTrue(event.hasTriggers());
        assertEquals(1, event.getTriggers().size());
    }
    
    @Test
    void builderWithNullTriggers() {
        MembersChangeEvent event = MembersChangeEvent.builder()
            .members(Collections.singletonList(member1))
            .build();
        assertFalse(event.hasTriggers());
        assertNotNull(event.getTriggers());
        assertTrue(event.getTriggers().isEmpty());
    }
    
    @Test
    void toStringContainsMembersAndTriggers() {
        MembersChangeEvent event = MembersChangeEvent.builder()
            .members(Collections.singletonList(member1))
            .trigger(member1)
            .build();
        String s = event.toString();
        assertNotNull(s);
        assertTrue(s.contains("MembersChangeEvent"));
        assertTrue(s.contains("members="));
        assertTrue(s.contains("triggers="));
    }
}
