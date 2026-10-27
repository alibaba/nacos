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
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S2-2 read-only resolution and forged/stale Hub pointer regressions. */
class HubSkillReferenceResolverTest {
    
    private HubShareRepository repository;
    
    private HubSkillReferenceResolver resolver;
    
    private AiResource entry;
    
    private AiResourceVersion version;
    
    private HubResourceReference reference;
    
    @BeforeEach
    void setUp() throws NacosException {
        repository = mock(HubShareRepository.class);
        resolver = new HubSkillReferenceResolver(repository);
        reference = reference(101L, 501L, "1.0.0");
        entry = hubMeta(901L, reference);
        version = hubVersion(3001L, entry, reference);
        when(repository.findByEntryId(901L)).thenReturn(entry);
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenReturn(version);
    }
    
    @Test
    void resolvesPinnedVersionByHubEntryIdWithoutWriting() throws Exception {
        ResolvedHubSkillReference result = resolver.resolve(901L);
        assertEquals(901L, result.getHubEntryId());
        assertEquals(7L, result.getHubMetaVersion());
        assertEquals(3001L, result.getHubVersionId());
        assertEquals("find-skills-101", result.getInternalName());
        assertEquals("find-skills", result.getSourceName());
        assertEquals("team-a", result.getSourceNamespaceId());
        assertEquals(101L, result.getSourceResourceId());
        assertEquals(501L, result.getSourceVersionId());
        assertEquals("1.0.0", result.getSourceVersion());
        assertEquals("shared", result.getDescription());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        assertEquals(result.getSourceVersionId(),
            result.toReference().getSourceVersionId().longValue());
        resolver.requireUnchanged(result);
        verify(repository, never()).ensureReferenceVersion(anyLong(),
            any(HubResourceReference.class));
        verify(repository, never()).disable(anyLong(), anyLong());
        verify(repository, never()).publish(anyLong(), anyLong(), any(HubResourceReference.class));
    }
    
    @Test
    void doesNotConfuseTwoSameNamedSources() throws Exception {
        HubResourceReference another = reference(201L, 601L, "2.0.0");
        AiResource otherMeta = hubMeta(902L, another);
        AiResourceVersion otherVersion = hubVersion(3002L, otherMeta, another);
        when(repository.findByEntryId(902L)).thenReturn(otherMeta);
        when(repository.requireReferenceVersion(eq(902L), any(HubResourceReference.class)))
            .thenReturn(otherVersion);
        
        ResolvedHubSkillReference first = resolver.resolve(901L);
        ResolvedHubSkillReference second = resolver.resolve(902L);
        assertEquals("find-skills", first.getSourceName());
        assertEquals("find-skills", second.getSourceName());
        assertEquals("find-skills-101", first.getInternalName());
        assertEquals("find-skills-201", second.getInternalName());
        assertEquals(501L, first.getSourceVersionId());
        assertEquals(601L, second.getSourceVersionId());
    }
    
    @Test
    void rejectsNonpositiveAndMissingEntryIds() throws Exception {
        assertEquals(NacosException.INVALID_PARAM, error(() -> resolver.resolve(null)));
        assertEquals(NacosException.INVALID_PARAM, error(() -> resolver.resolve(0L)));
        assertEquals(NacosException.INVALID_PARAM, error(() -> resolver.resolve(-9L)));
        assertEquals(NacosException.NOT_FOUND, error(() -> resolver.resolve(909L)));
        verify(repository, never()).findByEntryId(0L);
    }
    
    @Test
    void rejectsDisabledAndIncorrectNamespaceOrOwner() {
        entry.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        assertUnavailable();
        entry.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        entry.setNamespaceId("team-a");
        assertUnavailable();
        entry.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        entry.setFrom("local");
        assertUnavailable();
        entry.setFrom("hub");
        entry.setOwner("source-owner");
        assertUnavailable();
    }
    
    @Test
    void rejectsWrongIdTypeScopeAndMetaVersion() {
        entry.setId(902L);
        assertUnavailable();
        entry.setId(901L);
        entry.setType("prompt");
        assertUnavailable();
        entry.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        entry.setScope(VisibilityConstants.SCOPE_PRIVATE);
        assertUnavailable();
        entry.setScope(VisibilityConstants.SCOPE_PUBLIC);
        entry.setMetaVersion(0L);
        assertUnavailable();
    }
    
    @Test
    void rejectsMalformedExtAndWrongInternalName() throws Exception {
        entry.setExt("{broken-json");
        assertUnavailable();
        entry.setExt(HubResourceReferenceJsonUtil.toJson(reference));
        entry.setName("find-skills");
        assertUnavailable();
    }
    
