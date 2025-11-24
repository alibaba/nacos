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

package com.alibaba.nacos.client.ai.remote.redo;

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.redo.data.RedoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AiGrpcRedoServiceTest {
    
    @Mock
    private AiGrpcClient aiGrpcClient;
    
    AiGrpcRedoService redoService;
    
    @BeforeEach
    void setUp() {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        redoService = new AiGrpcRedoService(properties, aiGrpcClient);
    }
    
    @AfterEach
    void tearDown() {
        redoService.shutdown();
    }
    
    @Test
    void cachedMcpServerEndpointForRedo() {
        redoService.cachedMcpServerEndpointForRedo("test", "127.0.0.1", 8080, "1.0.0");
        McpServerEndpoint redoData = redoService.getMcpServerEndpoint("test");
        assertEquals("127.0.0.1", redoData.getAddress());
        assertEquals(8080, redoData.getPort());
        assertEquals("1.0.0", redoData.getVersion());
        assertFalse(redoService.isMcpServerEndpointRegistered("test"));
        
        redoService.mcpServerEndpointRegistered("test");
        assertTrue(redoService.isMcpServerEndpointRegistered("test"));
        
        redoService.mcpServerEndpointDeregister("test");
        assertTrue(redoService.isMcpServerEndpointRegistered("test"));
        
        redoService.mcpServerEndpointDeregistered("test");
        assertFalse(redoService.isMcpServerEndpointRegistered("test"));
        
        redoService.removeMcpServerEndpointForRedo("test");
        redoData = redoService.getMcpServerEndpoint("test");
        assertNull(redoData);
    }
    
    @Test
    void findMcpServerEndpointRedoData() {
        redoService.cachedMcpServerEndpointForRedo("test", "127.0.0.1", 8080, "1.0.0");
        redoService.mcpServerEndpointRegistered("test");
        redoService.cachedMcpServerEndpointForRedo("test2", "127.0.0.1", 8080, "1.0.0");
        Set<RedoData<McpServerEndpoint>> redoDatas = redoService.findMcpServerEndpointRedoData();
        assertEquals(1, redoDatas.size());
        RedoData<McpServerEndpoint> redoData = redoDatas.iterator().next();
        assertInstanceOf(McpServerEndpointRedoData.class, redoData);
        assertEquals("test2", ((McpServerEndpointRedoData) redoData).getMcpName());
    }
    
    @Test
    void cachedAgentEndpointForRedoWithSingleEndpoint() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        AgentEndpointWrapper wrapper = AgentEndpointWrapper.wrap(endpoint);
        
        redoService.cachedAgentEndpointForRedo("testAgent", wrapper);
        assertFalse(redoService.isAgentEndpointRegistered("testAgent"));
        
        redoService.agentEndpointRegistered("testAgent");
        assertTrue(redoService.isAgentEndpointRegistered("testAgent"));
        
        redoService.agentEndpointDeregister("testAgent");
        assertTrue(redoService.isAgentEndpointRegistered("testAgent"));
        
        redoService.agentEndpointDeregistered("testAgent");
        assertFalse(redoService.isAgentEndpointRegistered("testAgent"));
        
        redoService.removeAgentEndpointForRedo("testAgent");
        Set<RedoData<AgentEndpointWrapper>> redoDatas = redoService.findAgentEndpointRedoData();
        assertTrue(redoDatas.isEmpty());
    }
    
    @Test
    void cachedAgentEndpointForRedoWithBatchEndpoint() {
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("127.0.0.1");
        endpoint1.setPort(8080);
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("127.0.0.2");
        endpoint2.setPort(8081);
        
        AgentEndpointWrapper wrapper = AgentEndpointWrapper.wrap(Collections.singletonList(endpoint1));
        
        redoService.cachedAgentEndpointForRedo("testAgent", wrapper);
        assertFalse(redoService.isAgentEndpointRegistered("testAgent"));
        
        Set<RedoData<AgentEndpointWrapper>> redoDatas = redoService.findAgentEndpointRedoData();
        assertEquals(1, redoDatas.size());
        RedoData<AgentEndpointWrapper> redoData = redoDatas.iterator().next();
        assertInstanceOf(AgentEndpointRedoData.class, redoData);
        assertEquals("testAgent", ((AgentEndpointRedoData) redoData).getAgentName());
        
        redoService.agentEndpointRegistered("testAgent");
        assertTrue(redoService.isAgentEndpointRegistered("testAgent"));
        
        redoDatas = redoService.findAgentEndpointRedoData();
        assertTrue(redoDatas.isEmpty());
    }
}