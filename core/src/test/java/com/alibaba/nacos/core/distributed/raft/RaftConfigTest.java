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

import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaftConfigTest {
    
    private RaftConfig config;
    
    @BeforeEach
    void setUp() {
        config = new RaftConfig();
    }
    
    @Test
    void testSetMembersAndGetters() {
        Set<String> members = new HashSet<>();
        members.add("127.0.0.1:7848");
        members.add("127.0.0.1:7849");
        config.setMembers("127.0.0.1:7848", members);
        assertEquals("127.0.0.1:7848", config.getSelfMember());
        assertEquals(members, config.getMembers());
        assertTrue(config.getMembers().contains("127.0.0.1:7848"));
        assertTrue(config.getMembers().contains("127.0.0.1:7849"));
    }
    
    @Test
    void testSetMembersReplacesPreviousMembers() {
        config.setMembers("self1", Collections.singleton("a"));
        config.setMembers("self2", Collections.singleton("b"));
        assertEquals("self2", config.getSelfMember());
        assertEquals(1, config.getMembers().size());
        assertTrue(config.getMembers().contains("b"));
    }
    
    @Test
    void testAddMembersAndRemoveMembers() {
        config.setMembers("self", new HashSet<>());
        config.addMembers(Set.of("a", "b"));
        assertEquals(2, config.getMembers().size());
        config.addMembers(Set.of("c"));
        assertEquals(3, config.getMembers().size());
        config.removeMembers(Set.of("b"));
        assertEquals(2, config.getMembers().size());
        assertTrue(config.getMembers().contains("a"));
        assertTrue(config.getMembers().contains("c"));
    }
    
    @Test
    void testSetValAndGetVal() {
        config.setVal("key1", "value1");
        config.setVal("key2", "value2");
        assertEquals("value1", config.getVal("key1"));
        assertEquals("value2", config.getVal("key2"));
        assertEquals("default", config.getValOfDefault("missing", "default"));
        assertEquals("value1", config.getValOfDefault("key1", "ignored"));
    }
    
    @Test
    void testGetDataAndSetData() {
        Map<String, String> data = new HashMap<>();
        data.put("k1", "v1");
        config.setData(data);
        assertNotNull(config.getData());
        assertEquals("v1", config.getData().get("k1"));
        config.setVal("k2", "v2");
        assertEquals("v2", config.getData().get("k2"));
    }
    
    @Test
    void testStrictMode() {
        assertFalse(config.isStrictMode());
        config.setStrictMode(true);
        assertTrue(config.isStrictMode());
    }
    
    @Test
    void testToString() {
        config.setVal("a", "b");
        String s = config.toString();
        assertNotNull(s);
        assertTrue(s.contains("a") && s.contains("b"));
    }
    
    @Test
    void testToStringWhenJacksonThrowsReturnsDataString() {
        config.setVal("k", "v");
        try (MockedStatic<JacksonUtils> jacksonMock = Mockito.mockStatic(JacksonUtils.class)) {
            jacksonMock.when(() -> JacksonUtils.toJson(config))
                .thenThrow(new RuntimeException("serialize error"));
            String s = config.toString();
            assertNotNull(s);
            assertTrue(s.contains("k") && s.contains("v"));
        }
    }
}
