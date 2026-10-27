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
import com.alibaba.nacos.ai.form.search.client.AiResourcePageSearchForm;
import com.alibaba.nacos.ai.form.skills.client.SkillQueryForm;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Shared search/download preserve opaque identity, publication fences and independent counts. */
class HubSkillClientServiceTest {
    
    private final HubSkillReadService reads = mock(HubSkillReadService.class);
    
    private final HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
    
    private final HubSkillSourceContentReader contents = mock(HubSkillSourceContentReader.class);
    
    private final HubSkillClientService service =
        new HubSkillClientService(reads, access, contents);
    
    @Test
    void sharedSearchPreservesSameDisplayNamesAndOpaqueIdentities() throws Exception {
        HubSkillSummary one = summary(41L);
        HubSkillSummary two = summary(42L);
        Page<HubSkillSummary> page = new Page<>();
        page.setPageNumber(2);
        page.setTotalCount(12);
        page.setPagesAvailable(6);
        page.setPageItems(List.of(one, two));
        when(reads.list(any())).thenReturn(page);
        Page<com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo> result =
            service.search(new AiResourcePageSearchForm(), 2, 2);
        assertEquals(12, result.getTotalCount());
        assertEquals(2, result.getPageNumber());
        assertEquals("41", result.getPageItems().get(0).getName());
        assertEquals("42", result.getPageItems().get(1).getName());
        assertEquals("same-name", result.getPageItems().get(0).getDisplayName());
        assertEquals("HUB", result.getPageItems().get(0).getDiscoverySource());
        assertEquals(AiConstants.Hub.NAMESPACE_ID, result.getPageItems().get(0).getNamespaceId());
        assertEquals(7L, result.getPageItems().get(0).getDownloadCount());
        org.mockito.Mockito.verify(reads).list(org.mockito.ArgumentMatchers.argThat(
            request -> "download_count".equals(request.getOrderBy())));
    }
    
    @Test
    void invalidHandleDoesNotProbeSources() {
        for (String name : List.of("same-name", "0", "-1", "01", "9223372036854775808")) {
            assertThrows(NacosException.class, () -> service.download(form(name)));
        }
        verifyNoInteractions(access, contents);
    }
    
    @Test
    void matchingMd5Returns304WithoutDownloadingOrCounting() throws Exception {
        ResolvedHubSkillReference publication = prepare("{\"contentMd5\":\"cached\"}");
        when(contents.readContentMd5(publication)).thenReturn("cached");
        SkillQueryForm form = form("42");
        form.setMd5("cached");
        try (MockedStatic<NotifyCenter> events = mockStatic(NotifyCenter.class)) {
            var response = service.download(form);
            assertEquals(304, response.getStatusCode().value());
            assertNull(response.getBody());
            assertNull(response.getHeaders().getContentType());
            assertNull(response.getHeaders().getFirst("X-Nacos-Skill-Resolved-Version"));
            org.mockito.Mockito.verify(contents).readContentMd5(publication);
            org.mockito.Mockito.verifyNoMoreInteractions(contents);
            events.verifyNoInteractions();
        }
    }
    
    @Test
    void actualDownloadCountsHubAndNeverSource() throws Exception {
        ResolvedHubSkillReference publication = prepare("{}");
        HubSkillDownload download = new HubSkillDownload();
        download.setDisplayName("same-name");
        download.setZipBytes(new byte[] {1, 2});
        when(contents.download(publication)).thenReturn(download);
        try (MockedStatic<NotifyCenter> events = mockStatic(NotifyCenter.class)) {
            var response = service.download(form("42"));
            assertEquals(200, response.getStatusCode().value());
            assertEquals("1.0.0", response.getHeaders().getFirst("X-Nacos-Skill-Resolved-Version"));
            events.verify(() -> NotifyCenter.publishEvent(org.mockito.ArgumentMatchers.argThat(
                (SkillDownloadEvent event) -> AiConstants.Hub.NAMESPACE_ID
                    .equals(event.getNamespaceId())
                    && "same-name-101".equals(event.getName())
                    && "1.0.0".equals(event.getVersion()))));
        }
    }
    
    @Test
    void legacyDigestBackfillStillTransfersOnceAndReturnsFingerprint() throws Exception {
        ResolvedHubSkillReference publication = prepare("{}");
        HubSkillDownload download = new HubSkillDownload();
        download.setDisplayName("same-name");
        download.setZipBytes(new byte[] {1, 2});
        when(contents.download(publication)).thenReturn(download);
        when(contents.backfillContentMd5(publication)).thenReturn("computed");
        SkillQueryForm form = form("42");
        form.setMd5("computed");
        try (MockedStatic<NotifyCenter> events = mockStatic(NotifyCenter.class)) {
            var response = service.download(form);
            assertEquals(200, response.getStatusCode().value());
            assertEquals("computed", response.getHeaders().getFirst("X-Nacos-Skill-Md5"));
            org.mockito.Mockito.verify(contents).backfillContentMd5(publication);
        }
    }
    
    @Test
    void unavailablePinnedVersionCannotFallBackToSourceLatest() throws Exception {
        prepare("{}");
        SkillQueryForm form = form("42");
        form.setVersion("2.0.0");
        assertEquals(NacosException.NOT_FOUND,
            assertThrows(NacosException.class, () -> service.download(form)).getErrCode());
        verifyNoInteractions(contents);
    }
    
    @Test
    void revocationAfterLoadingDoesNotCountDownload() throws Exception {
        ResolvedHubSkillReference publication = prepare("{}");
        when(contents.download(publication)).thenReturn(new HubSkillDownload());
        doThrow(new NacosException(NacosException.NOT_FOUND, "withdrawn"))
            .when(access).requireStillReadable(publication);
        try (MockedStatic<NotifyCenter> events = mockStatic(NotifyCenter.class)) {
            assertThrows(NacosException.class, () -> service.download(form("42")));
            events.verifyNoInteractions();
        }
    }
    
    private ResolvedHubSkillReference prepare(String storage) throws Exception {
        AiResource meta = new AiResource();
        meta.setId(42L);
        meta.setMetaVersion(1L);
        meta.setName("same-name-101");
        AiResourceVersion hubVersion = new AiResourceVersion();
        hubVersion.setId(100L);
        var reference =
            HubResourceReferenceJsonUtil.build("source-a", 101L, "same-name", 199L, "1.0.0");
        var publication = new ResolvedHubSkillReference(meta, hubVersion, reference);
        AiResourceVersion sourceVersion = new AiResourceVersion();
        sourceVersion.setStorage(storage);
        when(access.resolveReadable(42L)).thenReturn(publication);
        when(access.requireReadableVersion(publication)).thenReturn(
            new HubEligibleSkillVersion(new AiResource(), sourceVersion, reference));
        return publication;
    }
    
    private SkillQueryForm form(String name) {
        SkillQueryForm form = new SkillQueryForm();
        form.setName(name);
        form.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        return form;
    }
    
    private HubSkillSummary summary(long id) {
        HubSkillSummary entry = new HubSkillSummary();
        entry.setHubEntryId(id);
        entry.setDisplayName("same-name");
        entry.setSharedVersion("1.0.0");
        entry.setDownloadCount(7L);
        return entry;
    }
}
