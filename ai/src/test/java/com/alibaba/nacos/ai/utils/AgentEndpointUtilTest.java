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
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEndpointUtilTest {
    
    @Test
    void testTransferToInstance() throws NacosApiException {
        // Given
        AgentEndpoint endpoint = createTestAgentEndpoint();
        
        // When
        Instance instance = AgentEndpointUtil.transferToInstance(endpoint);
        
        // Then
        assertNotNull(instance);
        assertEquals(endpoint.getAddress(), instance.getIp());
        assertEquals(endpoint.getPort(), instance.getPort());
        
        Map<String, String> metadata = instance.getMetadata();
        assertNotNull(metadata);
        assertEquals(endpoint.getPath(), metadata.get(Constants.A2A.AGENT_ENDPOINT_PATH_KEY));
        assertEquals(endpoint.getTransport(), metadata.get(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY));
        assertEquals(String.valueOf(endpoint.isSupportTls()),
                metadata.get(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS));
        assertEquals(endpoint.getProtocol(), metadata.get(Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY));
        assertEquals(endpoint.getQuery(), metadata.get(Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY));
        
        assertDoesNotThrow(instance::validate);
    }
    
    @Test
    void testTransferToInstanceWithEmptyFields() throws NacosApiException {
        // Given
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        endpoint.setProtocol("");
        // Leave other fields as null/empty
        
        // When
        Instance instance = AgentEndpointUtil.transferToInstance(endpoint);
        
        // Then
        assertNotNull(instance);
        assertEquals(endpoint.getAddress(), instance.getIp());
        assertEquals(endpoint.getPort(), instance.getPort());
        
        Map<String, String> metadata = instance.getMetadata();
        assertNotNull(metadata);
        assertEquals("", metadata.get(Constants.A2A.AGENT_ENDPOINT_PATH_KEY));
        assertEquals("", metadata.get(Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY));
        assertEquals("", metadata.get(Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY));
        
        assertDoesNotThrow(instance::validate);
    }
    
    @Test
    void testTransferToInstanceWithGrpcProtocol() throws NacosApiException {
        // Given
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        endpoint.setProtocol("grpc");
        endpoint.setTransport("GRPC");
        
        // When
        Instance instance = AgentEndpointUtil.transferToInstance(endpoint);
        
        // Then
        assertNotNull(instance);
        assertEquals(endpoint.getAddress(), instance.getIp());
        assertEquals(endpoint.getPort(), instance.getPort());
        
        Map<String, String> metadata = instance.getMetadata();
        assertNotNull(metadata);
        assertEquals(endpoint.getProtocol(), metadata.get(Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY));
        assertEquals(endpoint.getTransport(), metadata.get(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY));
        
        assertDoesNotThrow(instance::validate);
    }
    
    @Test
    void testTransferToInstances() throws NacosApiException {
        // Given
        Collection<AgentEndpoint> endpoints = Arrays.asList(createTestAgentEndpoint(),
                createAnotherTestAgentEndpoint());
        
        // When
        List<Instance> instances = AgentEndpointUtil.transferToInstances(endpoints);
        
        // Then
        assertNotNull(instances);
        assertEquals(2, instances.size());
        
        Instance firstInstance = instances.get(0);
        assertEquals("127.0.0.1", firstInstance.getIp());
        assertEquals(8080, firstInstance.getPort());
        
        Instance secondInstance = instances.get(1);
        assertEquals("192.168.1.100", secondInstance.getIp());
        assertEquals(9090, secondInstance.getPort());
        
        // Validate all instances
        for (Instance instance : instances) {
            assertDoesNotThrow(instance::validate);
        }
    }
    
    @Test
    void testTransferToInstanceWithNullEndpoint() {
        // Given
        AgentEndpoint endpoint = null;
        
        // When & Then
        assertThrows(NullPointerException.class, () -> AgentEndpointUtil.transferToInstance(endpoint));
    }
    
    @Test
    void testTransferToInstancesWithEmptyCollection() throws NacosApiException {
        // Given
        Collection<AgentEndpoint> endpoints = Arrays.asList();
        
        // When
        List<Instance> instances = AgentEndpointUtil.transferToInstances(endpoints);
        
        // Then
        assertNotNull(instances);
        assertTrue(instances.isEmpty());
    }
    
    private AgentEndpoint createTestAgentEndpoint() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setTransport("JSONRPC");
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        endpoint.setPath("/agent");
        endpoint.setSupportTls(true);
        endpoint.setVersion("1.0.0");
        endpoint.setProtocol("https");
        endpoint.setQuery("param1=value1&param2=value2");
        return endpoint;
    }
    
    private AgentEndpoint createAnotherTestAgentEndpoint() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setTransport("GRPC");
        endpoint.setAddress("192.168.1.100");
        endpoint.setPort(9090);
        endpoint.setPath("/grpc-agent");
        endpoint.setSupportTls(false);
        endpoint.setVersion("2.0.0");
        endpoint.setProtocol("http");
        endpoint.setQuery("token=abc123");
        return endpoint;
    }
}