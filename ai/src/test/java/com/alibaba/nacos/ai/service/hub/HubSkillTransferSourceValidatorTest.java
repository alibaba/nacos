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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exact identity and online status are mandatory, even for a name reused after delete. */
class HubSkillTransferSourceValidatorTest {
    
    private final AiResourceManager manager = mock(AiResourceManager.class);
    private final HubSkillTransferSourceValidator validator =
        new HubSkillTransferSourceValidator(manager);
    private final AiResource source = new AiResource();
    private final AiResourceVersion version = new AiResourceVersion();
    
    @BeforeEach
    void setUp() {
        source.setId(101L);
        source.setNamespaceId("ns-a");
        source.setName("find-skills");
        source.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        source.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        source.setScope("PRIVATE");
        version.setId(501L);
        version.setNamespaceId("ns-a");
        version.setName("find-skills");
        version.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        version.setVersion("1.0.0");
        version.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(manager.findMeta("ns-a", "find-skills", "skill")).thenReturn(source);
        when(manager.findVersion("ns-a", "find-skills", "skill", "1.0.0"))
            .thenReturn(version);
    }
    
    @Test
    void explicitPrivateVersionIsEligibleForDirectCopyAfterIndependentReadPermission()
        throws Exception {
        HubSkillTransferSourceSnapshot snapshot = validator.qualify(source, "1.0.0", null);
        assertEquals(101L, snapshot.getResourceId());
        assertEquals(501L, snapshot.getVersionId());
        assertEquals(version, validator.requireStillLive(snapshot));
    }
    
    @Test
    void sameNameNewResourceIdDoesNotInheritPermission() throws Exception {
        HubSkillTransferSourceSnapshot pinned = validator.qualify(source, "1.0.0", null);
        source.setId(999L);
        assertThrows(NacosException.class, () -> validator.requireStillLive(pinned));
    }
    
    @Test
    void sameVersionStringNewVersionIdMustFail() throws Exception {
        HubSkillTransferSourceSnapshot pinned = validator.qualify(source, "1.0.0", null);
        version.setId(999L);
        assertThrows(NacosException.class, () -> validator.requireStillLive(pinned));
    }
    
    @Test
    void sourceOfflineFailsBothBeforeAndAfterQualification() throws Exception {
        HubSkillTransferSourceSnapshot pinned = validator.qualify(source, "1.0.0", null);
        version.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        assertThrows(NacosException.class, () -> validator.requireStillLive(pinned));
        assertThrows(NacosException.class, () -> validator.qualify(source, "1.0.0", null));
    }
    
    @Test
    void disabledSourceCannotBeCopied() {
        source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        assertThrows(NacosException.class, () -> validator.qualify(source, "1.0.0", null));
    }
    
    @Test
    void latestLabelCannotDriftIntoAClone() {
        assertThrows(NacosException.class, () -> validator.qualify(source, "latest", null));
    }
    
    @Test
    void storageFailureIsControlled500WithoutBackendDetails() {
        when(manager.findMeta("ns-a", "find-skills", "skill"))
            .thenThrow(
                new IllegalStateException("SELECT private_namespace and secret storage key"));
        NacosException failure = assertThrows(NacosException.class,
            () -> validator.lookupDirectSource("ns-a", "find-skills"));
        assertEquals(500, failure.getErrCode());
        assertEquals("Skill transfer source unavailable", failure.getErrMsg());
    }
    
    @Test
    void changedDescriptorAndResourceTypeInvalidateSnapshot() throws Exception {
        HubSkillTransferSourceSnapshot snapshot = validator.qualify(source, "1.0.0", null);
        version.setStorage("changed descriptor");
        assertThrows(NacosException.class, () -> validator.requireStillLive(snapshot));
        version.setStorage(null);
        version.setType("prompt");
        assertThrows(NacosException.class, () -> validator.requireStillLive(snapshot));
    }
    
}
