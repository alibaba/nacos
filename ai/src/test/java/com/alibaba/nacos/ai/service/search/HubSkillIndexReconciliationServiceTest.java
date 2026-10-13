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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.hub.HubSkillReferenceResolver;
import com.alibaba.nacos.ai.service.hub.HubSkillSourceReadValidator;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Canonical invalidation and missing-projection repair tests. */
class HubSkillIndexReconciliationServiceTest {
    
    private final HubEntryStorage storage = mock(HubEntryStorage.class);
    private final AiResourceSearchRepository repository = mock(AiResourceSearchRepository.class);
    private final HubSkillReferenceResolver resolver = mock(HubSkillReferenceResolver.class);
    private final HubSkillSourceReadValidator source = mock(HubSkillSourceReadValidator.class);
    private final HubSkillIndexReconciliationService service =
        new HubSkillIndexReconciliationService(storage, repository, resolver, source);
    
    @Test
    void orphanedIndexIsDeletedWithoutMutatingSource() throws Exception {
        AiResourceSearchDocument document = document(11L, "find-skills-101", "1.0.0");
        when(repository.scanEntries(eq(AiConstants.Hub.NAMESPACE_ID),
            eq(Collections.singletonList("skill")),
            anyLong(), eq(256))).thenReturn(Collections.singletonList(document));
        when(storage.findByInternalName("find-skills-101")).thenReturn(null);
        service.reconcile();
        verify(repository).deleteByResourceVersion(AiConstants.Hub.NAMESPACE_ID, "skill",
            "find-skills-101", "1.0.0");
    }
    
    @Test
    void newEnabledHubEntryWithoutIndexSchedulesReprojection() throws Exception {
        AiResource meta = new AiResource();
        meta.setId(5L);
        meta.setName("find-skills-101");
        when(storage.maxEntryId()).thenReturn(5L);
        when(storage.nextEntryId(0L, 5L)).thenReturn(5L);
        when(storage.entriesInIdRange(5L, 5L)).thenReturn(Collections.singletonList(meta));
        // The source reader is mocked here; this test isolates index reconciliation.
        com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference publication =
            mock(com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference.class);
        when(publication.getInternalName()).thenReturn(meta.getName());
        when(resolver.resolve(5L)).thenReturn(publication);
        AiResourceIndexMaintenanceService maintenance =
            mock(AiResourceIndexMaintenanceService.class);
        service.setMaintenance(maintenance);
        service.reconcile();
        verify(maintenance).scheduleReconciliation(AiConstants.Hub.NAMESPACE_ID, "skill",
            "find-skills-101");
    }
    
    private AiResourceSearchDocument document(long id, String name, String version) {
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setId(id);
        document.setResourceType("skill");
        document.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        document.setResourceName(name);
        document.setResourceVersion(version);
        return document;
    }
    
    @Test
    void transientResolverFailureDoesNotDeleteAValidDocument() throws Exception {
        AiResource meta = new AiResource();
        meta.setId(5L);
        meta.setName("find-skills-101");
        AiResourceSearchDocument document = document(11L, meta.getName(), "1.0.0");
        document.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        when(repository.scanEntries(eq(AiConstants.Hub.NAMESPACE_ID),
            eq(Collections.singletonList("skill")),
            anyLong(), eq(256))).thenReturn(Collections.singletonList(document));
        when(storage.findByInternalName(meta.getName())).thenReturn(meta);
        when(resolver.resolve(5L))
            .thenThrow(new com.alibaba.nacos.api.exception.NacosException(500, "transient"));
        org.junit.jupiter.api.Assertions
            .assertThrows(com.alibaba.nacos.api.exception.NacosException.class, service::reconcile);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteByResourceVersion(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
    
    @Test
    void oldProjectionWithSourceIdsIsRemovedThroughTheCanonicalVectorAwareService()
        throws Exception {
        AiResource meta = new AiResource();
        meta.setId(5L);
        meta.setName("find-skills-101");
        AiResourceSearchDocument document = document(11L, meta.getName(), "1.0.0");
        document.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        document.setDisplayName("find-skills");
        document.setMetadata(
            "{\"hubEntryId\":\"5\",\"hubMetaVersion\":\"8\",\"origin\":\"HUB\",\"sourceNamespaceId\":\"secret\"}");
        com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference publication =
            mock(com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference.class);
        when(publication.getInternalName()).thenReturn(meta.getName());
        when(publication.getSourceVersion()).thenReturn("1.0.0");
        when(publication.getSourceName()).thenReturn("find-skills");
        when(publication.getHubEntryId()).thenReturn(5L);
        when(publication.getHubMetaVersion()).thenReturn(8L);
        when(resolver.resolve(5L)).thenReturn(publication);
        when(storage.findByInternalName(meta.getName())).thenReturn(meta);
        when(repository.scanEntries(eq(AiConstants.Hub.NAMESPACE_ID),
            eq(Collections.singletonList("skill")),
            anyLong(), eq(256))).thenReturn(Collections.singletonList(document));
        AiResourceIndexService indexService = mock(AiResourceIndexService.class);
        service.setIndexService(indexService);
        service.reconcile();
        verify(indexService).deleteResourceVersion(AiConstants.Hub.NAMESPACE_ID, "skill",
            meta.getName(), "1.0.0");
    }
    
    @Test
    void safeCurrentProjectionIsKeptAndOldMetadataRevisionIsInvalidated() throws Exception {
        AiResource meta = new AiResource();
        meta.setId(5L);
        meta.setName("find-skills-101");
        AiResourceSearchDocument document = document(11L, meta.getName(), "1.0.0");
        document.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        document.setDisplayName("find-skills");
        document.setMetadata("{\"hubEntryId\":\"5\",\"hubMetaVersion\":\"8\",\"origin\":\"HUB\"}");
        com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference publication =
            mock(com.alibaba.nacos.ai.service.hub.ResolvedHubSkillReference.class);
        when(publication.getInternalName()).thenReturn(meta.getName());
        when(publication.getSourceVersion()).thenReturn("1.0.0");
        when(publication.getSourceName()).thenReturn("find-skills");
        when(publication.getHubEntryId()).thenReturn(5L);
        when(publication.getHubMetaVersion()).thenReturn(8L);
        when(resolver.resolve(5L)).thenReturn(publication);
        when(storage.findByInternalName(meta.getName())).thenReturn(meta);
        when(repository.scanEntries(eq(AiConstants.Hub.NAMESPACE_ID),
            eq(Collections.singletonList("skill")),
            anyLong(), eq(256))).thenReturn(Collections.singletonList(document));
        service.reconcile();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteByResourceVersion(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        document.setStatus(AiResourceSearchConstants.STATUS_PENDING);
        service.reconcile();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteByResourceVersion(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        when(publication.getHubMetaVersion()).thenReturn(9L);
        service.reconcile();
        verify(repository).deleteByResourceVersion(AiConstants.Hub.NAMESPACE_ID, "skill",
            meta.getName(), "1.0.0");
    }
}
