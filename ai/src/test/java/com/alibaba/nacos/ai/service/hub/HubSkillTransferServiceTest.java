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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Distinguishes the Hub grant from a direct source READ grant. */
class HubSkillTransferServiceTest {
    
    private final NamespaceOperationService namespaces = mock(NamespaceOperationService.class);
    private final HubSkillTransferPermissionGuard permissions =
        mock(HubSkillTransferPermissionGuard.class);
    private final HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
    private final HubSkillTransferSourceValidator sources =
        mock(HubSkillTransferSourceValidator.class);
    private final HubSkillTransferFileService files = mock(HubSkillTransferFileService.class);
    private final HubSkillDraftTransferWriter writer = mock(HubSkillDraftTransferWriter.class);
    private final HubSkillTransferService service = new HubSkillTransferService(namespaces,
        permissions, access, sources, files, writer);
    
    private final AiResource meta = source();
    private final AiResourceVersion version = version();
    private final HubSkillTransferSourceSnapshot directSnapshot =
        new HubSkillTransferSourceSnapshot(meta, version, null);
    private final HubSkillTransferSourceSnapshot hubSnapshot =
        new HubSkillTransferSourceSnapshot(meta, version, 701L);
    
    @Test
    void hubImportDoesNotRequireSourceNamespaceRead() throws Exception {
        HubSkillTransferRequest request = hubRequest();
        ResolvedHubSkillReference publication = publication(701L, "source-a", 101L, 501L);
        when(access.resolveReadable(701L)).thenReturn(publication);
        when(sources.lookupDirectSource("source-a", "find-skills")).thenReturn(meta);
        when(sources.qualify(meta, "1.0.0", 701L)).thenReturn(hubSnapshot);
        when(sources.requireStillLive(hubSnapshot)).thenReturn(version);
        HubSkillTransferFileService.Files content = mock(HubSkillTransferFileService.Files.class);
        when(files.load(hubSnapshot, version, "target-skill", "0.0.1")).thenReturn(content);
        HubSkillTransferResult expected = new HubSkillTransferResult();
        when(writer.create(eq(hubSnapshot), eq("target-ns"), eq("target-skill"),
            eq("0.0.1"), eq(content), any(), any())).thenReturn(expected);
        
        assertEquals(expected, service.importFromHub(request));
        verify(permissions, never()).requireDirectSourceRead(any());
        verify(access).resolveReadable(701L);
        ArgumentCaptor<Map> origin = ArgumentCaptor.forClass(Map.class);
        verify(writer).create(eq(hubSnapshot), eq("target-ns"), eq("target-skill"),
            eq("0.0.1"), eq(content), origin.capture(), any());
        assertEquals("source-a", origin.getValue().get("sourceNamespaceId"));
        assertEquals(701L, origin.getValue().get("hubEntryId"));
    }
    
    @Test
    void directCopyRequiresSourceReadButNoHubGrant() throws Exception {
        HubSkillTransferRequest request = directRequest();
        when(sources.lookupDirectSource("source-a", "find-skills")).thenReturn(meta);
        when(sources.qualify(meta, "1.0.0", null)).thenReturn(directSnapshot);
        when(sources.requireStillLive(directSnapshot)).thenReturn(version);
        HubSkillTransferFileService.Files content = mock(HubSkillTransferFileService.Files.class);
        when(files.load(directSnapshot, version, "target-skill", "0.0.1"))
            .thenReturn(content);
        when(writer.create(eq(directSnapshot), eq("target-ns"), eq("target-skill"),
            eq("0.0.1"), eq(content), any(), any()))
            .thenReturn(new HubSkillTransferResult());
        
        service.copyDirect(request);
        verify(permissions, org.mockito.Mockito.times(2)).requireDirectSourceRead(meta);
        verifyNoInteractions(access);
        verify(writer).create(eq(directSnapshot), eq("target-ns"), eq("target-skill"),
            eq("0.0.1"), eq(content), any(), any());
    }
    
    @Test
    void missingTargetWriteStopsBeforeAnySourceLookupOrFileRead() throws Exception {
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .when(permissions).requireTargetWrite("target-ns", "target-skill");
        assertEquals(NacosException.NO_RIGHT,
            assertThrows(NacosException.class, () -> service.copyDirect(directRequest()))
                .getErrCode());
        verifyNoInteractions(access, sources, files, writer);
    }
    
    @Test
    void noSourceReadCannotEnterDirectCopyWriter() throws Exception {
        when(sources.lookupDirectSource("source-a", "find-skills")).thenReturn(meta);
        doThrow(new NacosException(NacosException.NO_RIGHT, "source denied"))
            .when(permissions).requireDirectSourceRead(meta);
        assertEquals(NacosException.NO_RIGHT,
            assertThrows(NacosException.class, () -> service.copyDirect(directRequest()))
                .getErrCode());
        verifyNoInteractions(files);
        verify(writer, never()).create(any(), any(), any(), any(), any(), any(), any());
    }
    
