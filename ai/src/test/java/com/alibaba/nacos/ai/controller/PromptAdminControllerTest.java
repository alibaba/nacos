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

import com.alibaba.nacos.ai.form.prompt.PromptBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptDescriptionUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptDraftCreateForm;
import com.alibaba.nacos.ai.form.prompt.PromptDraftUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelBindForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelsUpdateForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptOnlineForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.ai.form.prompt.PromptSubmitForm;
import com.alibaba.nacos.ai.form.prompt.PromptVersionPublishForm;
import com.alibaba.nacos.ai.service.prompt.PromptOperationService;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptAdminController}.
 */
@ExtendWith(MockitoExtension.class)
class PromptAdminControllerTest {
    
    @Mock
    private PromptOperationService promptOperationService;
    
    private PromptAdminController controller;
    
    @BeforeEach
    void setUp() {
        controller = new PromptAdminController(promptOperationService);
    }
    
    @Test
    void commonQueryEndpointsShouldDelegateToService() throws NacosException {
        Page<PromptMetaSummary> promptPage = new Page<>();
        promptPage.setTotalCount(1);
        Page<PromptVersionSummary> versionPage = new Page<>();
        versionPage.setTotalCount(2);
        PromptMetaInfo metaInfo = new PromptMetaInfo();
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey("prompt-a");
        versionInfo.setVersion("1.0.0");
        when(promptOperationService.listPrompts("public", "prompt-a", "accurate", "[\"tag\"]",
            2, 20)).thenReturn(promptPage);
        when(promptOperationService.listPromptVersions("public", "prompt-a", 3, 30))
            .thenReturn(versionPage);
        when(promptOperationService.getPromptDetail("public", "prompt-a")).thenReturn(metaInfo);
        when(promptOperationService.getPromptVersionDetail("public", "prompt-a", "1.0.0"))
            .thenReturn(versionInfo);
        
        Result<Page<PromptMetaSummary>> prompts = controller.listPrompts(promptListForm());
        Result<Page<PromptVersionSummary>> versions =
            controller.listPromptVersions(promptHistoryForm());
        Result<PromptMetaInfo> governance =
            controller.getPromptGovernanceDetail(promptForm());
        Result<PromptVersionInfo> version = controller.getVersionDetail(promptQueryForm());
        
        assertEquals(1, prompts.getData().getTotalCount());
        assertEquals(2, versions.getData().getTotalCount());
        assertEquals(metaInfo, governance.getData());
        assertEquals(versionInfo, version.getData());
    }
    
    @Test
    void lifecycleEndpointsShouldDelegateToService() throws NacosException {
        when(promptOperationService.createDraft(eq("public"), eq("prompt-a"), isNull(),
            eq("1.0.0"), eq("hello {{name}}"), any(), eq("init"), eq("desc"),
            eq("[\"tag\"]"))).thenReturn("1.0.0");
        when(promptOperationService.submit("public", "prompt-a", "1.0.0")).thenReturn("1.0.0");
        
        assertEquals("1.0.0", controller.createDraft(promptDraftCreateForm()).getData());
        assertEquals("ok", controller.updateDraft(promptDraftUpdateForm()).getData());
        assertEquals("ok", controller.deleteDraft(promptForm()).getData());
        assertEquals("1.0.0", controller.submit(promptSubmitForm()).getData());
        assertEquals("ok", controller.publish(promptVersionPublishForm()).getData());
        assertEquals("ok", controller.forcePublish(promptVersionPublishForm()).getData());
        assertEquals("ok", controller.redraft(promptVersionPublishForm()).getData());
        assertEquals("ok", controller.online(promptOnlineForm()).getData());
        assertEquals("ok", controller.offline(promptOnlineForm()).getData());
        
        ArgumentCaptor<List<PromptVariable>> variablesCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(promptOperationService).createDraft(eq("public"), eq("prompt-a"), isNull(),
            eq("1.0.0"), eq("hello {{name}}"), variablesCaptor.capture(), eq("init"),
            eq("desc"), eq("[\"tag\"]"));
        assertEquals("name", variablesCaptor.getValue().get(0).getName());
        verify(promptOperationService).updateDraft(eq("public"), eq("prompt-a"),
            eq("hello {{name}}"), any(), eq("update"));
        verify(promptOperationService).deleteDraft("public", "prompt-a");
        verify(promptOperationService).publish("public", "prompt-a", "1.0.0", true);
        verify(promptOperationService).forcePublish("public", "prompt-a", "1.0.0", true);
        verify(promptOperationService).redraft("public", "prompt-a", "1.0.0");
        verify(promptOperationService).changeOnlineStatus("public", "prompt-a", "1.0.0", true);
        verify(promptOperationService).changeOnlineStatus("public", "prompt-a", "1.0.0",
            false);
    }
    
