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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardUtilTest {
    
    @Test
    void testBuildAgentCardDetailInfo() {
        // Given
        AgentCard agentCard = createTestAgentCard();
        String registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        
        // When
        AgentCardDetailInfo result = AgentCardUtil.buildAgentCardDetailInfo(agentCard, registrationType);
        
        // Then
        assertNotNull(result);
        assertEquals(registrationType, result.getRegistrationType());
        assertAgentCardEquals(agentCard, result);
    }
    
    @Test
    void testBuildAgentCardVersionInfoWithLatest() {
        // Given
        AgentCard agentCard = createTestAgentCard();
        String registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        boolean isLatest = true;
        
        // When
        AgentCardVersionInfo result = AgentCardUtil.buildAgentCardVersionInfo(agentCard, registrationType, isLatest);
        
        // Then
        assertNotNull(result);
        assertEquals(registrationType, result.getRegistrationType());
        assertEquals(agentCard.getVersion(), result.getLatestPublishedVersion());
        assertNotNull(result.getVersionDetails());
        assertEquals(1, result.getVersionDetails().size());
        
        AgentVersionDetail versionDetail = result.getVersionDetails().get(0);
        assertEquals(agentCard.getVersion(), versionDetail.getVersion());
        assertTrue(versionDetail.isLatest());
        assertNotNull(versionDetail.getCreatedAt());
        assertNotNull(versionDetail.getUpdatedAt());
    }
    
    @Test
    void testBuildAgentCardVersionInfoWithoutLatest() {
        // Given
        AgentCard agentCard = createTestAgentCard();
        String registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        boolean isLatest = false;
        
        // When
        AgentCardVersionInfo result = AgentCardUtil.buildAgentCardVersionInfo(agentCard, registrationType, isLatest);
        
        // Then
        assertNotNull(result);
        assertEquals(registrationType, result.getRegistrationType());
        assertNotNull(result.getVersionDetails());
        assertEquals(1, result.getVersionDetails().size());
        
        AgentVersionDetail versionDetail = result.getVersionDetails().get(0);
        assertEquals(agentCard.getVersion(), versionDetail.getVersion());
        assertEquals(isLatest, versionDetail.isLatest());
        assertNotNull(versionDetail.getCreatedAt());
        assertNotNull(versionDetail.getUpdatedAt());
    }
    
    @Test
    void testBuildAgentVersionDetail() {
        // Given
        AgentCard agentCard = createTestAgentCard();
        boolean isLatest = true;
        
        // When
        AgentVersionDetail result = AgentCardUtil.buildAgentVersionDetail(agentCard, isLatest);
        
        // Then
        assertNotNull(result);
        assertEquals(agentCard.getVersion(), result.getVersion());
        assertEquals(isLatest, result.isLatest());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }
    
    @Test
    void testUpdateUpdateTime() {
        // Given
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        versionDetail.setUpdatedAt("2023-01-01T00:00:00Z");
        
        // When
        AgentCardUtil.updateUpdateTime(versionDetail);
        
        // Then
        assertNotNull(versionDetail.getUpdatedAt());
        // We can't assert exact value since it's based on current time, but we can ensure it's not null
    }
    
    @Test
    void testBuildAgentInterfaceWithTlsSupport() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "true");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "JSONRPC");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, "/agent");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("https://127.0.0.1:8080/agent", result.getUrl());
        assertEquals("JSONRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithoutTlsSupport() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "false");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "JSONRPC");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, "/agent");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("http://127.0.0.1:8080/agent", result.getUrl());
        assertEquals("JSONRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithoutPath() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "false");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "JSONRPC");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("http://127.0.0.1:8080", result.getUrl());
        assertEquals("JSONRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithPathWithoutLeadingSlash() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "false");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "JSONRPC");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, "agent");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("http://127.0.0.1:8080/agent", result.getUrl());
        assertEquals("JSONRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithProtocolField() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "false");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "GRPC");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, "/agent");
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY, "grpc");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("grpc://127.0.0.1:8080/agent", result.getUrl());
        assertEquals("GRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithQueryField() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "false");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "JSONRPC");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, "/agent");
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY, "param1=value1&param2=value2");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("http://127.0.0.1:8080/agent?param1=value1&param2=value2", result.getUrl());
        assertEquals("JSONRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithProtocolAndQueryFields() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, "true");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, "JSONRPC");
        metadata.put(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, "/agent");
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY, "https");
        metadata.put(Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY, "token=abc123");
        instance.setMetadata(metadata);
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("https://127.0.0.1:8080/agent?token=abc123", result.getUrl());
        assertEquals("JSONRPC", result.getTransport());
    }
    
    @Test
    void testBuildAgentInterfaceWithEmptyMetadata() {
        // Given
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        instance.setMetadata(Collections.emptyMap());
        
        // When
        AgentInterface result = AgentCardUtil.buildAgentInterface(instance);
        
        // Then
        assertNotNull(result);
        assertEquals("http://127.0.0.1:8080", result.getUrl());
        assertEquals(null, result.getTransport());
    }
    
    @Test
    void testGetCurrentTimeDoesNotThrow() {
        // This test ensures the private method getCurrentTime works without exceptions
        assertDoesNotThrow(() -> {
            // We can't directly test the private method, but we can test methods that use it
            AgentVersionDetail versionDetail = new AgentVersionDetail();
            AgentCardUtil.updateUpdateTime(versionDetail);
            assertNotNull(versionDetail.getUpdatedAt());
        });
    }
    
    private AgentCard createTestAgentCard() {
        AgentCard agentCard = new AgentCard();
        agentCard.setProtocolVersion("1.0");
        agentCard.setName("test-agent");
        agentCard.setDescription("Test Agent");
        agentCard.setVersion("1.0.0");
        agentCard.setIconUrl("http://example.com/icon.png");
        agentCard.setCapabilities(new AgentCapabilities());
        agentCard.setSkills(Collections.emptyList());
        agentCard.setUrl("http://example.com/agent");
        agentCard.setPreferredTransport("JSONRPC");
        agentCard.setAdditionalInterfaces(Collections.emptyList());
        agentCard.setDocumentationUrl("http://example.com/docs");
        agentCard.setDefaultInputModes(Collections.emptyList());
        agentCard.setDefaultOutputModes(Collections.emptyList());
        agentCard.setSupportsAuthenticatedExtendedCard(false);
        return agentCard;
    }
    
    private void assertAgentCardEquals(AgentCard expected, AgentCard actual) {
        assertEquals(expected.getProtocolVersion(), actual.getProtocolVersion());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.getIconUrl(), actual.getIconUrl());
        assertEquals(expected.getUrl(), actual.getUrl());
        assertEquals(expected.getPreferredTransport(), actual.getPreferredTransport());
        assertEquals(expected.getDocumentationUrl(), actual.getDocumentationUrl());
    }
}