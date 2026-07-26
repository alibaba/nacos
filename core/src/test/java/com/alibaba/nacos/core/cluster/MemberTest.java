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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberTest {
    
    Member member;
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        member = Member.builder().ip("127.0.0.1").port(8080).state(NodeState.UP).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    public void testSerialize() {
        String json = JacksonUtils.toJson(member);
        assertTrue(json.contains("\"ip\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":8080"));
        assertTrue(json.contains("\"state\":\"UP\""));
        assertTrue(json.contains("\"extendInfo\":{"));
        assertTrue(json.contains("\"adWeight\":\"0\""));
        assertTrue(json.contains("\"site\":\"unknow\""));
        assertTrue(json.contains("\"weight\":\"1\""));
        assertTrue(json.contains("\"address\":\"127.0.0.1:8080\""));
        assertTrue(json.contains("\"failAccessCnt\":0"));
    }
    
    @Test
    public void testDeserialize() {
        String json =
            "{\"ip\":\"127.0.0.1\",\"port\":8080,\"state\":\"UP\",\"extendInfo\":{\"adWeight\":\"0\","
                + "\"site\":\"unknow\",\"weight\":\"1\"},\"address\":\"127.0.0.1:8080\","
                + "\"abilities\":{\"remoteAbility\":{\"supportRemoteConnection\":false,\"grpcReportEnabled\":true},"
                + "\"configAbility\":{\"supportRemoteMetrics\":false},\"namingAbility\":{\"supportJraft\":false}},"
                + "\"grpcReportEnabled\":false,\"failAccessCnt\":0}";
        Member actualMember = JacksonUtils.toObj(json, Member.class);
        assertEquals(member, actualMember);
        assertEquals(member.getExtendInfo(), actualMember.getExtendInfo());
        assertEquals(member.getAbilities(), actualMember.getAbilities());
        assertEquals(member.isGrpcReportEnabled(), actualMember.isGrpcReportEnabled());
        assertEquals(member.getFailAccessCnt(), actualMember.getFailAccessCnt());
    }
    
    @Test
    public void testDeserializeToNacosMember() {
        String json =
            "{\"ip\":\"127.0.0.1\",\"port\":8080,\"state\":\"UP\",\"extendInfo\":{\"adWeight\":\"0\","
                + "\"site\":\"unknow\",\"weight\":\"1\"},\"address\":\"127.0.0.1:8080\","
                + "\"abilities\":{\"remoteAbility\":{\"supportRemoteConnection\":false,\"grpcReportEnabled\":true},"
                + "\"configAbility\":{\"supportRemoteMetrics\":false},\"namingAbility\":{\"supportJraft\":false}},"
                + "\"grpcReportEnabled\":true,\"failAccessCnt\":1}";
        NacosMember actualMember = JacksonUtils.toObj(json, NacosMember.class);
        assertEquals(member.getIp(), actualMember.getIp());
        assertEquals(member.getPort(), actualMember.getPort());
        assertEquals(member.getState(), actualMember.getState());
        assertEquals(member.getAddress(), actualMember.getAddress());
        assertEquals(member.getExtendInfo(), actualMember.getExtendInfo());
        assertEquals(member.getAbilities(), actualMember.getAbilities());
    }
    
    @Test
    void testCheckValidMember() {
        assertTrue(member.check());
    }
    
    @Test
    void testCheckInvalidMemberMissingIp() {
        Member invalid = Member.builder().ip("").port(8848).state(NodeState.UP).build();
        assertFalse(invalid.check());
    }
    
    @Test
    void testCheckInvalidMemberPort() {
        Member invalid = Member.builder().ip("127.0.0.1").port(-1).state(NodeState.UP).build();
        assertFalse(invalid.check());
    }
    
    @Test
    void testCompareTo() {
        Member other = Member.builder().ip("127.0.0.2").port(8080).state(NodeState.UP).build();
        assertTrue(member.compareTo(other) < 0);
        assertTrue(other.compareTo(member) > 0);
        assertEquals(0, member.compareTo(member));
    }
    
    @Test
    void testEqualsNull() {
        assertFalse(member.equals(null));
    }
    
    @Test
    void testEqualsDifferentClass() {
        assertFalse(member.equals("127.0.0.1:8080"));
        assertFalse(member.equals(new Object()));
    }
    
    @Test
    void testCopy() {
        Member copied = member.copy();
        assertNotNull(copied);
        assertEquals(member.getIp(), copied.getIp());
        assertEquals(member.getPort(), copied.getPort());
        assertEquals(member.getAddress(), copied.getAddress());
    }
    
    @Test
    void testGetExtendValSetExtendValDelExtendVal() {
        member.setExtendVal("key1", "value1");
        assertEquals("value1", member.getExtendVal("key1"));
        member.delExtendVal("key1");
        assertTrue(member.getExtendInfo().get("key1") == null
            || member.getExtendInfo().get("key1") == null);
    }
    
    @Test
    void testBuilderExtendInfo() {
        Map<String, String> extra = new HashMap<>();
        extra.put("custom", "v");
        Member m =
            Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).extendInfo(extra).build();
        assertNotNull(m.getExtendInfo());
        assertEquals("v", m.getExtendInfo().get("custom"));
    }
    
    @Test
    void testFailAccessCnt() {
        member.setFailAccessCnt(5);
        assertEquals(5, member.getFailAccessCnt());
    }
    
    @Test
    void testGrpcReportEnabled() {
        member.setGrpcReportEnabled(true);
        assertTrue(member.isGrpcReportEnabled());
    }
}
