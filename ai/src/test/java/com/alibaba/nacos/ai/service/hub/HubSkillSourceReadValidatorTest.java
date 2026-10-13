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
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests source identity and live status with the real S1 eligibility validator. */
class HubSkillSourceReadValidatorTest {
    
    private AiResourceManager manager;
    
    private HubSkillSourceReadValidator validator;
    
    private AiResource source;
    
    private AiResourceVersion sourceVersion;
    
    private ResolvedHubSkillReference snapshot;
    
    @BeforeEach
    void setUp() throws Exception {
        manager = mock(AiResourceManager.class);
        validator = new HubSkillSourceReadValidator(new HubSkillEligibilityValidator(manager));
        source = new AiResource();
        source.setId(101L);
        source.setNamespaceId("team-a");
        source.setName("find-skills");
        source.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        source.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        source.setScope(VisibilityConstants.SCOPE_PUBLIC);
        sourceVersion = new AiResourceVersion();
        sourceVersion.setId(501L);
        sourceVersion.setNamespaceId("team-a");
        sourceVersion.setName("find-skills");
        sourceVersion.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        sourceVersion.setVersion("1.0.0");
        sourceVersion.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        snapshot = snapshot(101L, 501L, "team-a");
        when(manager.findMeta("team-a", "find-skills", AiResourceConstants.RESOURCE_TYPE_SKILL))
            .thenAnswer(invocation -> source);
        when(manager.findVersion("team-a", "find-skills",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenAnswer(invocation -> sourceVersion);
    }
    
    @Test
    void permitsExactPublicEnabledOnlineSourceWithoutSourceReadCheck() throws Exception {
        assertSame(source, validator.requireLiveSource(snapshot));
    }
    
    @Test
    void missingSourceIsUnavailableWithoutLeakingItsNamespace() {
        source = null;
        assertUnavailable();
    }
    
    @Test
    void recreatedSourceWithSameNameIsUnavailable() {
        source.setId(102L);
        assertUnavailable();
    }
    
    @Test
    void privateSourceIsUnavailable() {
        source.setScope(VisibilityConstants.SCOPE_PRIVATE);
        assertUnavailable();
    }
    
    @Test
    void disabledSourceIsUnavailable() {
        source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        assertUnavailable();
    }
    
    @Test
    void deletedPinnedVersionIsUnavailable() {
        sourceVersion = null;
        assertUnavailable();
    }
    
    @Test
    void offlinePinnedVersionIsUnavailable() {
        sourceVersion.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        assertUnavailable();
    }
    
    @Test
    void recreatedVersionWithSameVersionStringIsUnavailable() {
        sourceVersion.setId(502L);
        assertUnavailable();
    }
    
    @Test
    void mismatchedVersionNamespaceIsUnavailable() {
        sourceVersion.setNamespaceId("team-b");
        assertUnavailable();
    }
    
    @Test
    void mismatchedVersionTypeIsUnavailable() {
        sourceVersion.setType("agent");
        assertUnavailable();
    }
    
    @Test
    void sourceNameOnlyIsNotASafeCrossNamespaceIdentity() throws Exception {
        ResolvedHubSkillReference other = snapshot(101L, 501L, "team-b");
        NacosException error = assertThrows(NacosException.class,
            () -> validator.requireLiveSource(other));
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
    }
    
    @Test
    void nullPublicationIsUnavailable() {
        NacosException error = assertThrows(NacosException.class,
            () -> validator.requireLiveSource(null));
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
    }
    
    @Test
    void sourceBecomingPrivateDuringVersionLookupIsUnavailable() {
        when(manager.findVersion("team-a", "find-skills",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")).thenAnswer(invocation -> {
                source.setScope(VisibilityConstants.SCOPE_PRIVATE);
                return sourceVersion;
            });
        assertUnavailable();
    }
    
    @Test
    void sourceDisabledDuringVersionLookupIsUnavailable() {
        when(manager.findVersion("team-a", "find-skills",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")).thenAnswer(invocation -> {
                source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
                return sourceVersion;
            });
        assertUnavailable();
    }
    
    @Test
    void sourceRecreatedDuringVersionLookupIsUnavailable() {
        when(manager.findVersion("team-a", "find-skills",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")).thenAnswer(invocation -> {
                source.setId(102L);
                return sourceVersion;
            });
        assertUnavailable();
    }
    
    @Test
    void sourceDeletedDuringVersionLookupIsUnavailable() {
        when(manager.findVersion("team-a", "find-skills",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0")).thenAnswer(invocation -> {
                source = null;
                return sourceVersion;
            });
        assertUnavailable();
    }
    
    @Test
    void versionWithdrawnDuringFinalMetadataCheckIsUnavailable() {
        AtomicInteger reads =
            new AtomicInteger();
        when(manager.findMeta("team-a", "find-skills", AiResourceConstants.RESOURCE_TYPE_SKILL))
            .thenAnswer(invocation -> {
                if (reads.incrementAndGet() == 3) {
                    sourceVersion.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
                }
                return source;
            });
        assertUnavailable();
    }
    
    @Test
    void unexpectedStorageErrorsAreRedacted() {
        when(manager.findVersion("team-a", "find-skills",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenThrow(new IllegalStateException("storage team-a/501"));
        NacosException error = assertThrows(NacosException.class,
            () -> validator.requireLiveSource(snapshot));
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
        assertFalse(error.getMessage().contains("team-a"));
        assertFalse(error.getMessage().contains("501"));
    }
    
    private void assertUnavailable() {
        NacosException error = assertThrows(NacosException.class,
            () -> validator.requireLiveSource(snapshot));
        assertEquals(NacosException.NOT_FOUND, error.getErrCode());
        assertFalse(error.getMessage().contains("team-a"));
        assertFalse(error.getMessage().contains("501"));
    }
    
    @Test
    void indexValidationDoesNotMistakeStorageFailureForSourceRevocation() {
        when(manager.findVersion("team-a", "find-skills", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "1.0.0"))
            .thenThrow(new IllegalStateException("storage team-a/501"));
        NacosException error =
            assertThrows(NacosException.class, () -> validator.requireLiveSourceForIndex(snapshot));
        assertEquals(500, error.getErrCode());
        assertFalse(error.getMessage().contains("team-a"));
    }
    
    @Test
    void reconciliationPreservesCheckedBackendOutagesInsteadOfReportingAnOrphan() {
        when(manager.findVersion("team-a", "find-skills", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "1.0.0")).thenAnswer(invocation -> {
                throw new NacosException(503, "private backend details");
            });
        NacosException failure = assertThrows(NacosException.class,
            () -> validator.requireLiveSourceForIndex(snapshot));
        assertEquals(500, failure.getErrCode());
        assertFalse(failure.getMessage().contains("private backend"));
    }
    
    private ResolvedHubSkillReference snapshot(long resourceId, long versionId, String ns)
        throws Exception {
        HubResourceReference ref = HubResourceReferenceJsonUtil.build(ns, resourceId,
            "find-skills", versionId, "1.0.0");
        AiResource hub = new AiResource();
        hub.setId(901L);
        hub.setMetaVersion(7L);
        hub.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        hub.setName("find-skills-" + resourceId);
        AiResourceVersion version = new AiResourceVersion();
        version.setId(701L);
        return new ResolvedHubSkillReference(hub, version, ref);
    }
    
    @Test
    void versionProjectionKeepsPinnedIdentityAndRejectsRecreatedRows() throws Exception {
        sourceVersion.setAuthor("original-author");
        sourceVersion.setDownloadCount(13L);
        HubEligibleSkillVersion eligible = validator.requireLiveVersion(snapshot);
        assertSame(source, eligible.getSourceMeta());
        assertSame(sourceVersion, eligible.getSourceVersion());
        assertEquals("1.0.0", eligible.getSourceVersion().getVersion());
        sourceVersion.setId(502L);
        assertThrows(NacosException.class, () -> validator.requireLiveVersion(snapshot));
    }
    
}
