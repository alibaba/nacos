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

import com.alibaba.nacos.ai.event.SkillDownloadEvent;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.NotifyCenter;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Read API never returns an unresolved descriptor or bypasses the post-read fence. */
class HubSkillReadServiceImplTest {
    
    private final HubSkillDirectoryService directory = mock(HubSkillDirectoryService.class);
    private final HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
    private final HubSkillSourceContentReader loader = mock(HubSkillSourceContentReader.class);
    private final HubSkillReadServiceImpl service =
        new HubSkillReadServiceImpl(directory, access, loader);
    
    @Test
    void versionMismatchFailsBeforeReadingContentOrCountingDownload() throws Exception {
        when(access.resolveReadable(5L)).thenReturn(publication());
        try (MockedStatic<NotifyCenter> events =
            org.mockito.Mockito.mockStatic(NotifyCenter.class)) {
            assertEquals(NacosException.NOT_FOUND,
                assertThrows(NacosException.class, () -> service.getContent(5L, "2.0.0"))
                    .getErrCode());
            assertEquals(NacosException.NOT_FOUND,
                assertThrows(NacosException.class, () -> service.download(5L, "2.0.0"))
                    .getErrCode());
            verifyNoInteractions(loader);
            events.verifyNoInteractions();
        }
    }
    
    @Test
    void detailWithoutSourceReadHidesDirectSourceIdentity() throws Exception {
        ResolvedHubSkillReference publication = publication();
        AiResource source = new AiResource();
        when(access.resolveReadable(5L)).thenReturn(publication);
        when(access.requireReadableVersion(publication)).thenReturn(eligible(publication, source));
        HubSkillSummary summary = new HubSkillSummary();
        summary.setHubEntryId(5L);
        summary.setDisplayName("same-name");
        summary.setSourceDisplayName("Hub Test A");
        summary.setAuthor("owner-is-not-author");
        summary.setDownloadCount(57L);
        when(directory.toSummary(publication, source)).thenReturn(summary);
        HubSkillDetail detail = service.getDetail(5L);
        assertEquals(5L, detail.getHubEntryId());
        assertEquals("same-name", detail.getDisplayName());
        assertEquals("Hub Test A", detail.getSourceDisplayName());
        assertEquals("version-author", detail.getAuthor());
        assertEquals("online", detail.getVersionStatus());
        assertEquals(2L, detail.getVersionDownloadCount());
        assertEquals("Pinned version commit", detail.getCommitMsg());
        assertEquals(57L, detail.getDownloadCount());
        assertFalse(detail.isSourceReadable());
        assertNull(detail.getSourceNamespaceId());
        assertNull(detail.getSourceName());
        verify(access).requireStillReadable(publication);
    }
    
    @Test
    void detailWithSourceReadAllowsDirectLinkOnly() throws Exception {
        ResolvedHubSkillReference publication = publication();
        AiResource source = new AiResource();
        when(access.resolveReadable(5L)).thenReturn(publication);
        when(access.requireReadableVersion(publication)).thenReturn(eligible(publication, source));
        when(access.canReadSourceDetail(publication)).thenReturn(true);
        when(directory.toSummary(publication, source)).thenReturn(new HubSkillSummary());
        HubSkillDetail detail = service.getDetail(5L);
        assertTrue(detail.isSourceReadable());
        assertEquals("source-a", detail.getSourceNamespaceId());
        assertEquals("same-name", detail.getSourceName());
    }
    
    @Test
    void contentIsFromPinnedSourceAndRecheckedBeforeReturn() throws Exception {
        ResolvedHubSkillReference publication = publication();
        when(access.resolveReadable(5L)).thenReturn(publication);
        HubSkillContent expected = new HubSkillContent();
        expected.setSkillMd("# safe");
        when(loader.load(publication)).thenReturn(expected);
        assertEquals(expected, service.getContent(5L));
        verify(access).requireStillReadable(publication);
    }
    
    @Test
    void withdrawalAfterLoadFailsClosed() throws Exception {
        ResolvedHubSkillReference publication = publication();
        when(access.resolveReadable(5L)).thenReturn(publication);
        when(loader.load(publication)).thenReturn(new HubSkillContent());
        doThrow(new NacosException(NacosException.NOT_FOUND, "revoked"))
            .when(access).requireStillReadable(publication);
        assertEquals(NacosException.NOT_FOUND,
            assertThrows(NacosException.class, () -> service.getContent(5L)).getErrCode());
    }
    
