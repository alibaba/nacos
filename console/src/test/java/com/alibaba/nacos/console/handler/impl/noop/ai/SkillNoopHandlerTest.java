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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillDraftCreateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillLabelsUpdateForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillOnlineForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillScopeForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillSubmitForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.ai.service.skills.SkillUploadRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link SkillNoopHandler}.
 *
 * @author nacos
 */
class SkillNoopHandlerTest {
    
    private SkillNoopHandler skillNoopHandler;
    
    @BeforeEach
    void setUp() {
        skillNoopHandler = new SkillNoopHandler();
    }
    
    @Test
    void testGetSkillThrowsNotImplemented() {
        NacosApiException ex =
            assertThrows(NacosApiException.class, () -> skillNoopHandler.getSkill(new SkillForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testGetSkillVersionThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.getSkillVersion(new SkillForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDownloadSkillVersionThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.downloadSkillVersion(new SkillForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDeleteSkillThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.deleteSkill(new SkillForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testListSkillsThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.listSkills(new SkillListForm(), new AiResourceFilterableForm(),
                new PageForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUploadSkillFromZipThrowsNotImplemented() {
        SkillUploadRequest request = SkillUploadRequest.builder().namespaceId("public")
            .zipBytes(new byte[0]).build();
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.uploadSkillFromZip(request));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testCreateDraftThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.createDraft(new SkillDraftCreateForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.updateDraft(new SkillUpdateForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDeleteDraftThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.deleteDraft(new SkillForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testSubmitThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.submit(new SkillSubmitForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testPublishThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.publish(new SkillPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateLabelsThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.updateLabels(new SkillLabelsUpdateForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testChangeOnlineStatusThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.changeOnlineStatus(new SkillOnlineForm(), true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateScopeThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.updateScope(new SkillScopeForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testForcePublishThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.forcePublish(new SkillPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testRedraftThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> skillNoopHandler.redraft(new SkillPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
}
