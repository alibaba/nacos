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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.AgentSpecProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleAgentSpecControllerTest {
    
    private static final String NS = "public";
    
    private static final String AGENT_SPEC_NAME = "test-agentspec";
    
    private static final String VERSION = "v1";
    
    private static final String BASE_PATH = Constants.AgentSpecs.CONSOLE_PATH;
    
    @Mock
    private AgentSpecProxy agentSpecProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsoleAgentSpecController(agentSpecProxy)).build();
    }
    
    @Test
    void testGetAgentSpec() throws Exception {
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setName(AGENT_SPEC_NAME);
        when(agentSpecProxy.getAgentSpec(any())).thenReturn(meta);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH)
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<AgentSpecMeta> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(AGENT_SPEC_NAME, result.getData().getName());
    }
    
    @Test
    void testGetAgentSpecVersion() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setName(AGENT_SPEC_NAME);
        when(agentSpecProxy.getAgentSpecVersion(any())).thenReturn(spec);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/version")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<AgentSpec> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(AGENT_SPEC_NAME, result.getData().getName());
    }
    
    @Test
    void testDeleteAgentSpec() throws Exception {
        doNothing().when(agentSpecProxy).deleteAgentSpec(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.delete(BASE_PATH)
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testRedraftSuccess() throws Exception {
        doNothing().when(agentSpecProxy).redraft(any(AgentSpecPublishForm.class));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(
            Constants.AgentSpecs.CONSOLE_PATH + "/redraft").param("namespaceId", "test-ns")
            .param("agentSpecName", "test-agentspec").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String content = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(content, new TypeReference<>() {
        });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(agentSpecProxy).redraft(any(AgentSpecPublishForm.class));
    }
    
    @Test
    void testListAgentSpecsSuccess() throws Exception {
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        AgentSpecSummary item = new AgentSpecSummary();
        item.setName(AGENT_SPEC_NAME);
        page.setPageItems(List.of(item));
        when(agentSpecProxy.listAgentSpecs(any(), any(), any())).thenReturn(page);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/list")
                .param("pageNo", "1").param("pageSize", "10"))
            .andReturn().getResponse();
        
        Result<Page<AgentSpecSummary>> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListAgentSpecsWithScopeFilter() throws Exception {
        Page<AgentSpecSummary> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(Collections.emptyList());
        when(agentSpecProxy.listAgentSpecs(any(), any(), any())).thenReturn(page);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/list")
                .param("scope", "PUBLIC").param("pageNo", "1")
                .param("pageSize", "10"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).listAgentSpecs(any(), any(), any());
    }
    
    @Test
    void testUploadAgentSpec() throws Exception {
        when(agentSpecProxy.uploadAgentSpecFromZip(anyString(), any(byte[].class),
            anyBoolean())).thenReturn(AGENT_SPEC_NAME);
        
        MockMultipartFile file = new MockMultipartFile("file", "agentspec.zip",
            "application/zip", new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.multipart(BASE_PATH + "/upload").file(file)
                .param("namespaceId", NS).param("overwrite", "false"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(AGENT_SPEC_NAME, result.getData());
    }
    
    @Test
    void testCreateDraft() throws Exception {
        when(agentSpecProxy.createDraft(any())).thenReturn("v1-draft");
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/draft")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("v1-draft", result.getData());
    }
    
    @Test
    void testUpdateDraft() throws Exception {
        doNothing().when(agentSpecProxy).updateDraft(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/draft")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION)
                .param("agentSpecCard", "{\"name\":\"test-agentspec\"}"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testDeleteDraft() throws Exception {
        doNothing().when(agentSpecProxy).deleteDraft(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.delete(BASE_PATH + "/draft")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testSubmit() throws Exception {
        when(agentSpecProxy.submit(any())).thenReturn("reviewing");
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/submit")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("reviewing", result.getData());
    }
    
    @Test
    void testPublish() throws Exception {
        doNothing().when(agentSpecProxy).publish(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/publish")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).publish(any());
    }
    
    @Test
    void testForcePublishSuccess() throws Exception {
        doNothing().when(agentSpecProxy).forcePublish(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/force-publish")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testUpdateLabels() throws Exception {
        doNothing().when(agentSpecProxy).updateLabels(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/labels")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("labels", "{\"env\":\"prod\"}"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).updateLabels(any());
    }
    
    @Test
    void testUpdateBizTags() throws Exception {
        doNothing().when(agentSpecProxy).updateBizTags(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/biz-tags")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("bizTags", "tag1,tag2"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).updateBizTags(any());
    }
    
    @Test
    void testOnline() throws Exception {
        doNothing().when(agentSpecProxy).online(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/online")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).online(any());
    }
    
    @Test
    void testOffline() throws Exception {
        doNothing().when(agentSpecProxy).offline(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/offline")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).offline(any());
    }
    
    @Test
    void testUpdateScope() throws Exception {
        doNothing().when(agentSpecProxy).updateScope(any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/scope")
                .param("namespaceId", NS)
                .param("agentSpecName", AGENT_SPEC_NAME)
                .param("scope", "PUBLIC"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(agentSpecProxy).updateScope(any());
    }
}
