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

package com.alibaba.nacos.common.paramcheck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for ParamInfo.
 *
 * @author nacos
 */
class ParamInfoTest {
    
    @Test
    @DisplayName("Getter and setter for namespaceShowName should work correctly")
    void testNamespaceShowNameGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getNamespaceShowName());
        paramInfo.setNamespaceShowName("testNamespace");
        assertEquals("testNamespace", paramInfo.getNamespaceShowName());
    }
    
    @Test
    @DisplayName("Getter and setter for namespaceId should work correctly")
    void testNamespaceIdGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getNamespaceId());
        paramInfo.setNamespaceId("ns-123");
        assertEquals("ns-123", paramInfo.getNamespaceId());
    }
    
    @Test
    @DisplayName("Getter and setter for dataId should work correctly")
    void testDataIdGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getDataId());
        paramInfo.setDataId("data-123");
        assertEquals("data-123", paramInfo.getDataId());
    }
    
    @Test
    @DisplayName("Getter and setter for serviceName should work correctly")
    void testServiceNameGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getServiceName());
        paramInfo.setServiceName("service-123");
        assertEquals("service-123", paramInfo.getServiceName());
    }
    
    @Test
    @DisplayName("Getter and setter for group should work correctly")
    void testGroupGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getGroup());
        paramInfo.setGroup("group-123");
        assertEquals("group-123", paramInfo.getGroup());
    }
    
    @Test
    @DisplayName("Getter and setter for cluster should work correctly")
    void testClusterGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getCluster());
        paramInfo.setCluster("cluster-123");
        assertEquals("cluster-123", paramInfo.getCluster());
    }
    
    @Test
    @DisplayName("Getter and setter for clusters should work correctly")
    void testClustersGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getClusters());
        paramInfo.setClusters("cluster1,cluster2");
        assertEquals("cluster1,cluster2", paramInfo.getClusters());
    }
    
    @Test
    @DisplayName("Getter and setter for ip should work correctly")
    void testIpGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getIp());
        paramInfo.setIp("192.168.1.1");
        assertEquals("192.168.1.1", paramInfo.getIp());
    }
    
    @Test
    @DisplayName("Getter and setter for port should work correctly")
    void testPortGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getPort());
        paramInfo.setPort("8848");
        assertEquals("8848", paramInfo.getPort());
    }
    
    @Test
    @DisplayName("Getter and setter for metadata should work correctly")
    void testMetadataGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getMetadata());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        paramInfo.setMetadata(metadata);
        assertNotNull(paramInfo.getMetadata());
        assertEquals("value1", paramInfo.getMetadata().get("key1"));
    }
    
    @Test
    @DisplayName("Getter and setter for mcpName should work correctly")
    void testMcpNameGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getMcpName());
        paramInfo.setMcpName("mcp-123");
        assertEquals("mcp-123", paramInfo.getMcpName());
    }
    
    @Test
    @DisplayName("Getter and setter for mcpId should work correctly")
    void testMcpIdGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getMcpId());
        paramInfo.setMcpId("mcp-id-123");
        assertEquals("mcp-id-123", paramInfo.getMcpId());
    }
    
    @Test
    @DisplayName("Getter and setter for agentName should work correctly")
    void testAgentNameGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getAgentName());
        paramInfo.setAgentName("agent-123");
        assertEquals("agent-123", paramInfo.getAgentName());
    }
    
    @Test
    @DisplayName("Getter and setter for skillName should work correctly")
    void testSkillNameGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getSkillName());
        paramInfo.setSkillName("skill-123");
        assertEquals("skill-123", paramInfo.getSkillName());
    }
    
    @Test
    @DisplayName("Getter and setter for skillSearchName should work correctly")
    void testSkillSearchNameGetterSetter() {
        ParamInfo paramInfo = new ParamInfo();
        assertNull(paramInfo.getSkillSearchName());
        paramInfo.setSkillSearchName("skill-");
        assertEquals("skill-", paramInfo.getSkillSearchName());
    }
}
