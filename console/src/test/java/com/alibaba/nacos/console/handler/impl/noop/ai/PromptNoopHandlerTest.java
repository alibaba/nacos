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

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptNoopHandlerTest {
    
    private PromptNoopHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new PromptNoopHandler();
    }
    
    @Test
    void testDeletePromptThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.deletePrompt(new PromptForm(), null, null));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testListPromptsThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.listPrompts(new PromptListForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testListPromptVersionsThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.listPromptVersions(new PromptHistoryForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testGetPromptGovernanceDetailThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.getPromptGovernanceDetail("ns", "key"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testGetVersionDetailThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.getVersionDetail("ns", "key", "v1"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDownloadPromptVersionThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.downloadPromptVersion("ns", "key", "v1"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testCreateDraftThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.createDraft("ns", "key", null, null,
                "tpl", null, null, null, null));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateDraft("ns", "key", "tpl", null, null));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDeleteDraftThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.deleteDraft("ns", "key"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testSubmitThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.submit("ns", "key", "v1"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testPublishThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.publish("ns", "key", "v1", true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testForcePublishThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.forcePublish("ns", "key", "v1", true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testChangeOnlineStatusThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.changeOnlineStatus("ns", "key", "v1", true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateLabelsThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateLabels("ns", "key", Collections.emptyMap()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateDescriptionThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateDescription("ns", "key", "desc"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateBizTagsThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateBizTags("ns", "key", "tag1"));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
}
