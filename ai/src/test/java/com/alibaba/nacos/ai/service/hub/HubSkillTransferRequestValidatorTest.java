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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Input path cannot be made to drift to latest or use the reserved Hub namespace. */
class HubSkillTransferRequestValidatorTest {
    
    @Test
    void rejectsMissingRequest() {
        assertThrows(NacosException.class,
            () -> HubSkillTransferRequestValidator.validate(null, true));
    }
    
    @Test
    void rejectsHubNamespaceAsTargetBeforeNamePrecheck() {
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        request.setHubEntryId(99L);
        request.setTargetNamespaceId("__nacos_ai_hub__");
        request.setTargetName("new-skill");
        assertThrows(NacosException.class,
            () -> HubSkillTransferRequestValidator.validate(request, true));
    }
    
    @Test
    void rejectsHubImportWithSourceNamespaceFields() {
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        request.setHubEntryId(99L);
        request.setTargetNamespaceId("target-ns");
        request.setTargetName("new-skill");
        request.setSourceName("malicious");
        assertThrows(NacosException.class,
            () -> HubSkillTransferRequestValidator.validate(request, true));
    }
    
    @Test
    void rejectsDirectCopyByMutableLatestVersionLabel() {
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        request.setSourceNamespaceId("source-ns");
        request.setSourceName("find-skills");
        request.setSourceVersion("latest");
        request.setTargetNamespaceId("target-ns");
        request.setTargetName("new-skill");
        assertThrows(NacosException.class,
            () -> HubSkillTransferRequestValidator.validate(request, false));
    }
    
    @Test
    void defaultDraftVersionIsStable() throws Exception {
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        request.setHubEntryId(99L);
        request.setTargetNamespaceId("target-ns");
        request.setTargetName("new-skill");
        assertEquals("0.0.1", HubSkillTransferRequestValidator.validate(request, true));
    }
}
