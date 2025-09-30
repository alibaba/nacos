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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentHttpParamExtractorTest {
    
    @Mock
    HttpServletRequest request;
    
    AgentHttpParamExtractor httpParamExtractor;
    
    @BeforeEach
    void setUp() {
        httpParamExtractor = new AgentHttpParamExtractor();
    }
    
    @Test
    void extractParamWithNamespaceIdAndAgentName() throws NacosException {
        String agentName = "testAgent";
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("agentName")).thenReturn(agentName);
        when(request.getParameterMap()).thenReturn(
                Map.of("namespaceId", new String[] {"testNs"}, "agentName", new String[] {agentName}));
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("testNs", actual.get(0).getNamespaceId());
        assertEquals(agentName, actual.get(0).getAgentName());
    }
    
    @Test
    void extractParamWithAgentCard() throws NacosException {
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgentFromCard");
        agentCard.setDescription("Test agent card");
        
        AgentCapabilities capabilities = new AgentCapabilities();
        agentCard.setCapabilities(capabilities);
        
        String agentCardJson = JacksonUtils.toJson(agentCard);
        
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("agentName")).thenReturn("shouldBeOverridden");
        when(request.getParameter("agentCard")).thenReturn(agentCardJson);
        when(request.getParameterMap()).thenReturn(
                Map.of("namespaceId", new String[] {"testNs"}, "agentName", new String[] {"shouldBeOverridden"},
                        "agentCard", new String[] {agentCardJson}));
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("testNs", actual.get(0).getNamespaceId());
        assertEquals("testAgentFromCard", actual.get(0).getAgentName());
    }
    
    @Test
    void extractParamWithInvalidAgentCardJson() throws NacosException {
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("agentName")).thenReturn("testAgent");
        when(request.getParameter("agentCard")).thenReturn("{invalidJson");
        when(request.getParameterMap()).thenReturn(
                Map.of("namespaceId", new String[] {"testNs"}, "agentName", new String[] {"testAgent"}, "agentCard",
                        new String[] {"{invalidJson"}));
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("testNs", actual.get(0).getNamespaceId());
        assertEquals("", actual.get(0).getAgentName());
    }
    
    @Test
    void extractParamWithEmptyParameters() throws NacosException {
        when(request.getParameterMap()).thenReturn(new java.util.HashMap<>());
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertTrue(actual.get(0).getNamespaceId() == null || actual.get(0).getNamespaceId().isEmpty());
        assertTrue(actual.get(0).getAgentName() == null || actual.get(0).getAgentName().isEmpty());
    }
}