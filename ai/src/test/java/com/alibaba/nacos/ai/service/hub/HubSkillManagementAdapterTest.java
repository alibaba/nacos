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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillDownload;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class HubSkillManagementAdapterTest {
    
    private final HubSkillReadService reads = mock(HubSkillReadService.class);
    private final HubSkillDirectoryService directory = mock(HubSkillDirectoryService.class);
    private final HubSkillManagementAdapter adapter =
        new HubSkillManagementAdapter(reads, directory);
    
    @Test
    void managementSortOptionReachesDirectoryWithoutChangingFilters() throws Exception {
        for (String orderBy : new String[] {null, "download_count"}) {
            doAnswer(invocation -> {
                HubSkillListRequest request = invocation.getArgument(0);
                assertEquals(orderBy, request.getOrderBy());
                assertEquals(2, request.getPageNo());
                assertEquals(5, request.getPageSize());
                Page<com.alibaba.nacos.api.ai.model.hub.HubSkillSummary> empty = new Page<>();
                empty.setPageItems(List.of());
                return empty;
            }).when(directory).list(any(), any());
            assertEquals(0, adapter
                .list("name", "accurate", orderBy, "owner", "PUBLIC", "tag", 2, 5).getTotalCount());
        }
    }
    
    @Test
    void metadataUsesExistingShapeAndOnlyPinnedVersionWithIndependentHubCounters()
        throws Exception {
        HubSkillDetail shared = new HubSkillDetail();
        shared.setHubEntryId(9007199254740993L);
        shared.setDisplayName("same-name");
        shared.setSourceDisplayName("Team A");
        shared.setSharedVersion("1.0.0");
        shared.setVersionStatus("online");
        shared.setAuthor("author");
        shared.setSharedAt(123L);
        shared.setDownloadCount(57L);
        shared.setVersionDownloadCount(2L);
        shared.setTags(List.of("coding"));
        shared.setVersionLabels(List.of("stable"));
        when(reads.getDetail(9007199254740993L)).thenReturn(shared);
        SkillMeta result = adapter.getDetail("9007199254740993");
        assertEquals(AiConstants.Hub.NAMESPACE_ID, result.getNamespaceId());
        assertEquals("9007199254740993", result.getName());
        assertEquals("HUB", result.getDiscoverySource());
        assertEquals("same-name", result.getDisplayName());
        assertEquals("1.0.0", result.getVersion());
        assertEquals(57L, result.getDownloadCount());
        assertEquals("[\"coding\"]", result.getBizTags());
        assertEquals(Map.of("stable", "1.0.0"), result.getLabels());
        assertEquals(1, result.getVersions().size());
        assertEquals(2L, result.getVersions().get(0).getDownloadCount());
        assertFalse(result.isWritable());
        assertTrue(result.isEnable());
        assertNull(result.getSourceNamespaceId());
        assertNull(result.getOrigin());
    }
    
    @Test
    void previewUsesSharedIdentityAndNeverCountsDownload() throws Exception {
        HubSkillContent content = new HubSkillContent();
        content.setSkillMd("# shared");
        content.setResource(Map.of());
        when(reads.getContent(5L, "1.0.0")).thenReturn(content);
        Skill skill = adapter.getVersion("5", "1.0.0");
        assertEquals("5", skill.getName());
        assertEquals(AiConstants.Hub.NAMESPACE_ID, skill.getNamespaceId());
        assertEquals("# shared", skill.getSkillMd());
        verify(reads).getContent(5L, "1.0.0");
    }
    
    @Test
    void downloadPreservesOriginalBytesAndDisplayName() throws Exception {
        HubSkillDownload shared = new HubSkillDownload();
        shared.setHubEntryId(5L);
        shared.setDisplayName("same-name");
        shared.setSharedVersion("1.0.0");
        shared.setZipBytes(new byte[] {0, (byte) 255});
        when(reads.download(5L, "1.0.0")).thenReturn(shared);
        SkillDownload result = adapter.download("5", "1.0.0");
        assertEquals("same-name", result.getDisplayName());
        assertEquals("1.0.0", result.getResolvedVersion());
        assertEquals(5L, result.getHubEntryId());
        assertArrayEquals(shared.getZipBytes(), result.getZipBytes());
    }
    
    @Test
    void sourceDeepLinksRemainAbsentForOrdinaryMetadataAndUnauthorizedHubReaders() {
        SkillMeta meta = new SkillMeta();
        meta.setSourceNamespaceId("private-id");
        meta.setSourceName("private-name");
        Map<?, ?> json = JacksonUtils.toObj(JacksonUtils.toJson(meta), Map.class);
        assertFalse(json.containsKey("sourceNamespaceId"));
        assertFalse(json.containsKey("sourceName"));
        assertFalse(json.containsKey("sourceReadable"));
        meta.setSourceReadable(false);
        assertNull(meta.getSourceNamespaceId());
        meta.setSourceReadable(true);
        assertEquals("private-id", meta.getSourceNamespaceId());
        assertEquals("private-name", meta.getSourceName());
    }
    
    @Test
    void onlyCanonicalPositiveOpaqueEntryIdsReachSharedReadService() {
        for (String name : new String[] {"same-name", "0", "-1", "01", "+1",
            "9223372036854775808"}) {
            assertThrows(NacosException.class, () -> adapter.getDetail(name));
            assertThrows(NacosException.class, () -> adapter.getVersion(name, null));
            assertThrows(NacosException.class, () -> adapter.download(name, null));
        }
        verifyNoInteractions(reads, directory);
    }
}
