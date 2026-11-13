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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.ai.controller.HubSkillAdminController;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hub parameter extraction and SPI registration tests.
 *
 * @author nacos
 */
class HubSkillHttpParamExtractorTest {
    
    @Test
    void shareExtractsSourceIdentityInsteadOfCallerSuppliedHubIdentity() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/v3/admin/ai/hub/skills/share");
        request.addParameter("sourceNamespaceId", "source-a");
        request.addParameter("sourceName", "source-skill");
        request.addParameter("namespaceId", "__nacos_ai_hub__");
        request.addParameter("skillName", "forged-skill");
        ParamInfo result = new HubSkillHttpParamExtractor().extractParam(request).get(0);
        assertEquals("source-a", result.getNamespaceId());
        assertEquals("source-skill", result.getSkillName());
    }
    
    @Test
    void entryIdOperationsDoNotInventSourceParameters() {
        for (String suffix : new String[] {"adjust", "unshare", "force-unshare"}) {
            MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/v3/admin/ai/hub/skills/" + suffix);
            assertTrue(new HubSkillHttpParamExtractor().extractParam(request).isEmpty());
        }
    }
    
    @Test
    void annotationResolvesRegisteredExtractorInsteadOfNoopFallback() {
        assertInstanceOf(HubSkillHttpParamExtractor.class, ExtractorManager.getHttpExtractor(
            HubSkillAdminController.class.getAnnotation(ExtractorManager.Extractor.class)));
    }
}
