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
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AgentSpecAdminController} — scope endpoint.
 *
 * @author nacos
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class AgentSpecAdminControllerTest {
    
    private static final String AGENTSPEC_ADMIN_PATH = Constants.AgentSpecs.ADMIN_PATH;
    
    private AgentSpecAdminController agentSpecAdminController;
    
    private MockMvc mockMvc;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @MockitoBean
    private AgentSpecOperationService agentSpecOperationService;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        agentSpecAdminController = new AgentSpecAdminController(agentSpecOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(agentSpecAdminController).build();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testUpdateScopeSuccess() throws Exception {
        doNothing().when(agentSpecOperationService).updateScope(anyString(), anyString(),
            anyString());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/scope")
                .param("agentSpecName", "test-agentspec").param("scope", "PUBLIC");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).updateScope("public", "test-agentspec", "PUBLIC");
    }
    
    @Test
    void testUpdateScopeMissingAgentSpecName() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/scope")
                .param("scope", "PUBLIC");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "Required parameter 'agentSpecName' type String is not present");
    }
    
    @Test
    void testUpdateScopeMissingScope() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/scope")
                .param("agentSpecName", "test-agentspec");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "Required parameter 'scope' type String is not present");
    }
    
    @Test
    void testUpdateScopeInvalidScope() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/scope")
                .param("agentSpecName", "test-agentspec").param("scope", "INVALID");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "must be PUBLIC or PRIVATE");
    }
    
    @Test
    void testUpdateBizTagsSuccess() throws Exception {
        doNothing().when(agentSpecOperationService).updateBizTags(anyString(), anyString(),
            anyString());
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/biz-tags")
                .param("agentSpecName", "test-agentspec").param("bizTags", "[\"finance\"]");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).updateBizTags("public", "test-agentspec",
            "[\"finance\"]");
    }
    
    @Test
    void testForcePublishSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .forcePublish(eq("public"), eq("test-agentspec"), eq("v1"), eq(true));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/force-publish")
                .param("agentSpecName", "test-agentspec").param("version", "v1")
                .param("updateLatestLabel", "false");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).forcePublish("public", "test-agentspec", "v1", true);
    }
    
    @Test
    void testListAgentSpecsSuccess() throws Exception {
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        AgentSpecSummary item = new AgentSpecSummary();
        item.setName("test-agentspec");
        page.setPageItems(Collections.singletonList(item));
        when(agentSpecOperationService.listAgentSpecs(eq("public"), isNull(), isNull(), isNull(),
            isNull(), isNull(),
            eq(1), eq(10))).thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(AGENTSPEC_ADMIN_PATH + "/list")
                .param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Result<Page<AgentSpecSummary>> result = JacksonUtils.toObj(response.getContentAsString(),
            new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListAgentSpecsWithOwnerFilter() throws Exception {
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        AgentSpecSummary item = new AgentSpecSummary();
        item.setName("test-agentspec");
        page.setPageItems(Collections.singletonList(item));
        when(agentSpecOperationService.listAgentSpecs(eq("public"), isNull(), isNull(), isNull(),
            eq("alice"), isNull(),
            eq(1), eq(10))).thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(AGENTSPEC_ADMIN_PATH + "/list")
                .param("owner", "alice").param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).listAgentSpecs("public", null, null, null, "alice", null,
            1, 10);
    }
    
    @Test
    void testListAgentSpecsWithInvalidScope() throws Throwable {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(AGENTSPEC_ADMIN_PATH + "/list")
                .param("scope", "INVALID").param("pageNo", "1").param("pageSize", "10");
        assertServletException(NacosApiException.class, () -> mockMvc.perform(builder).andReturn(),
            "must be PUBLIC or PRIVATE");
    }
    
    private void assertServletException(Class<? extends Exception> expectedException,
        Executable executable,
        String expectedMessage) throws Throwable {
        try {
            executable.execute();
        } catch (ServletException e) {
            assertInstanceOf(expectedException, e.getCause());
            if (expectedMessage != null) {
                assertNotNull(e.getCause().getMessage());
                assertEquals(true, e.getCause().getMessage().contains(expectedMessage),
                    "Expected message containing '" + expectedMessage + "', but got: "
                        + e.getCause().getMessage());
            }
        }
    }
}