    @Test
    void updateEndpointsShouldParseLabelsAndDelegate() throws NacosException {
        assertEquals("ok", controller.updateLabels(labelsUpdateForm()).getData());
        assertEquals("ok", controller.updateDescription(descriptionUpdateForm()).getData());
        assertEquals("ok", controller.updateBizTags(bizTagsUpdateForm()).getData());
        assertEquals(true, controller.deletePrompt(promptForm(), null).getData());
        
        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(promptOperationService).updateLabels(eq("public"), eq("prompt-a"),
            labelsCaptor.capture());
        assertEquals("1.0.0", labelsCaptor.getValue().get("stable"));
        verify(promptOperationService).updateDescription("public", "prompt-a", "new desc");
        verify(promptOperationService).updateBizTags("public", "prompt-a", "[\"tag\"]");
        verify(promptOperationService).deletePrompt("public", "prompt-a");
    }
    
    @Test
    void legacyEndpointsShouldDelegateToService() throws NacosException {
        PromptMetaInfo metaInfo = new PromptMetaInfo();
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        when(promptOperationService.publishPromptVersion(eq("public"), eq("prompt-a"),
            eq("1.0.0"), eq("hello {{name}}"), eq("init"), eq("desc"), eq("[\"tag\"]"),
            any())).thenReturn(true);
        when(promptOperationService.getPromptMeta("public", "prompt-a")).thenReturn(metaInfo);
        when(promptOperationService.queryPromptDetail("public", "prompt-a", "1.0.0", null))
            .thenReturn(versionInfo);
        when(promptOperationService.bindLabel("public", "prompt-a", "stable", "1.0.0"))
            .thenReturn(true);
        when(promptOperationService.unbindLabel("public", "prompt-a", "stable"))
            .thenReturn(true);
        when(promptOperationService.updatePromptMetadata("public", "prompt-a", "new desc",
            "[\"tag\"]")).thenReturn(true);
        
        assertEquals(true, controller.publishPrompt(promptPublishForm(), null).getData());
        assertEquals(metaInfo, controller.getPromptMetadata(promptForm()).getData());
        assertEquals(versionInfo, controller.queryPromptDetail(promptQueryForm()).getData());
        assertEquals(true, controller.bindLabel(promptLabelBindForm(), null).getData());
        assertEquals(true, controller.unbindLabel(promptLabelForm(), null).getData());
        assertEquals(true, controller.updatePromptMetadata(promptMetadataForm(), null).getData());
    }
    
    @Test
    void downloadPromptVersionShouldReturnMarkdownResponse() throws NacosException {
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey("prompt-a");
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("hello");
        when(promptOperationService.downloadPromptVersion("public", "prompt-a", "1.0.0"))
            .thenReturn(versionInfo);
        
        ResponseEntity<byte[]> response = controller.downloadPromptVersion(promptQueryForm());
        
        assertNotNull(response.getBody());
        assertEquals(200, response.getStatusCode().value());
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertEquals(true, body.contains("hello"));
    }
    
    private static PromptForm promptForm() {
        PromptForm form = new PromptForm();
        form.setPromptKey("prompt-a");
        return form;
    }
    
    private static PromptListForm promptListForm() {
        PromptListForm form = new PromptListForm();
        form.setPromptKey("prompt-a");
        form.setSearch("accurate");
        form.setBizTags("[\"tag\"]");
        form.setPageNo(2);
        form.setPageSize(20);
        return form;
    }
    
