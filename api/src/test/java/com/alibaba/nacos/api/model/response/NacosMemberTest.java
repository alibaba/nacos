/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.response;

import com.alibaba.nacos.api.common.NodeState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosMemberTest {
    
    private ObjectMapper mapper;
    
    NacosMember member;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        member = new NacosMember();
        member.setIp("127.0.0.1");
        member.setPort(8080);
        member.setState(NodeState.UP);
        member.getExtendInfo().put("testK", "testV");
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(member);
        assertTrue(json.contains("\"ip\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":8080"));
        assertTrue(json.contains("\"state\":\"UP\""));
        assertTrue(json.contains("\"extendInfo\":{"));
        assertTrue(json.contains("\"testK\":\"testV\""));
        assertTrue(json.contains("\"address\":\"127.0.0.1:8080\""));
    }
    
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json =
                "{\"ip\":\"127.0.0.1\",\"port\":8080,\"state\":\"UP\",\"extendInfo\":{\"testK\":\"testV\"},\"address\":\"127.0.0.1:8080\","
                        + "\"abilities\":{\"remoteAbility\":{\"supportRemoteConnection\":false,\"grpcReportEnabled\":true},"
                        + "\"configAbility\":{\"supportRemoteMetrics\":false},\"namingAbility\":{\"supportJraft\":false}}}";
        NacosMember actualMember = mapper.readValue(json, NacosMember.class);
        assertEquals(member, actualMember);
        assertEquals(member.getExtendInfo(), actualMember.getExtendInfo());
        assertEquals(member.getAbilities(), actualMember.getAbilities());
        assertEquals(member.hashCode(), actualMember.hashCode());
        assertEquals(member.toString(), actualMember.toString());
    }
    
    @Test
    public void testEquals() {
        assertEquals(member, member);
        assertNotEquals(member, new Object());
        NacosMember member1 = new NacosMember();
        member1.setIp(member.getIp());
        member1.setPort(member.getPort());
        member1.setState(NodeState.DOWN);
        assertEquals(member, member1);
        member1.setIp("127.0.0.2");
        assertNotEquals(member, member1);
    }
}