    @Test
    void rejectsHubAsSourceAndFloatingLatestVersion() throws Exception {
        HubResourceReference hubSource = reference(101L, 501L, "1.0.0");
        hubSource.setSourceNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        entry.setExt(HubResourceReferenceJsonUtil.toJson(hubSource));
        assertUnavailable();
        HubResourceReference floating = reference(101L, 501L, "latest");
        entry.setExt(HubResourceReferenceJsonUtil.toJson(floating));
        assertUnavailable();
    }
    
    @Test
    void refusesAbsentMalformedOrDifferentCurrentVersionPointer() {
        entry.setVersionInfo(null);
        assertUnavailable();
        entry.setVersionInfo("not-json");
        assertUnavailable();
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setLabels(Collections.emptyMap());
        entry.setVersionInfo(JacksonUtils.toJson(info));
        assertUnavailable();
        info.setLabels(Collections.singletonMap(AiResourceConstants.LABEL_LATEST, "2.0.0"));
        entry.setVersionInfo(JacksonUtils.toJson(info));
        assertUnavailable();
    }
    
    @Test
    void rejectsAbsentOfflineOrMismatchedHubVersion() throws Exception {
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenReturn(null);
        assertUnavailable();
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenReturn(version);
        version.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        assertUnavailable();
        version.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        version.setId(null);
        assertUnavailable();
        version.setId(3001L);
        version.setVersion("2.0.0");
        assertUnavailable();
        version.setVersion("1.0.0");
        HubResourceReference mismatched = reference(101L, 999L, "1.0.0");
        version.setStorage(HubResourceReferenceJsonUtil.toJson(mismatched));
        assertUnavailable();
    }
    
    @Test
    void ignoresOldPreparedVersionAfterPointerSwitch() throws Exception {
        HubResourceReference next = reference(101L, 601L, "2.0.0");
        entry.setExt(HubResourceReferenceJsonUtil.toJson(next));
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setLabels(Collections.singletonMap(AiResourceConstants.LABEL_LATEST, "2.0.0"));
        entry.setVersionInfo(JacksonUtils.toJson(info));
        // The old version row may still exist after S1-6 adjusts the pointer.
        // It must not be returned, even if a repository accidentally selects it.
        assertUnavailable();
    }
    
    @Test
    void rejectsVersionFromDifferentNamespaceOrResource() {
        version.setNamespaceId("team-a");
        assertUnavailable();
        version.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        version.setName("find-skills-201");
        assertUnavailable();
        version.setName(entry.getName());
        version.setType("prompt");
        assertUnavailable();
        version.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        version.setAuthor("source-owner");
        assertUnavailable();
    }
    
    @Test
    void repositoryVersionConflictsDoNotExposeStoredDetails() throws Exception {
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenThrow(new NacosException(NacosException.CONFLICT, "raw storage details"));
        NacosException thrown = assertThrows(NacosException.class, () -> resolver.resolve(901L));
        assertEquals(NacosException.NOT_FOUND, thrown.getErrCode());
        assertTrue(!thrown.getMessage().contains("raw storage details"));
    }
    
    @Test
    void detectsUnshareOrCasAdjustDuringVersionLookup() throws Exception {
        AiResource disabled = hubMeta(901L, reference);
        disabled.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        disabled.setMetaVersion(8L);
        when(repository.findByEntryId(901L)).thenReturn(entry, disabled);
        assertUnavailable();
    }
    
    @Test
    void rejectsPublicationChangesAfterInitialRead() throws Exception {
        ResolvedHubSkillReference original = resolver.resolve(901L);
        AiResource adjusted = hubMeta(901L, reference);
        adjusted.setMetaVersion(8L);
        when(repository.findByEntryId(901L)).thenReturn(adjusted);
        assertEquals(NacosException.NOT_FOUND,
            error(() -> resolver.requireUnchanged(original)));
    }
    
    @Test
    void rejectsNullSnapshotDuringFinalCheck() {
        assertEquals(NacosException.NOT_FOUND,
            error(() -> resolver.requireUnchanged(null)));
    }
    
    @Test
    void detectsUnshareWhenRepositoryReusesTheSameMutableMetaObject() throws Exception {
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenAnswer(invocation -> {
                entry.setStatus(AiResourceConstants.META_STATUS_DISABLE);
                entry.setMetaVersion(8L);
                return version;
            });
        assertUnavailable();
    }
    
    @Test
    void detectsCasChangeWhenRepositoryReusesTheSameMutableMetaObject() throws Exception {
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenAnswer(invocation -> {
                entry.setMetaVersion(8L);
                return version;
            });
        assertUnavailable();
    }
    
