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
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.PromptProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsolePromptControllerTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String VERSION = "0.0.1";
    
    private static final String BASE_PATH = Constants.Prompt.CONSOLE_PATH;
    
    @Mock
    private PromptProxy promptProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsolePromptController(promptProxy)).build();
    }
    
    @Test
    void testDeletePrompt() throws Exception {
        when(promptProxy.deletePrompt(any(), any(), any())).thenReturn(true);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.delete(BASE_PATH)
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<Boolean> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertTrue(result.getData());
    }
    
    @Test
    void testListPrompts() throws Exception {
        Page<PromptMetaSummary> page = new Page<>();
        page.setTotalCount(1);
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setPromptKey(PROMPT_KEY);
        page.setPageItems(List.of(summary));
        when(promptProxy.listPrompts(any())).thenReturn(page);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/list")
                .param("namespaceId", NS).param("pageNo", "1")
                .param("pageSize", "10"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<Page<PromptMetaSummary>> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListPromptVersions() throws Exception {
        Page<PromptVersionSummary> page = new Page<>();
        page.setTotalCount(2);
        page.setPageItems(List.of(new PromptVersionSummary(), new PromptVersionSummary()));
        when(promptProxy.listPromptVersions(any())).thenReturn(page);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/versions")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("pageNo", "1").param("pageSize", "10"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<Page<PromptVersionSummary>> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(2, result.getData().getTotalCount());
    }
    
    @Test
    void testGetPromptGovernanceDetail() throws Exception {
        PromptMetaInfo info = new PromptMetaInfo();
        info.setPromptKey(PROMPT_KEY);
        when(promptProxy.getPromptGovernanceDetail(eq(NS), eq(PROMPT_KEY)))
            .thenReturn(info);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/governance")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<PromptMetaInfo> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(PROMPT_KEY, result.getData().getPromptKey());
    }
    
    @Test
    void testGetVersionDetail() throws Exception {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setVersion(VERSION);
        when(promptProxy.getVersionDetail(eq(NS), eq(PROMPT_KEY), eq(VERSION)))
            .thenReturn(info);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/version")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<PromptVersionInfo> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(VERSION, result.getData().getVersion());
    }
    
    @Test
    void testDownloadPromptVersionReturnsAttachment() throws Exception {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey(PROMPT_KEY);
        info.setVersion(VERSION);
        info.setTemplate("Hello {{name}}");
        when(promptProxy.downloadPromptVersion(eq(NS), eq(PROMPT_KEY), eq(VERSION)))
            .thenReturn(info);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(BASE_PATH + "/version/download")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().toLowerCase().contains("text/markdown"));
        String disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains(PROMPT_KEY));
        assertTrue(response.getContentAsString().contains("Hello"));
    }
    
    @Test
    void testCreateDraft() throws Exception {
        when(promptProxy.createDraft(eq(NS), eq(PROMPT_KEY), isNull(), isNull(),
            eq("template content"), isNull(), isNull(), isNull(), isNull()))
            .thenReturn("0.0.1-draft");
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/draft")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("template", "template content"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("0.0.1-draft", result.getData());
    }
    
    @Test
    void testUpdateDraft() throws Exception {
        doNothing().when(promptProxy).updateDraft(eq(NS), eq(PROMPT_KEY),
            eq("new template"), isNull(), isNull());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/draft")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("template", "new template"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testDeleteDraft() throws Exception {
        doNothing().when(promptProxy).deleteDraft(eq(NS), eq(PROMPT_KEY));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.delete(BASE_PATH + "/draft")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        Result<String> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testSubmit() throws Exception {
        when(promptProxy.submit(eq(NS), eq(PROMPT_KEY), eq(VERSION)))
            .thenReturn("reviewing");
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/submit")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
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
        doNothing().when(promptProxy).publish(eq(NS), eq(PROMPT_KEY),
            eq(VERSION), eq(true));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/publish")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).publish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testPublishWithoutUpdateLatest() throws Exception {
        doNothing().when(promptProxy).publish(eq(NS), eq(PROMPT_KEY),
            eq(VERSION), eq(true));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/publish")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION)
                .param("updateLatestLabel", "false"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).publish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testForcePublish() throws Exception {
        doNothing().when(promptProxy).forcePublish(eq(NS), eq(PROMPT_KEY),
            eq(VERSION), anyBoolean());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/force-publish")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).forcePublish(eq(NS), eq(PROMPT_KEY), eq(VERSION),
            eq(true));
    }
    
    @Test
    void testOnline() throws Exception {
        doNothing().when(promptProxy).changeOnlineStatus(eq(NS), eq(PROMPT_KEY),
            eq(VERSION), eq(true));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/online")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testOffline() throws Exception {
        doNothing().when(promptProxy).changeOnlineStatus(eq(NS), eq(PROMPT_KEY),
            eq(VERSION), eq(false));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(BASE_PATH + "/offline")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).changeOnlineStatus(NS, PROMPT_KEY, VERSION, false);
    }
    
    @Test
    void testUpdateLabels() throws Exception {
        doNothing().when(promptProxy).updateLabels(eq(NS), eq(PROMPT_KEY), any());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/labels")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("labels", "{\"env\":\"prod\"}"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).updateLabels(eq(NS), eq(PROMPT_KEY), any());
    }
    
    @Test
    void testUpdateDescription() throws Exception {
        doNothing().when(promptProxy).updateDescription(eq(NS), eq(PROMPT_KEY),
            eq("new desc"));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/description")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("description", "new desc"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).updateDescription(NS, PROMPT_KEY, "new desc");
    }
    
    @Test
    void testUpdateBizTags() throws Exception {
        doNothing().when(promptProxy).updateBizTags(eq(NS), eq(PROMPT_KEY),
            eq("tag1,tag2"));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.put(BASE_PATH + "/biz-tags")
                .param("namespaceId", NS).param("promptKey", PROMPT_KEY)
                .param("bizTags", "tag1,tag2"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(promptProxy).updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
    }
    
    @Test
    void testRedraftSuccess() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.Prompt.CONSOLE_PATH + "/redraft")
                .param("namespaceId", NS)
                .param("promptKey", PROMPT_KEY)
                .param("version", VERSION))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("ok"));
        verify(promptProxy).redraft(eq(NS), eq(PROMPT_KEY), eq(VERSION));
    }
}