    private static PromptHistoryForm promptHistoryForm() {
        PromptHistoryForm form = new PromptHistoryForm();
        form.setPromptKey("prompt-a");
        form.setPageNo(3);
        form.setPageSize(30);
        return form;
    }
    
    private static PromptQueryForm promptQueryForm() {
        PromptQueryForm form = new PromptQueryForm();
        form.setPromptKey("prompt-a");
        form.setVersion("1.0.0");
        return form;
    }
    
    private static PromptDraftCreateForm promptDraftCreateForm() {
        PromptDraftCreateForm form = new PromptDraftCreateForm();
        form.setPromptKey("prompt-a");
        form.setTargetVersion("1.0.0");
        form.setTemplate("hello {{name}}");
        form.setVariables("[{\"name\":\"name\",\"defaultValue\":\"nacos\"}]");
        form.setCommitMsg("init");
        form.setDescription("desc");
        form.setBizTags("[\"tag\"]");
        return form;
    }
    
    private static PromptDraftUpdateForm promptDraftUpdateForm() {
        PromptDraftUpdateForm form = new PromptDraftUpdateForm();
        form.setPromptKey("prompt-a");
        form.setTemplate("hello {{name}}");
        form.setVariables("[{\"name\":\"name\",\"defaultValue\":\"nacos\"}]");
        form.setCommitMsg("update");
        return form;
    }
    
    private static PromptSubmitForm promptSubmitForm() {
        PromptSubmitForm form = new PromptSubmitForm();
        form.setPromptKey("prompt-a");
        form.setVersion("1.0.0");
        return form;
    }
    
    private static PromptVersionPublishForm promptVersionPublishForm() {
        PromptVersionPublishForm form = new PromptVersionPublishForm();
        form.setPromptKey("prompt-a");
        form.setVersion("1.0.0");
        return form;
    }
    
    private static PromptOnlineForm promptOnlineForm() {
        PromptOnlineForm form = new PromptOnlineForm();
        form.setPromptKey("prompt-a");
        form.setVersion("1.0.0");
        return form;
    }
    
    private static PromptLabelsUpdateForm labelsUpdateForm() {
        PromptLabelsUpdateForm form = new PromptLabelsUpdateForm();
        form.setPromptKey("prompt-a");
        form.setLabels("{\"stable\":\"1.0.0\"}");
        return form;
    }
    
    private static PromptDescriptionUpdateForm descriptionUpdateForm() {
        PromptDescriptionUpdateForm form = new PromptDescriptionUpdateForm();
        form.setPromptKey("prompt-a");
        form.setDescription("new desc");
        return form;
    }
    
    private static PromptBizTagsUpdateForm bizTagsUpdateForm() {
        PromptBizTagsUpdateForm form = new PromptBizTagsUpdateForm();
        form.setPromptKey("prompt-a");
        form.setBizTags("[\"tag\"]");
        return form;
    }
    
    private static PromptPublishForm promptPublishForm() {
        PromptPublishForm form = new PromptPublishForm();
        form.setPromptKey("prompt-a");
        form.setVersion("1.0.0");
        form.setTemplate("hello {{name}}");
        form.setVariables("[{\"name\":\"name\",\"defaultValue\":\"nacos\"}]");
        form.setCommitMsg("init");
        form.setDescription("desc");
        form.setBizTags("[\"tag\"]");
        return form;
    }
    
    private static PromptLabelBindForm promptLabelBindForm() {
        PromptLabelBindForm form = new PromptLabelBindForm();
        form.setPromptKey("prompt-a");
        form.setLabel("stable");
        form.setVersion("1.0.0");
        return form;
    }
    
    private static PromptLabelForm promptLabelForm() {
        PromptLabelForm form = new PromptLabelForm();
        form.setPromptKey("prompt-a");
        form.setLabel("stable");
        return form;
    }
    
    private static PromptMetadataForm promptMetadataForm() {
        PromptMetadataForm form = new PromptMetadataForm();
        form.setPromptKey("prompt-a");
        form.setDescription("new desc");
        form.setBizTags("[\"tag\"]");
        return form;
    }
}
