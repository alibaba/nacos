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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
    void testGetAgentSpecSuccess() throws Exception {
        AgentSpecMeta detail = new AgentSpecMeta();
        detail.setName("test-agentspec");
        detail.setEnable(true);
        when(agentSpecOperationService.getAgentSpecDetail(eq("public"), eq("test-agentspec")))
            .thenReturn(detail);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(AGENTSPEC_ADMIN_PATH)
            .param("agentSpecName", "test-agentspec");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<AgentSpecMeta> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("test-agentspec", result.getData().getName());
    }
    
    @Test
    void testGetAgentSpecVersionSuccess() throws Exception {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("test-agentspec");
        when(agentSpecOperationService.getAgentSpecVersionDetail(eq("public"),
            eq("test-agentspec"), eq("v1"))).thenReturn(agentSpec);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(AGENTSPEC_ADMIN_PATH + "/version")
                .param("agentSpecName", "test-agentspec").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<AgentSpec> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("test-agentspec", result.getData().getName());
    }
    
    @Test
    void testGetAgentSpecVersionMetaSuccess() throws Exception {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("test-agentspec");
        when(agentSpecOperationService.getAgentSpecVersionMeta(eq("public"),
            eq("test-agentspec"), eq("v1"))).thenReturn(agentSpec);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(AGENTSPEC_ADMIN_PATH + "/version/meta")
                .param("agentSpecName", "test-agentspec").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<AgentSpec> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("test-agentspec", result.getData().getName());
    }
    
    @Test
    void testDeleteAgentSpecSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .deleteAgentSpec(eq("public"), eq("test-agentspec"));
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(AGENTSPEC_ADMIN_PATH)
            .param("agentSpecName", "test-agentspec");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).deleteAgentSpec("public", "test-agentspec");
    }
    
    @Test
    void testCreateDraftSuccess() throws Exception {
        when(agentSpecOperationService.createDraft(eq("public"), eq("test-agentspec"), isNull(),
            eq("v1"))).thenReturn("v1");
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/draft")
                .param("agentSpecName", "test-agentspec").param("targetVersion", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("v1", result.getData());
    }
    
    @Test
    void testUpdateDraftSuccess() throws Exception {
        doNothing().when(agentSpecOperationService).updateDraft(eq("public"), any(AgentSpec.class));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/draft")
                .param("agentSpecCard", "{\"name\":\"test-agentspec\",\"content\":\"{}\"}");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).updateDraft(eq("public"), any(AgentSpec.class));
    }
    
    @Test
    void testDeleteDraftSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .deleteDraft(eq("public"), eq("test-agentspec"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(AGENTSPEC_ADMIN_PATH + "/draft")
                .param("agentSpecName", "test-agentspec");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).deleteDraft("public", "test-agentspec");
    }
    
    @Test
    void testSubmitSuccess() throws Exception {
        when(agentSpecOperationService.submit(eq("public"), eq("test-agentspec"), eq("v1")))
            .thenReturn("pipeline-123");
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/submit")
                .param("agentSpecName", "test-agentspec").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("pipeline-123", result.getData());
    }
    
    @Test
    void testPublishSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .publish(eq("public"), eq("test-agentspec"), eq("v1"), eq(true));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/publish")
                .param("agentSpecName", "test-agentspec").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).publish("public", "test-agentspec", "v1", true);
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
    void testRedraftSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .redraft(eq("public"), eq("test-agentspec"), eq("v1"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/redraft")
                .param("agentSpecName", "test-agentspec").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).redraft("public", "test-agentspec", "v1");
    }
    
    @Test
    void testUpdateLabelsSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .updateLabels(eq("public"), eq("test-agentspec"), any(Map.class));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(AGENTSPEC_ADMIN_PATH + "/labels")
                .param("agentSpecName", "test-agentspec")
                .param("labels", "{\"stable\":\"v1\"}");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).updateLabels(eq("public"), eq("test-agentspec"),
            any(Map.class));
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
    void testOnlineSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .changeOnlineStatus(anyString(), anyString(), anyString(), anyString(), eq(true));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/online")
                .param("agentSpecName", "test-agentspec").param("scope", "version")
                .param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).changeOnlineStatus("public", "test-agentspec",
            "version", "v1", true);
    }
    
    @Test
    void testOfflineSuccess() throws Exception {
        doNothing().when(agentSpecOperationService)
            .changeOnlineStatus(anyString(), anyString(), anyString(), anyString(), eq(false));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(AGENTSPEC_ADMIN_PATH + "/offline")
                .param("agentSpecName", "test-agentspec").param("scope", "version")
                .param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecOperationService).changeOnlineStatus("public", "test-agentspec",
            "version", "v1", false);
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
