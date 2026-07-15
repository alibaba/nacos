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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.service.prompt.PromptOperationService;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptInnerHandlerTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String VERSION = "0.0.1";
    
    @Mock
    private PromptOperationService promptOperationService;
    
    private PromptInnerHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new PromptInnerHandler(promptOperationService);
    }
    
    @Test
    void testDeletePrompt() throws NacosException {
        PromptForm form = new PromptForm();
        form.setNamespaceId(NS);
        form.setPromptKey(PROMPT_KEY);
        doNothing().when(promptOperationService).deletePrompt(NS, PROMPT_KEY);
        
        boolean result = handler.deletePrompt(form, "user1", "127.0.0.1");
        
        assertTrue(result);
        verify(promptOperationService).deletePrompt(NS, PROMPT_KEY);
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
        when(promptOperationService.listPrompts(NS, PROMPT_KEY, "blur", "tag1", 1, 10))
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
        when(promptOperationService.listPromptVersions(NS, PROMPT_KEY, 1, 10))
            .thenReturn(page);
        
        Page<PromptVersionSummary> result = handler.listPromptVersions(form);
        
        assertEquals(2, result.getTotalCount());
    }
    
    @Test
    void testGetPromptGovernanceDetail() throws NacosException {
        PromptMetaInfo info = new PromptMetaInfo();
        info.setPromptKey(PROMPT_KEY);
        when(promptOperationService.getPromptDetail(NS, PROMPT_KEY)).thenReturn(info);
        
        PromptMetaInfo result = handler.getPromptGovernanceDetail(NS, PROMPT_KEY);
        
        assertEquals(PROMPT_KEY, result.getPromptKey());
    }
    
    @Test
    void testGetVersionDetail() throws NacosException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setVersion(VERSION);
        when(promptOperationService.getPromptVersionDetail(NS, PROMPT_KEY, VERSION))
            .thenReturn(info);
        
        PromptVersionInfo result = handler.getVersionDetail(NS, PROMPT_KEY, VERSION);
        
        assertEquals(VERSION, result.getVersion());
    }
    
    @Test
    void testDownloadPromptVersion() throws NacosException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setTemplate("Hello");
        when(promptOperationService.downloadPromptVersion(NS, PROMPT_KEY, VERSION))
            .thenReturn(info);
        
        PromptVersionInfo result = handler.downloadPromptVersion(NS, PROMPT_KEY, VERSION);
        
        assertEquals("Hello", result.getTemplate());
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        List<PromptVariable> vars = Collections.emptyList();
        when(promptOperationService.createDraft(NS, PROMPT_KEY, "0.0.1", "0.0.2",
            "tpl", vars, "msg", "desc", "tags")).thenReturn("0.0.2-draft");
        
        String result = handler.createDraft(NS, PROMPT_KEY, "0.0.1", "0.0.2",
            "tpl", vars, "msg", "desc", "tags");
        
        assertEquals("0.0.2-draft", result);
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        List<PromptVariable> vars = Collections.emptyList();
        doNothing().when(promptOperationService).updateDraft(NS, PROMPT_KEY,
            "new tpl", vars, "commit");
        
        handler.updateDraft(NS, PROMPT_KEY, "new tpl", vars, "commit");
        
        verify(promptOperationService).updateDraft(NS, PROMPT_KEY, "new tpl",
            vars, "commit");
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        doNothing().when(promptOperationService).deleteDraft(NS, PROMPT_KEY);
        
        handler.deleteDraft(NS, PROMPT_KEY);
        
        verify(promptOperationService).deleteDraft(NS, PROMPT_KEY);
    }
    
    @Test
    void testSubmit() throws NacosException {
        when(promptOperationService.submit(NS, PROMPT_KEY, VERSION))
            .thenReturn("reviewing");
        
        String result = handler.submit(NS, PROMPT_KEY, VERSION);
        
        assertEquals("reviewing", result);
    }
    
    @Test
    void testPublish() throws NacosException {
        doNothing().when(promptOperationService).publish(NS, PROMPT_KEY, VERSION, true);
        
        handler.publish(NS, PROMPT_KEY, VERSION, true);
        
        verify(promptOperationService).publish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testForcePublish() throws NacosException {
        doNothing().when(promptOperationService).forcePublish(NS, PROMPT_KEY,
            VERSION, true);
        
        handler.forcePublish(NS, PROMPT_KEY, VERSION, false);
        
        verify(promptOperationService).forcePublish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        doNothing().when(promptOperationService).changeOnlineStatus(NS, PROMPT_KEY,
            VERSION, true);
        
        handler.changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
        
        verify(promptOperationService).changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        Map<String, String> labels = Map.of("env", "prod");
        doNothing().when(promptOperationService).updateLabels(NS, PROMPT_KEY, labels);
        
        handler.updateLabels(NS, PROMPT_KEY, labels);
        
        verify(promptOperationService).updateLabels(NS, PROMPT_KEY, labels);
    }
    
    @Test
    void testUpdateDescription() throws NacosException {
        doNothing().when(promptOperationService).updateDescription(NS, PROMPT_KEY,
            "new desc");
        
        handler.updateDescription(NS, PROMPT_KEY, "new desc");
        
        verify(promptOperationService).updateDescription(NS, PROMPT_KEY, "new desc");
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        doNothing().when(promptOperationService).updateBizTags(NS, PROMPT_KEY,
            "tag1,tag2");
        
        handler.updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
        
        verify(promptOperationService).updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
    }
}