    @Test
    void resolvedPublicationIsIndependentOfMutableRowsAndDescriptors() throws Exception {
        ResolvedHubSkillReference resolved = resolver.resolve(901L);
        entry.setId(902L);
        entry.setMetaVersion(8L);
        entry.setName("replacement");
        entry.setDesc("replacement description");
        entry.getGmtCreate().setTime(0L);
        version.setId(3002L);
        reference.setSourceVersionId(999L);
        HubResourceReference exported = resolved.toReference();
        exported.setSourceResourceId(999L);
        
        assertEquals(901L, resolved.getHubEntryId());
        assertEquals(7L, resolved.getHubMetaVersion());
        assertEquals(3001L, resolved.getHubVersionId());
        assertEquals("find-skills-101", resolved.getInternalName());
        assertEquals("shared", resolved.getDescription());
        assertEquals(Long.valueOf(1_720_000_000_000L), resolved.getCreatedAt());
        assertEquals(101L, resolved.toReference().getSourceResourceId());
        assertEquals(501L, resolved.toReference().getSourceVersionId());
    }
    
    @Test
    void hubDownloadCountersAreSnapshotsButDoNotChangePublicationIdentity() throws Exception {
        entry.setDownloadCount(12L);
        version.setDownloadCount(4L);
        ResolvedHubSkillReference resolved = resolver.resolve(901L);
        entry.setDownloadCount(13L);
        version.setDownloadCount(5L);
        assertEquals(12L, resolved.getDownloadCount());
        assertEquals(4L, resolved.getVersionDownloadCount());
        resolver.requireUnchanged(resolved);
    }
    
    @Test
    void versionIdReplacementIsDetectedEvenWhenMetaIsUnchanged() throws Exception {
        ResolvedHubSkillReference resolved = resolver.resolve(901L);
        version.setId(3002L);
        assertEquals(NacosException.NOT_FOUND, error(() -> resolver.requireUnchanged(resolved)));
    }
    
    @Test
    void metaReadFailuresReturnControlledErrorsWithoutStorageDetails() {
        when(repository.findByEntryId(901L))
            .thenThrow(new IllegalStateException("storage team-a/501 unavailable"));
        NacosException error = assertThrows(NacosException.class, () -> resolver.resolve(901L));
        assertEquals(NacosException.SERVER_ERROR, error.getErrCode());
        assertFalse(error.getMessage().contains("team-a"));
        assertFalse(error.getMessage().contains("501"));
    }
    
    @Test
    void versionReadRuntimeFailuresReturnControlledErrorsWithoutStorageDetails() throws Exception {
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenThrow(new IllegalStateException("raw descriptor storage"));
        NacosException error = assertThrows(NacosException.class, () -> resolver.resolve(901L));
        assertEquals(NacosException.SERVER_ERROR, error.getErrCode());
        assertFalse(error.getMessage().contains("raw descriptor storage"));
    }
    
    @Test
    void checkedVersionStorageOutageDoesNotMasqueradeAsADeletedReference() throws Exception {
        when(repository.requireReferenceVersion(eq(901L), any(HubResourceReference.class)))
            .thenThrow(new NacosException(503, "private backend details"));
        NacosException failure = assertThrows(NacosException.class, () -> resolver.resolve(901L));
        assertEquals(500, failure.getErrCode());
        assertFalse(failure.getMessage().contains("private backend"));
    }
    
    private void assertUnavailable() {
        assertEquals(NacosException.NOT_FOUND, error(() -> resolver.resolve(901L)));
    }
    
    private static int error(CheckedRunnable runnable) {
        return assertThrows(NacosException.class, runnable::run).getErrCode();
    }
    
    private static HubResourceReference reference(long sourceId, long sourceVersionId,
        String sourceVersion) throws NacosException {
        return HubResourceReferenceJsonUtil.build("team-a", sourceId, "find-skills",
            sourceVersionId, sourceVersion);
    }
    
    private static AiResource hubMeta(long entryId, HubResourceReference ref)
        throws NacosException {
        AiResource meta = new AiResource();
        meta.setId(entryId);
        meta.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        meta.setName("find-skills-" + ref.getSourceResourceId());
        meta.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        meta.setFrom("hub");
        meta.setOwner("nacos-ai-hub");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setMetaVersion(7L);
        meta.setExt(HubResourceReferenceJsonUtil.toJson(ref));
        meta.setDesc("shared");
        meta.setBizTags("[\"tool\"]");
        meta.setGmtCreate(new Timestamp(1_720_000_000_000L));
        meta.setGmtModified(new Timestamp(1_720_000_000_100L));
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setLabels(Collections.singletonMap(AiResourceConstants.LABEL_LATEST,
            ref.getSourceVersion()));
        meta.setVersionInfo(JacksonUtils.toJson(info));
        return meta;
    }
    
    private static AiResourceVersion hubVersion(long id, AiResource meta,
        HubResourceReference ref) throws NacosException {
        AiResourceVersion result = new AiResourceVersion();
        result.setId(id);
        result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        result.setName(meta.getName());
        result.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        result.setAuthor("nacos-ai-hub");
        result.setVersion(ref.getSourceVersion());
        result.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        result.setStorage(HubResourceReferenceJsonUtil.toJson(ref));
        return result;
    }
    
    @FunctionalInterface
    private interface CheckedRunnable {
        
        void run() throws NacosException;
    }
}