    @Test
    void sameSourceNameFromTwoNamespacesKeepsCorrectEntryAndOrigin() throws Exception {
        AiResource other = source();
        other.setId(201L);
        other.setNamespaceId("source-b");
        AiResourceVersion otherVersion = version();
        otherVersion.setId(601L);
        otherVersion.setNamespaceId("source-b");
        HubSkillTransferSourceSnapshot pinnedOther =
            new HubSkillTransferSourceSnapshot(other, otherVersion, 802L);
        ResolvedHubSkillReference publishedOther =
            publication(802L, "source-b", 201L, 601L);
        when(access.resolveReadable(802L)).thenReturn(publishedOther);
        when(sources.lookupDirectSource("source-b", "find-skills")).thenReturn(other);
        when(sources.qualify(other, "1.0.0", 802L)).thenReturn(pinnedOther);
        when(sources.requireStillLive(pinnedOther)).thenReturn(otherVersion);
        HubSkillTransferFileService.Files content = mock(HubSkillTransferFileService.Files.class);
        when(files.load(pinnedOther, otherVersion, "target-skill", "0.0.1"))
            .thenReturn(content);
        when(writer.create(eq(pinnedOther), eq("target-ns"), eq("target-skill"),
            eq("0.0.1"), eq(content), any(), any()))
            .thenReturn(new HubSkillTransferResult());
        HubSkillTransferRequest req = hubRequest();
        req.setHubEntryId(802L);
        service.importFromHub(req);
        ArgumentCaptor<Map> origin = ArgumentCaptor.forClass(Map.class);
        verify(writer).create(eq(pinnedOther), eq("target-ns"), eq("target-skill"),
            eq("0.0.1"), eq(content), origin.capture(), any());
        assertEquals("source-b", origin.getValue().get("sourceNamespaceId"));
        assertEquals(201L, origin.getValue().get("sourceResourceId"));
        assertEquals(802L, origin.getValue().get("hubEntryId"));
    }
    
    @Test
    void targetConflictStopsBeforeSourceLookup() throws Exception {
        doThrow(new NacosException(409, "already exists")).when(writer)
            .requireAvailable("target-ns", "target-skill", "0.0.1");
        assertEquals(409, assertThrows(NacosException.class,
            () -> service.importFromHub(hubRequest())).getErrCode());
        verifyNoInteractions(access, sources, files);
    }
    
    @Test
    void callerMutationCannotChangeTargetDuringTransferOrFinalChecks() throws Exception {
        HubSkillTransferRequest request = directRequest();
        doAnswer(i -> {
            request.setTargetNamespaceId("unapproved-ns");
            request.setTargetName("unapproved-name");
            return null;
        }).when(permissions).requireTargetWrite("target-ns", "target-skill");
        when(sources.lookupDirectSource("source-a", "find-skills")).thenReturn(meta);
        when(sources.qualify(meta, "1.0.0", null)).thenReturn(directSnapshot);
        when(sources.requireStillLive(directSnapshot)).thenReturn(version);
        HubSkillTransferFileService.Files content = mock(HubSkillTransferFileService.Files.class);
        when(files.load(directSnapshot, version, "target-skill", "0.0.1")).thenReturn(content);
        when(writer.create(eq(directSnapshot), eq("target-ns"), eq("target-skill"), eq("0.0.1"),
            eq(content), any(), any())).thenAnswer(i -> {
                i.<HubSkillDraftTransferWriter.RunnableWithNacosException>getArgument(6).run();
                return new HubSkillTransferResult();
            });
        service.copyDirect(request);
        verify(permissions, org.mockito.Mockito.times(2)).requireTargetWrite("target-ns",
            "target-skill");
        verify(permissions, never()).requireTargetWrite("unapproved-ns", "unapproved-name");
    }
    
    private ResolvedHubSkillReference publication(long hubId, String ns,
        long sourceId, long sourceVersionId) throws NacosException {
        AiResource hubMeta = new AiResource();
        hubMeta.setId(hubId);
        hubMeta.setMetaVersion(3L);
        hubMeta.setName("find-skills-" + sourceId);
        AiResourceVersion hubVersion = new AiResourceVersion();
        hubVersion.setId(hubId + 1000L);
        return new ResolvedHubSkillReference(hubMeta, hubVersion,
            HubResourceReferenceJsonUtil.build(ns, sourceId, "find-skills",
                sourceVersionId, "1.0.0"));
    }
    
    private HubSkillTransferRequest directRequest() {
        HubSkillTransferRequest r = hubRequest();
        r.setHubEntryId(null);
        r.setSourceNamespaceId("source-a");
        r.setSourceName("find-skills");
        r.setSourceVersion("1.0.0");
        return r;
    }
    
    private HubSkillTransferRequest hubRequest() {
        HubSkillTransferRequest r = new HubSkillTransferRequest();
        r.setHubEntryId(701L);
        r.setTargetNamespaceId("target-ns");
        r.setTargetName("target-skill");
        return r;
    }
    
    private static AiResource source() {
        AiResource s = new AiResource();
        s.setNamespaceId("source-a");
        s.setName("find-skills");
        s.setType("skill");
        s.setId(101L);
        return s;
    }
    
    private static AiResourceVersion version() {
        AiResourceVersion v = new AiResourceVersion();
        v.setId(501L);
        v.setNamespaceId("source-a");
        v.setName("find-skills");
        v.setVersion("1.0.0");
        return v;
    }
}
