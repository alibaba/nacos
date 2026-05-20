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
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSpecNoopHandlerTest {
    
    private AgentSpecNoopHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentSpecNoopHandler();
    }
    
    @Test
    void testGetAgentSpecThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.getAgentSpec(new AgentSpecForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testGetAgentSpecVersionThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.getAgentSpecVersion(new AgentSpecForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDeleteAgentSpecThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.deleteAgentSpec(new AgentSpecForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testListAgentSpecsThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.listAgentSpecs(new AgentSpecListForm(),
                new AiResourceFilterableForm(), new PageForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUploadAgentSpecFromZipThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.uploadAgentSpecFromZip("ns", new byte[0], false));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testCreateDraftThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.createDraft(new AgentSpecDraftCreateForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateDraftThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateDraft(new AgentSpecUpdateForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testDeleteDraftThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.deleteDraft(new AgentSpecForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testSubmitThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.submit(new AgentSpecSubmitForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testPublishThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.publish(new AgentSpecPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testForcePublishThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.forcePublish(new AgentSpecPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateLabelsThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateLabels(new AgentSpecLabelsUpdateForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testChangeOnlineStatusThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.changeOnlineStatus(new AgentSpecOnlineForm(), true));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testUpdateScopeThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.updateScope(new AgentSpecScopeForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testRedraftThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.redraft(new AgentSpecPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
}
