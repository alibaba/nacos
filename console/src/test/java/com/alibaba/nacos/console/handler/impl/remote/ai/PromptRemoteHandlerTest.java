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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.PromptMaintainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptRemoteHandlerTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String VERSION = "0.0.1";
    
    @Mock
    private NacosMaintainerClientHolder clientHolder;
    
    @Mock
    private AiMaintainerService aiMaintainerService;
    
    @Mock
    private PromptMaintainerService promptMaintainerService;
    
    private PromptRemoteHandler handler;
    
    @BeforeEach
    void setUp() {
        when(clientHolder.getAiMaintainerService()).thenReturn(aiMaintainerService);
        when(aiMaintainerService.prompt()).thenReturn(promptMaintainerService);
        handler = new PromptRemoteHandler(clientHolder);
    }
    
    @Test
    void testDeletePrompt() throws NacosException {
        PromptForm form = new PromptForm();
        form.setNamespaceId(NS);
        form.setPromptKey(PROMPT_KEY);
        when(promptMaintainerService.deletePrompt(NS, PROMPT_KEY)).thenReturn(true);
        
        boolean result = handler.deletePrompt(form, "user1", "127.0.0.1");
        
        assertTrue(result);
        verify(promptMaintainerService).deletePrompt(NS, PROMPT_KEY);
    }
    
    @Test
    void testListPrompts() throws NacosException {
        PromptListForm form = new PromptListForm();
        form.setNamespaceId(NS);
        form.setPromptKey(PROMPT_KEY);
        form.setSearch("blur");
        form.setBizTags("tag1");
        form.setPageNo(1);
        form.setPageSize(10);
        Page<PromptMetaSummary> page = new Page<>();
        page.setTotalCount(1);
        when(promptMaintainerService.listPrompts(NS, PROMPT_KEY, "blur", "tag1", 1, 10))
            .thenReturn(page);
        
        Page<PromptMetaSummary> result = handler.listPrompts(form);
        
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    void testListPromptVersions() throws NacosException {
        PromptHistoryForm form = new PromptHistoryForm();
        form.setNamespaceId(NS);
        form.setPromptKey(PROMPT_KEY);
        form.setPageNo(1);
        form.setPageSize(10);
        Page<PromptVersionSummary> page = new Page<>();
        page.setTotalCount(2);
        when(promptMaintainerService.listPromptVersions(NS, PROMPT_KEY, 1, 10))
            .thenReturn(page);
        
        Page<PromptVersionSummary> result = handler.listPromptVersions(form);
        
        assertEquals(2, result.getTotalCount());
    }
    
    @Test
    void testGetPromptGovernanceDetail() throws NacosException {
        PromptMetaInfo info = new PromptMetaInfo();
        info.setPromptKey(PROMPT_KEY);
        when(promptMaintainerService.getPromptGovernanceDetail(NS, PROMPT_KEY))
            .thenReturn(info);
        
        PromptMetaInfo result = handler.getPromptGovernanceDetail(NS, PROMPT_KEY);
        
        assertEquals(PROMPT_KEY, result.getPromptKey());
    }
    
    @Test
    void testGetVersionDetail() throws NacosException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setVersion(VERSION);
        when(promptMaintainerService.getVersionDetail(NS, PROMPT_KEY, VERSION))
            .thenReturn(info);
        
        PromptVersionInfo result = handler.getVersionDetail(NS, PROMPT_KEY, VERSION);
        
        assertEquals(VERSION, result.getVersion());
    }
    
    @Test
    void testDownloadPromptVersion() throws NacosException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setTemplate("Hello");
        when(promptMaintainerService.getVersionDetail(NS, PROMPT_KEY, VERSION))
            .thenReturn(info);
        
        PromptVersionInfo result = handler.downloadPromptVersion(NS, PROMPT_KEY, VERSION);
        
        assertEquals("Hello", result.getTemplate());
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        when(promptMaintainerService.createDraft(eq(NS), eq(PROMPT_KEY), eq("0.0.1"),
            eq("0.0.2"), eq("tpl"), isNull(), eq("msg"), eq("desc"), eq("tags")))
            .thenReturn("0.0.2-draft");
        
        String result = handler.createDraft(NS, PROMPT_KEY, "0.0.1", "0.0.2",
            "tpl", null, "msg", "desc", "tags");
        
        assertEquals("0.0.2-draft", result);
    }
    
    @Test
    void testCreateDraftWithVariables() throws NacosException {
        List<PromptVariable> vars = List.of(new PromptVariable());
        when(promptMaintainerService.createDraft(eq(NS), eq(PROMPT_KEY), isNull(),
            isNull(), eq("tpl"), any(String.class), isNull(), isNull(), isNull()))
            .thenReturn("0.0.1-draft");
        
        String result = handler.createDraft(NS, PROMPT_KEY, null, null,
            "tpl", vars, null, null, null);
        
        assertEquals("0.0.1-draft", result);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        doNothing().when(promptMaintainerService).updateDraft(eq(NS), eq(PROMPT_KEY),
            eq("new tpl"), isNull(), eq("commit"));
        
        handler.updateDraft(NS, PROMPT_KEY, "new tpl", null, "commit");
        
        verify(promptMaintainerService).updateDraft(NS, PROMPT_KEY, "new tpl",
            null, "commit");
    }
    
    @Test
    void testUpdateDraftWithVariables() throws NacosException {
        List<PromptVariable> vars = List.of(new PromptVariable());
        doNothing().when(promptMaintainerService).updateDraft(eq(NS), eq(PROMPT_KEY),
            eq("tpl"), any(String.class), eq("msg"));
        
        handler.updateDraft(NS, PROMPT_KEY, "tpl", vars, "msg");
        
        verify(promptMaintainerService).updateDraft(eq(NS), eq(PROMPT_KEY), eq("tpl"),
            any(String.class), eq("msg"));
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        doNothing().when(promptMaintainerService).deleteDraft(NS, PROMPT_KEY);
        
        handler.deleteDraft(NS, PROMPT_KEY);
        
        verify(promptMaintainerService).deleteDraft(NS, PROMPT_KEY);
    }
    
    @Test
    void testSubmit() throws NacosException {
        when(promptMaintainerService.submit(NS, PROMPT_KEY, VERSION))
            .thenReturn("reviewing");
        
        String result = handler.submit(NS, PROMPT_KEY, VERSION);
        
        assertEquals("reviewing", result);
    }
    
    @Test
    void testPublish() throws NacosException {
        doNothing().when(promptMaintainerService).publish(NS, PROMPT_KEY, VERSION, true);
        
        handler.publish(NS, PROMPT_KEY, VERSION, true);
        
        verify(promptMaintainerService).publish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testForcePublish() throws NacosException {
        doNothing().when(promptMaintainerService).forcePublish(NS, PROMPT_KEY,
            VERSION, true);
        
        handler.forcePublish(NS, PROMPT_KEY, VERSION, false);
        
        verify(promptMaintainerService).forcePublish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        doNothing().when(promptMaintainerService).changeOnlineStatus(NS, PROMPT_KEY,
            VERSION, true);
        
        handler.changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
        
        verify(promptMaintainerService).changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        Map<String, String> labels = Map.of("env", "prod");
        doNothing().when(promptMaintainerService).updateLabels(eq(NS), eq(PROMPT_KEY),
            any(String.class));
        
        handler.updateLabels(NS, PROMPT_KEY, labels);
        
        verify(promptMaintainerService).updateLabels(eq(NS), eq(PROMPT_KEY),
            any(String.class));
    }
    
    @Test
    void testUpdateDescription() throws NacosException {
        doNothing().when(promptMaintainerService).updateDescription(NS, PROMPT_KEY,
            "new desc");
        
        handler.updateDescription(NS, PROMPT_KEY, "new desc");
        
        verify(promptMaintainerService).updateDescription(NS, PROMPT_KEY, "new desc");
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        doNothing().when(promptMaintainerService).updateBizTags(NS, PROMPT_KEY,
            "tag1,tag2");
        
        handler.updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
        
        verify(promptMaintainerService).updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
    }
}
