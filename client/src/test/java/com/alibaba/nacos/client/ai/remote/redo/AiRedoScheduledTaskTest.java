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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.redo.data.RedoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRedoScheduledTaskTest {
    
    @Mock
    private AiGrpcClient aiGrpcClient;
    
    @Mock
    private AiGrpcRedoService aiGrpcRedoService;
    
    AiRedoScheduledTask task;
    
    @BeforeEach
    void setUp() {
        task = new AiRedoScheduledTask(aiGrpcRedoService, aiGrpcClient);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void testRunForRedo() throws NacosException {
        Set<RedoData<McpServerEndpoint>> set = new HashSet<>();
        set.add(buildMcpServerEndpointRedoData("test", RedoData.RedoType.REGISTER));
        set.add(buildMcpServerEndpointRedoData("test1", RedoData.RedoType.UNREGISTER));
        set.add(buildMcpServerEndpointRedoData("test2", RedoData.RedoType.REMOVE));
        when(aiGrpcRedoService.findMcpServerEndpointRedoData()).thenReturn(set);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        task.run();
        verify(aiGrpcClient).doRegisterMcpServerEndpoint("test", "127.0.0.1", 8080, "1.0.0");
        verify(aiGrpcClient).doDeregisterMcpServerEndpoint("test1", "127.0.0.1", 8080);
        verify(aiGrpcRedoService).removeMcpServerEndpointForRedo("test2");
    }
    
    @Test
    void testRunForRedoConnectionDisconnect() throws NacosException {
        Set<RedoData<McpServerEndpoint>> set = new HashSet<>();
        set.add(buildMcpServerEndpointRedoData("test", RedoData.RedoType.REGISTER));
        set.add(buildMcpServerEndpointRedoData("test1", RedoData.RedoType.UNREGISTER));
        set.add(buildMcpServerEndpointRedoData("test2", RedoData.RedoType.REMOVE));
        when(aiGrpcRedoService.findMcpServerEndpointRedoData()).thenReturn(set);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        task.run();
        verify(aiGrpcClient, never()).doRegisterMcpServerEndpoint("test", "127.0.0.1", 8080, "1.0.0");
        verify(aiGrpcClient, never()).doDeregisterMcpServerEndpoint("test1", "127.0.0.1", 8080);
        verify(aiGrpcRedoService).removeMcpServerEndpointForRedo("test2");
    }
    
    @Test
    void testRunForRedoWithSingleNacosException() throws NacosException {
        Set<RedoData<McpServerEndpoint>> set = new HashSet<>();
        set.add(buildMcpServerEndpointRedoData("test", RedoData.RedoType.REGISTER));
        set.add(buildMcpServerEndpointRedoData("test1", RedoData.RedoType.UNREGISTER));
        set.add(buildMcpServerEndpointRedoData("test2", RedoData.RedoType.REMOVE));
        when(aiGrpcRedoService.findMcpServerEndpointRedoData()).thenReturn(set);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        doThrow(new NacosException(500, "test")).when(aiGrpcClient)
                .doRegisterMcpServerEndpoint("test", "127.0.0.1", 8080, "1.0.0");
        task.run();
        verify(aiGrpcClient).doRegisterMcpServerEndpoint("test", "127.0.0.1", 8080, "1.0.0");
        verify(aiGrpcClient).doDeregisterMcpServerEndpoint("test1", "127.0.0.1", 8080);
        verify(aiGrpcRedoService).removeMcpServerEndpointForRedo("test2");
    }
    
    @Test
    void testRunForRedoWithOtherException() throws NacosException {
        Set<RedoData<McpServerEndpoint>> set = new HashSet<>();
        set.add(buildMcpServerEndpointRedoData("test", RedoData.RedoType.REGISTER));
        set.add(buildMcpServerEndpointRedoData("test1", RedoData.RedoType.REGISTER));
        set.add(buildMcpServerEndpointRedoData("test2", RedoData.RedoType.REGISTER));
        String fistMcpName = ((McpServerEndpointRedoData) set.iterator().next()).getMcpName();
        when(aiGrpcRedoService.findMcpServerEndpointRedoData()).thenReturn(set);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        doThrow(new RuntimeException("test")).when(aiGrpcClient)
                .doRegisterMcpServerEndpoint(fistMcpName, "127.0.0.1", 8080, "1.0.0");
        task.run();
        verify(aiGrpcClient, times(1)).doRegisterMcpServerEndpoint(anyString(), anyString(), any(int.class),
                anyString());
    }
    
    @Test
    void testRunForAgentEndpointRedoWithSingleRegister() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.REGISTER, false));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify interactions
        AgentEndpoint expectedEndpoint = new AgentEndpoint();
        expectedEndpoint.setAddress("127.0.0.1");
        expectedEndpoint.setPort(8080);
        verify(aiGrpcClient).doRegisterAgentEndpoint("testAgent", expectedEndpoint);
    }
    
    @Test
    void testRunForAgentEndpointRedoWithBatchRegister() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.REGISTER, true));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify interactions
        AgentEndpoint expectedEndpoint = new AgentEndpoint();
        expectedEndpoint.setAddress("127.0.0.1");
        expectedEndpoint.setPort(8080);
        verify(aiGrpcClient).doRegisterAgentEndpoint("testAgent", Collections.singletonList(expectedEndpoint));
    }
    
    @Test
    void testRunForAgentEndpointRedoWithUnregister() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.UNREGISTER, false));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify interactions
        AgentEndpoint expectedEndpoint = new AgentEndpoint();
        expectedEndpoint.setAddress("127.0.0.1");
        expectedEndpoint.setPort(8080);
        verify(aiGrpcClient).doDeregisterAgentEndpoint("testAgent", expectedEndpoint);
    }
    
    @Test
    void testRunForAgentEndpointRedoWithBatchUnregister() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.UNREGISTER, true));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify interactions
        AgentEndpoint expectedEndpoint = new AgentEndpoint();
        expectedEndpoint.setAddress("127.0.0.1");
        expectedEndpoint.setPort(8080);
        verify(aiGrpcClient).doDeregisterAgentEndpoint("testAgent", expectedEndpoint);
    }
    
    @Test
    void testRunForAgentEndpointRedoWithRemove() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.REMOVE, false));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify interactions
        verify(aiGrpcRedoService).removeAgentEndpointForRedo("testAgent");
    }
    
    @Test
    void testRunForAgentEndpointRedoWithClientDisabled() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.REGISTER, false));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(false); // Client is disabled
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify that no interactions happened with the client
        verify(aiGrpcClient, never()).doRegisterAgentEndpoint(anyString(), any(AgentEndpoint.class));
    }
    
    @Test
    void testRunForAgentEndpointRedoWithUnregisterClientDisabled() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.UNREGISTER, false));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(false); // Client is disabled
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        // Run the task
        task.run();
        
        // Verify that no interactions happened with the client
        verify(aiGrpcClient, never()).doRegisterAgentEndpoint(anyString(), any(AgentEndpoint.class));
    }
    
    @Test
    void testRunForAgentEndpointRedoWithNacosException() throws NacosException {
        // Prepare test data
        Set<RedoData<AgentEndpointWrapper>> agentEndpointSet = new HashSet<>();
        agentEndpointSet.add(buildAgentEndpointRedoData("testAgent", RedoData.RedoType.REGISTER, false));
        
        // Mock service methods
        when(aiGrpcRedoService.findAgentEndpointRedoData()).thenReturn(agentEndpointSet);
        when(aiGrpcClient.isEnable()).thenReturn(true);
        when(aiGrpcRedoService.isConnected()).thenReturn(true);
        
        AgentEndpoint expectedEndpoint = new AgentEndpoint();
        expectedEndpoint.setAddress("127.0.0.1");
        expectedEndpoint.setPort(8080);
        doThrow(new NacosException(500, "test")).when(aiGrpcClient)
                .doRegisterAgentEndpoint("testAgent", expectedEndpoint);
        
        // Run the task - should not throw exception
        task.run();
        
        // Verify interactions
        verify(aiGrpcClient).doRegisterAgentEndpoint("testAgent", expectedEndpoint);
    }
    
    private McpServerEndpointRedoData buildMcpServerEndpointRedoData(String mcpName, RedoData.RedoType redoType) {
        McpServerEndpoint mcpServerEndpoint = new McpServerEndpoint("127.0.0.1", 8080, "1.0.0");
        McpServerEndpointRedoData result = new McpServerEndpointRedoData(mcpName);
        result.set(mcpServerEndpoint);
        switch (redoType) {
            case UNREGISTER:
                result.registered();
                result.setUnregistering(true);
                result.setExpectedRegistered(false);
                break;
            case REMOVE:
                result.unregistered();
                result.setExpectedRegistered(false);
                break;
            default:
        }
        return result;
    }
    
    private AgentEndpointRedoData buildAgentEndpointRedoData(String agentName, RedoData.RedoType redoType,
            boolean isBatch) {
        // Prepare test data
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8080);
        
        AgentEndpointWrapper wrapper = isBatch ? AgentEndpointWrapper.wrap(Collections.singletonList(endpoint))
                : AgentEndpointWrapper.wrap(endpoint);
        
        AgentEndpointRedoData agentEndpointRedoData = new AgentEndpointRedoData(agentName, wrapper);
        
        switch (redoType) {
            case UNREGISTER:
                agentEndpointRedoData.registered();
                agentEndpointRedoData.setUnregistering(true);
                agentEndpointRedoData.setExpectedRegistered(false);
                break;
            case REMOVE:
                agentEndpointRedoData.unregistered();
                agentEndpointRedoData.setExpectedRegistered(false);
                break;
            default:
        }
        return agentEndpointRedoData;
    }
}