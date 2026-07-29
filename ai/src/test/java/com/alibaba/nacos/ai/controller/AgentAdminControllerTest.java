/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentAdminControllerTest {
    
    private static final String PATH = Constants.Agent.ADMIN_PATH;
    
    @Mock
    private AgentOperationService operationService;
    
    @Mock
    private AgentRuntimeRegistryService runtimeRegistryService;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new AgentAdminController(operationService, runtimeRegistryService)).build();
    }
    
    @Test
    void testAgentDefinitionAndReadRoutes() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(PATH).param("agentName", "Demo Agent"))
            .andExpect(status().isOk());
        verify(operationService).getOverview("public", "Demo Agent");
        
        mockMvc.perform(MockMvcRequestBuilders.put(PATH)
            .param("agentName", "Demo Agent").param("status", "disable"))
            .andExpect(status().isOk());
        verify(operationService)
            .updateAgent(argThat(agent -> "public".equals(agent.getNamespaceId())
                && "Demo Agent".equals(agent.getAgentName())
                && "disable".equals(agent.getStatus()) && agent.getOwner() == null
                && agent.getScope() == null));
        
        mockMvc.perform(MockMvcRequestBuilders.delete(PATH).param("agentName", "Demo Agent"))
            .andExpect(status().isOk());
        verify(operationService).deleteAgent("public", "Demo Agent");
        
        mockMvc.perform(MockMvcRequestBuilders.get(PATH + "/list")
            .param("agentName", "Demo").param("bizTag", "assistant")
            .param("scope", "private").param("owner", "alice")
            .param("orderBy", "download_count").param("pageNo", "2").param("pageSize", "20"))
            .andExpect(status().isOk());
        verify(operationService).listAgents("public", "Demo", "assistant", "PRIVATE", "alice",
            "download_count", 2, 20);
        
        mockMvc.perform(MockMvcRequestBuilders.get(PATH + "/versions")
            .param("agentName", "Demo Agent").param("status", "draft")
            .param("pageNo", "2").param("pageSize", "20")).andExpect(status().isOk());
        verify(operationService).listVersions("public", "Demo Agent", "draft", 2, 20);
        
        mockMvc.perform(MockMvcRequestBuilders.get(PATH + "/version")
            .param("agentName", "Demo Agent").param("version", "1.0.0"))
            .andExpect(status().isOk());
        verify(operationService).getVersion("public", "Demo Agent", "1.0.0");
        
        mockMvc.perform(MockMvcRequestBuilders.get(PATH + "/runtime-endpoints")
            .param("agentName", "Demo Agent").param("protocol", "a2a"))
            .andExpect(status().isOk());
        verify(runtimeRegistryService).getRuntimeEndpointSnapshot("public", "Demo Agent", "a2a",
            null);
    }
    
    @Test
    void testDraftRoutes() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(PATH + "/draft")
            .param("namespaceId", "tenant-a").param("agentName", "Demo Agent")
            .param("version", "2.0.0")
            .param("displayName", "Demo")
            .param("provider", "{\"name\":\"Nacos\"}")
            .param("tags", "[\"assistant\",\"demo\"]")
            .param("extensions", "{\"channel\":\"internal\"}")
            .param("callInterfaces", "[]").param("author", "alice")
            .param("changeDescription", "draft")).andExpect(status().isOk());
        verify(operationService).createDraft(eq("tenant-a"),
            argThat(request -> "Demo Agent".equals(request.getAgentName())
                && "2.0.0".equals(request.getVersion())
                && "Demo".equals(request.getDisplayName())
                && "Nacos".equals(request.getProvider().getName())
                && request.getTags().size() == 2
                && "internal".equals(request.getExtensions().get("channel"))
                && "alice".equals(request.getAuthor())
                && request.getBasedOnVersion() == null));
        
        mockMvc.perform(MockMvcRequestBuilders.put(PATH + "/draft")
            .param("agentName", "Demo Agent").param("version", "2.0.0")
            .param("callInterfaces", "[]").param("changeDescription", "updated"))
            .andExpect(status().isOk());
        verify(operationService).updateDraft("public", "Demo Agent", "2.0.0",
            java.util.Collections.emptyList(), "updated");
        
        mockMvc.perform(MockMvcRequestBuilders.delete(PATH + "/draft")
            .param("agentName", "Demo Agent").param("version", "2.0.0"))
            .andExpect(status().isOk());
        verify(operationService).deleteDraft("public", "Demo Agent", "2.0.0");
    }
    
    @Test
    void testLifecycleRoutes() throws Exception {
        performVersionCommand("/submit");
        verify(operationService).submit("public", "Demo Agent", "1.0.0");
        
        performVersionCommand("/publish");
        verify(operationService).publish("public", "Demo Agent", "1.0.0");
        
        performVersionCommand("/force-publish");
        verify(operationService).forcePublish("public", "Demo Agent", "1.0.0");
        
        performVersionCommand("/redraft");
        verify(operationService).redraft("public", "Demo Agent", "1.0.0");
        
        performVersionCommand("/online");
        verify(operationService).online("public", "Demo Agent", "1.0.0");
        
        performVersionCommand("/offline");
        verify(operationService).offline("public", "Demo Agent", "1.0.0");
    }
    
    @Test
    void testLabelsRoute() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put(PATH + "/labels")
            .param("agentName", "Demo Agent")
            .param("labels", "{\"stable\":\"1.0.0\"}")).andExpect(status().isOk());
        
        verify(operationService).updateLabels(eq("public"), eq("Demo Agent"),
            eq(java.util.Collections.singletonMap("stable", "1.0.0")));
    }
    
    private void performVersionCommand(String relativePath) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(PATH + relativePath)
            .param("agentName", "Demo Agent").param("version", "1.0.0"))
            .andExpect(status().isOk());
    }
}
