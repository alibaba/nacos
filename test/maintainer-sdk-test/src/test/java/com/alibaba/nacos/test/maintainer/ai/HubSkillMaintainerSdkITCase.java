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

package com.alibaba.nacos.test.maintainer.ai;

import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.model.Page;
import java.util.UUID;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Hub Maintainer SDK controlled errors through the deployed Admin HTTP transport.
 *
 * <p>The full durable lifecycle is covered by the Hub HTTP IT suite. Delegated
 * Hub-role and remote Console transport require their deployment fixtures.</p>
 *
 * @author nacos
 */
class HubSkillMaintainerSdkITCase extends MaintainerSdkBaseITCase {

    private SkillMaintainerService skill;

    @BeforeEach
    void setUp() throws NacosException {
        assumeTrue(AUTH_ENABLED, "Hub SDK IT requires enabled authentication");
        skill = createAiMaintainerService().skill();
    }

    @Test
    void nullRequestsFailWithControlledLocal400() {
        assertEquals(400, assertThrows(NacosException.class, () -> skill.shareHubSkill(null)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.adjustHubShare(null)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.unshareHubSkill(null)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.forceUnshareHubSkill(null)).getErrCode());
    }

    @Test
    void latestSourceVersionIsRejectedByAdminApi() {
        HubShareSkillRequest request = new HubShareSkillRequest();
        request.setSourceNamespaceId("public");
        request.setSourceName("unused");
        request.setSourceVersion("latest");
        assertEquals(400, assertThrows(NacosException.class, () -> skill.shareHubSkill(request)).getErrCode());
    }

    @Test
    void missingEntryPreservesServer404() {
        HubAdjustShareRequest request = new HubAdjustShareRequest();
        request.setHubEntryId(Long.MAX_VALUE);
        request.setExpectedMetaVersion(1L);
        request.setTargetVersion("1.0.0");
        assertEquals(404, assertThrows(NacosException.class, () -> skill.adjustHubShare(request)).getErrCode());
    }

    @Test
    void anonymousCallerCannotUseGovernanceEndpoint() throws Exception {
        SkillMaintainerService anonymous = createAiMaintainerService(AuthIdentity.ANONYMOUS).skill();
        HubForceUnshareRequest request = new HubForceUnshareRequest();
        request.setHubEntryId(Long.MAX_VALUE);
        request.setExpectedMetaVersion(1L);
        request.setReason("anonymous denial IT");
        assertEquals(403, assertThrows(NacosException.class, () -> anonymous.forceUnshareHubSkill(request)).getErrCode());
    }

    @Test
    void readContractsPreserveLocalValidationAndRemoteNotFound() {
        assertEquals(400, assertThrows(NacosException.class, () -> skill.listHubSkills(null)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.getHubSkillDetail(-1L)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.getHubSkillContent(null)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.getHubSkillDownload(null)).getErrCode());
        assertEquals(404, assertThrows(NacosException.class, () -> skill.getHubSkillDownload(Long.MAX_VALUE)).getErrCode());
        assertEquals(404, assertThrows(NacosException.class, () -> skill.getHubSkillDetail(Long.MAX_VALUE)).getErrCode());
        assertEquals(404, assertThrows(NacosException.class, () -> skill.getHubSkillContent(Long.MAX_VALUE)).getErrCode());
    }

    @Test
    void readTransportRejectsAnonymousIdentity() throws Exception {
        SkillMaintainerService anonymous = createAiMaintainerService(AuthIdentity.ANONYMOUS).skill();
        assertEquals(403, assertThrows(NacosException.class,
            () -> anonymous.listHubSkills(new HubSkillListRequest())).getErrCode());
        assertEquals(403, assertThrows(NacosException.class, () -> anonymous.getHubSkillDetail(1L)).getErrCode());
        assertEquals(403, assertThrows(NacosException.class, () -> anonymous.getHubSkillContent(1L)).getErrCode());
        assertEquals(403, assertThrows(NacosException.class, () -> anonymous.getHubSkillDownload(1L)).getErrCode());
    }

    @Test
    void authenticatedDirectoryReadReturnsATypedEmptyPage() throws Exception {
        HubSkillListRequest request =
            new HubSkillListRequest();
        request.setQuery("no-hub-match-" + UUID.randomUUID());
        Page<HubSkillSummary> page = skill.listHubSkills(request);
        assertEquals(0, page.getTotalCount());
        assertEquals(1, page.getPageNumber());
        assertEquals(0, page.getPageItems().size());
    }

    @Test
    void transferValidationAndAnonymousDenialArePreserved() throws Exception {
        assertEquals(400, assertThrows(NacosException.class, () -> skill.importHubSkill(null)).getErrCode());
        assertEquals(400, assertThrows(NacosException.class, () -> skill.copyHubSkill(null)).getErrCode());
        HubSkillTransferRequest request =
            new HubSkillTransferRequest();
        request.setHubEntryId(Long.MAX_VALUE);
        request.setTargetNamespaceId("public");
        request.setTargetName("hub-sdk-transfer-" + UUID.randomUUID());
        assertEquals(404, assertThrows(NacosException.class, () -> skill.importHubSkill(request)).getErrCode());
        SkillMaintainerService anonymous = createAiMaintainerService(AuthIdentity.ANONYMOUS).skill();
        assertEquals(403, assertThrows(NacosException.class, () -> anonymous.importHubSkill(request)).getErrCode());
    }

    @Test
    void stateContractsPreserveValidationNotFoundAndAnonymousDenial() throws Exception {
        assertEquals(400, assertThrows(NacosException.class,
                () -> skill.getHubSkillSourceShare("", "unused")).getErrCode());
        assertEquals(400, assertThrows(NacosException.class,
                () -> skill.getHubSkillGovernanceState(0L)).getErrCode());
        assertEquals(404, assertThrows(NacosException.class,
                () -> skill.getHubSkillGovernanceState(Long.MAX_VALUE)).getErrCode());
        SkillMaintainerService anonymous = createAiMaintainerService(AuthIdentity.ANONYMOUS).skill();
        assertEquals(403, assertThrows(NacosException.class,
                () -> anonymous.getHubSkillSourceShare("public", "unused")).getErrCode());
        assertEquals(403, assertThrows(NacosException.class,
                () -> anonymous.getHubSkillGovernanceState(1L)).getErrCode());
    }

}