    private ResolvedHubSkillReference publication() throws Exception {
        AiResource hub = new AiResource();
        hub.setId(5L);
        hub.setMetaVersion(8L);
        hub.setName("same-name-101");
        AiResourceVersion version = new AiResourceVersion();
        version.setId(99L);
        version.setDownloadCount(2L);
        return new ResolvedHubSkillReference(hub, version,
            HubResourceReferenceJsonUtil.build("source-a", 101L, "same-name", 199L, "1.0.0"));
    }
    
    private HubEligibleSkillVersion eligible(ResolvedHubSkillReference publication,
        AiResource source) throws NacosException {
        AiResourceVersion version = new AiResourceVersion();
        version.setAuthor("version-author");
        version.setVersion("1.0.0");
        version.setStatus("online");
        version.setDownloadCount(7L);
        version.setDesc("Pinned version commit");
        source.setVersionInfo("{\"labels\":{\"latest\":\"2.0.0\",\"stable\":\"1.0.0\"}}");
        return new HubEligibleSkillVersion(source, version, publication.toReference());
    }
    
    @Test
    void labelsOnlyRevealNamesBoundToTheSharedVersion() throws Exception {
        ResolvedHubSkillReference publication = publication();
        AiResource source = new AiResource();
        when(access.resolveReadable(5L)).thenReturn(publication);
        when(access.requireReadableVersion(publication)).thenReturn(eligible(publication, source));
        when(directory.toSummary(publication, source)).thenReturn(new HubSkillSummary());
        assertEquals(Collections.singletonList("stable"), service.getDetail(5L).getVersionLabels());
    }
    
    @Test
    void downloadCountsOnlyTheHubEntryAndPinnedHubVersionAfterTheFinalReadFence() throws Exception {
        ResolvedHubSkillReference publication = publication();
        when(access.resolveReadable(5L)).thenReturn(publication);
        HubSkillDownload archive = new HubSkillDownload();
        when(loader.download(publication)).thenReturn(archive);
        try (MockedStatic<NotifyCenter> events =
            org.mockito.Mockito.mockStatic(NotifyCenter.class)) {
            events.when(() -> NotifyCenter.publishEvent(
                org.mockito.ArgumentMatchers.any(SkillDownloadEvent.class)))
                .thenAnswer(invocation -> {
                    verify(access).requireStillReadable(publication);
                    SkillDownloadEvent event = invocation.getArgument(0);
                    assertEquals(AiConstants.Hub.NAMESPACE_ID, event.getNamespaceId());
                    assertEquals("same-name-101", event.getName());
                    assertEquals("1.0.0", event.getVersion());
                    return true;
                });
            assertEquals(archive, service.download(5L));
            events.verify(() -> NotifyCenter.publishEvent(
                org.mockito.ArgumentMatchers.any(SkillDownloadEvent.class)));
        }
    }
    
    @Test
    void revokedDownloadNeverReturnsBytesOrCountsAnEvent() throws Exception {
        ResolvedHubSkillReference publication = publication();
        when(access.resolveReadable(5L)).thenReturn(publication);
        when(loader.download(publication)).thenReturn(new HubSkillDownload());
        doThrow(new NacosException(404, "revoked")).when(access).requireStillReadable(publication);
        try (MockedStatic<NotifyCenter> events =
            org.mockito.Mockito.mockStatic(NotifyCenter.class)) {
            assertThrows(NacosException.class, () -> service.download(5L));
            events.verifyNoInteractions();
        }
    }
    
    @Test
    void failedArchiveReadDoesNotEmitADownloadEvent() throws Exception {
        ResolvedHubSkillReference publication = publication();
        when(access.resolveReadable(5L)).thenReturn(publication);
        when(loader.download(publication)).thenThrow(new NacosException(500, "storage failure"));
        try (MockedStatic<NotifyCenter> events =
            org.mockito.Mockito.mockStatic(NotifyCenter.class)) {
            assertThrows(NacosException.class, () -> service.download(5L));
            events.verifyNoInteractions();
        }
    }
    
}
