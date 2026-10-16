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

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validation boundary tests for S1-1.
 *
 * @author nacos
 */
class HubSkillRequestValidatorTest {
    
    @Test
    void shareRequiresExplicitVersionAndRealSourceNamespace() {
        HubShareSkillRequest request = new HubShareSkillRequest();
        request.setSourceNamespaceId("team-a");
        request.setSourceName("find-skills");
        request.setSourceVersion("1.0.0");
        assertDoesNotThrow(() -> HubSkillRequestValidator.validate(request));
        
        request.setSourceVersion("latest");
        assertThrows(NacosException.class, () -> HubSkillRequestValidator.validate(request));
        request.setSourceVersion("1.0.0");
        request.setSourceNamespaceId("__nacos_ai_hub__");
        assertThrows(NacosException.class, () -> HubSkillRequestValidator.validate(request));
        request.setSourceNamespaceId("team-a");
        request.setExpectedSourceResourceId(-1L);
        assertThrows(NacosException.class, () -> HubSkillRequestValidator.validate(request));
    }
    
    @Test
    void adjustRequiresExpectedMetaVersion() {
        HubAdjustShareRequest request = new HubAdjustShareRequest();
        request.setHubEntryId(305L);
        request.setTargetVersion("2.0.0");
        assertThrows(NacosException.class, () -> HubSkillRequestValidator.validate(request));
        request.setExpectedMetaVersion(7L);
        assertDoesNotThrow(() -> HubSkillRequestValidator.validate(request));
    }
    
    @Test
    void unshareRequiresStableEntryId() {
        HubUnshareRequest request = new HubUnshareRequest();
        request.setExpectedMetaVersion(7L);
        assertThrows(NacosException.class, () -> HubSkillRequestValidator.validate(request));
        request.setHubEntryId(305L);
        assertDoesNotThrow(() -> HubSkillRequestValidator.validate(request));
    }
    
    @Test
    void administrativeTakedownRequiresReason() {
        HubForceUnshareRequest request = new HubForceUnshareRequest();
        request.setHubEntryId(305L);
        request.setExpectedMetaVersion(7L);
        assertThrows(NacosException.class, () -> HubSkillRequestValidator.validate(request));
        request.setReason("security incident");
        assertDoesNotThrow(() -> HubSkillRequestValidator.validate(request));
    }
}
