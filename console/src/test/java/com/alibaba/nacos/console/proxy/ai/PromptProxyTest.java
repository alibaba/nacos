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

package com.alibaba.nacos.console.proxy.ai;

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
import com.alibaba.nacos.console.handler.ai.PromptHandler;
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
class PromptProxyTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String VERSION = "0.0.1";
    
    @Mock
    private PromptHandler promptHandler;
    
    private PromptProxy promptProxy;
    
    @BeforeEach
    void setUp() {
        promptProxy = new PromptProxy(promptHandler);
    }
    
    @Test
    void testDeletePrompt() throws NacosException {
        PromptForm form = new PromptForm();
        form.setNamespaceId(NS);
        form.setPromptKey(PROMPT_KEY);
        when(promptHandler.deletePrompt(form, "user1", "127.0.0.1")).thenReturn(true);
        
        boolean result = promptProxy.deletePrompt(form, "user1", "127.0.0.1");
        
        assertTrue(result);
        verify(promptHandler).deletePrompt(form, "user1", "127.0.0.1");
    }
    
    @Test
    void testListPrompts() throws NacosException {
        PromptListForm form = new PromptListForm();
        form.setNamespaceId(NS);
        Page<PromptMetaSummary> page = new Page<>();
        page.setTotalCount(1);
        when(promptHandler.listPrompts(form)).thenReturn(page);
        
        Page<PromptMetaSummary> result = promptProxy.listPrompts(form);
        
        assertEquals(1, result.getTotalCount());
        verify(promptHandler).listPrompts(form);
    }
    
    @Test
    void testListPromptVersions() throws NacosException {
        PromptHistoryForm form = new PromptHistoryForm();
        form.setNamespaceId(NS);
        form.setPromptKey(PROMPT_KEY);
        Page<PromptVersionSummary> page = new Page<>();
        page.setTotalCount(3);
        when(promptHandler.listPromptVersions(form)).thenReturn(page);
        
        Page<PromptVersionSummary> result = promptProxy.listPromptVersions(form);
        
        assertEquals(3, result.getTotalCount());
        verify(promptHandler).listPromptVersions(form);
    }
    
    @Test
    void testGetPromptGovernanceDetail() throws NacosException {
        PromptMetaInfo info = new PromptMetaInfo();
        info.setPromptKey(PROMPT_KEY);
        when(promptHandler.getPromptGovernanceDetail(NS, PROMPT_KEY)).thenReturn(info);
        
        PromptMetaInfo result = promptProxy.getPromptGovernanceDetail(NS, PROMPT_KEY);
        
        assertEquals(PROMPT_KEY, result.getPromptKey());
        verify(promptHandler).getPromptGovernanceDetail(NS, PROMPT_KEY);
    }
    
    @Test
    void testGetVersionDetail() throws NacosException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setVersion(VERSION);
        when(promptHandler.getVersionDetail(NS, PROMPT_KEY, VERSION)).thenReturn(info);
        
        PromptVersionInfo result = promptProxy.getVersionDetail(NS, PROMPT_KEY, VERSION);
        
        assertEquals(VERSION, result.getVersion());
        verify(promptHandler).getVersionDetail(NS, PROMPT_KEY, VERSION);
    }
    
    @Test
    void testDownloadPromptVersion() throws NacosException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setTemplate("Hello {{name}}");
        when(promptHandler.downloadPromptVersion(NS, PROMPT_KEY, VERSION)).thenReturn(info);
        
        PromptVersionInfo result = promptProxy.downloadPromptVersion(NS, PROMPT_KEY, VERSION);
        
        assertEquals("Hello {{name}}", result.getTemplate());
        verify(promptHandler).downloadPromptVersion(NS, PROMPT_KEY, VERSION);
    }
    
    @Test
    void testCreateDraft() throws NacosException {
        List<PromptVariable> vars = Collections.emptyList();
        when(promptHandler.createDraft(NS, PROMPT_KEY, "0.0.1", "0.0.2",
            "template", vars, "msg", "desc", "tags")).thenReturn("0.0.2-draft");
        
        String result = promptProxy.createDraft(NS, PROMPT_KEY, "0.0.1", "0.0.2",
            "template", vars, "msg", "desc", "tags");
        
        assertEquals("0.0.2-draft", result);
        verify(promptHandler).createDraft(NS, PROMPT_KEY, "0.0.1", "0.0.2",
            "template", vars, "msg", "desc", "tags");
    }
    
    @Test
    void testUpdateDraft() throws NacosException {
        List<PromptVariable> vars = Collections.emptyList();
        doNothing().when(promptHandler).updateDraft(NS, PROMPT_KEY, "new template",
            vars, "commit");
        
        promptProxy.updateDraft(NS, PROMPT_KEY, "new template", vars, "commit");
        
        verify(promptHandler).updateDraft(NS, PROMPT_KEY, "new template", vars, "commit");
    }
    
    @Test
    void testDeleteDraft() throws NacosException {
        doNothing().when(promptHandler).deleteDraft(NS, PROMPT_KEY);
        
        promptProxy.deleteDraft(NS, PROMPT_KEY);
        
        verify(promptHandler).deleteDraft(NS, PROMPT_KEY);
    }
    
    @Test
    void testSubmit() throws NacosException {
        when(promptHandler.submit(NS, PROMPT_KEY, VERSION)).thenReturn("reviewing");
        
        String result = promptProxy.submit(NS, PROMPT_KEY, VERSION);
        
        assertEquals("reviewing", result);
        verify(promptHandler).submit(NS, PROMPT_KEY, VERSION);
    }
    
    @Test
    void testPublish() throws NacosException {
        doNothing().when(promptHandler).publish(NS, PROMPT_KEY, VERSION, true);
        
        promptProxy.publish(NS, PROMPT_KEY, VERSION, true);
        
        verify(promptHandler).publish(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testForcePublish() throws NacosException {
        doNothing().when(promptHandler).forcePublish(NS, PROMPT_KEY, VERSION, false);
        
        promptProxy.forcePublish(NS, PROMPT_KEY, VERSION, false);
        
        verify(promptHandler).forcePublish(NS, PROMPT_KEY, VERSION, false);
    }
    
    @Test
    void testChangeOnlineStatus() throws NacosException {
        doNothing().when(promptHandler).changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
        
        promptProxy.changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
        
        verify(promptHandler).changeOnlineStatus(NS, PROMPT_KEY, VERSION, true);
    }
    
    @Test
    void testUpdateLabels() throws NacosException {
        Map<String, String> labels = Map.of("env", "prod");
        doNothing().when(promptHandler).updateLabels(NS, PROMPT_KEY, labels);
        
        promptProxy.updateLabels(NS, PROMPT_KEY, labels);
        
        verify(promptHandler).updateLabels(NS, PROMPT_KEY, labels);
    }
    
    @Test
    void testUpdateDescription() throws NacosException {
        doNothing().when(promptHandler).updateDescription(NS, PROMPT_KEY, "new desc");
        
        promptProxy.updateDescription(NS, PROMPT_KEY, "new desc");
        
        verify(promptHandler).updateDescription(NS, PROMPT_KEY, "new desc");
    }
    
    @Test
    void testUpdateBizTags() throws NacosException {
        doNothing().when(promptHandler).updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
        
        promptProxy.updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
        
        verify(promptHandler).updateBizTags(NS, PROMPT_KEY, "tag1,tag2");
    }
}
