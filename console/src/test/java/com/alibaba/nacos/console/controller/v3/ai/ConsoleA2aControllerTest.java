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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.A2aProxy;
import com.alibaba.nacos.core.auth.AuthFilter;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConsoleA2aControllerTest.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleA2aControllerTest {
    
    @Mock
    private A2aProxy a2aProxy;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @InjectMocks
    private AuthFilter authFilter;
    
    private MockMvc mockMvc;
    
    private ConsoleA2aController consoleA2aController;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        consoleA2aController = new ConsoleA2aController(a2aProxy);
        mockMvc = MockMvcBuilders.standaloneSetup(consoleA2aController).addFilter(authFilter).build();
        when(authConfig.isAuthEnabled()).thenReturn(false);
    }
    
    @Test
    void testRegisterAgent() throws Exception {
        String agentCardJson = "{\"name\":\"test-agent\",\"version\":\"1.0.0\",\"protocolVersion\":\"1.0\",\"preferredTransport\":\"http\",\"url\":\"http://localhost:8080\"}";
        
        try (MockedStatic<AgentRequestUtil> mockedUtil = mockStatic(AgentRequestUtil.class)) {
            AgentCard mockAgentCard = new AgentCard();
            mockAgentCard.setName("test-agent");
            mockedUtil.when(() -> AgentRequestUtil.parseAgentCard(any(AgentCardForm.class))).thenReturn(mockAgentCard);
            
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.A2A.CONSOLE_PATH)
                    .param("namespaceId", "test-namespace")
                    .param("agentName", "test-agent")
                    .param("agentCard", agentCardJson)
                    .param("registrationType", "SERVICE");
            
            MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
            String actualValue = response.getContentAsString();
            Result<String> result = JacksonUtils.toObj(actualValue, new TypeReference<>() { });
            
            assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
            assertEquals("ok", result.getData());
        }
    }
    
    @Test
    void testGetAgentCard() throws Exception {
        AgentCardDetailInfo mockDetailInfo = new AgentCardDetailInfo();
        mockDetailInfo.setName("test-agent");
        mockDetailInfo.setVersion("1.0.0");
        
        when(a2aProxy.getAgentCard(any(AgentForm.class))).thenReturn(mockDetailInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.A2A.CONSOLE_PATH)
                .param("namespaceId", "test-namespace")
                .param("agentName", "test-agent")
                .param("version", "1.0.0")
                .param("registrationType", "SERVICE");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<AgentCardDetailInfo> result = JacksonUtils.toObj(actualValue, new TypeReference<>() { });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("test-agent", result.getData().getName());
    }
    
    @Test
    void testUpdateAgentCard() throws Exception {
        String agentCardJson = "{\"name\":\"test-agent\",\"version\":\"1.0.1\",\"protocolVersion\":\"1.0\",\"preferredTransport\":\"http\",\"url\":\"http://localhost:8080\"}";
        
        try (MockedStatic<AgentRequestUtil> mockedUtil = mockStatic(AgentRequestUtil.class)) {
            AgentCard mockAgentCard = new AgentCard();
            mockAgentCard.setName("test-agent");
            mockAgentCard.setVersion("1.0.1");
            mockedUtil.when(() -> AgentRequestUtil.parseAgentCard(any(AgentCardUpdateForm.class))).thenReturn(mockAgentCard);
            
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(Constants.A2A.CONSOLE_PATH)
                    .param("namespaceId", "test-namespace")
                    .param("agentName", "test-agent")
                    .param("agentCard", agentCardJson)
                    .param("registrationType", "SERVICE")
                    .param("latest", "true");
            
            MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
            String actualValue = response.getContentAsString();
            Result<String> result = JacksonUtils.toObj(actualValue, new TypeReference<>() { });
            
            assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
            assertEquals("ok", result.getData());
        }
    }
    
    @Test
    void testDeleteAgent() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.A2A.CONSOLE_PATH)
                .param("namespaceId", "test-namespace")
                .param("agentName", "test-agent")
                .param("version", "1.0.0")
                .param("registrationType", "SERVICE");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(actualValue, new TypeReference<>() { });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testListAgents() throws Exception {
        Page<AgentCardVersionInfo> mockPage = new Page<>();
        AgentCardVersionInfo versionInfo = new AgentCardVersionInfo();
        versionInfo.setName("test-agent");
        versionInfo.setVersion("1.0.0");
        mockPage.setPageItems(Collections.singletonList(versionInfo));
        mockPage.setTotalCount(1);
        
        when(a2aProxy.listAgents(any(AgentListForm.class), any(PageForm.class))).thenReturn(mockPage);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.A2A.CONSOLE_PATH + "/list")
                .param("namespaceId", "test-namespace")
                .param("agentName", "test")
                .param("search", "blur")
                .param("pageNo", "1")
                .param("pageSize", "10");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<Page<AgentCardVersionInfo>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() { });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListAgentVersions() throws Exception {
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        versionDetail.setVersion("1.0.0");
        versionDetail.setLatest(true);
        
        when(a2aProxy.listAgentVersions("test-namespace", "test-agent")).thenReturn(Collections.singletonList(versionDetail));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.A2A.CONSOLE_PATH + "/version/list")
                .param("namespaceId", "test-namespace")
                .param("agentName", "test-agent");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<List<AgentVersionDetail>> result = JacksonUtils.toObj(actualValue, new TypeReference<>() { });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("1.0.0", result.getData().get(0).getVersion());
    }
}