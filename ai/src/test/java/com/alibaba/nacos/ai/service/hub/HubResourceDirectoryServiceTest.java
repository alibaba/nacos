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

import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Totals and sort order use eligible Hub rows, not source dates or source popularity. */
class HubResourceDirectoryServiceTest {
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void eligibilityAndResourceFiltersApplyBeforeBothSortModesAndPagination(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        Page<HubResourceDirectoryService.Entry> updated = f.directory.list(type, null,
            Collections.emptyList(), null, null, 1, 1);
        assertEquals(2, updated.getTotalCount());
        assertEquals(2, updated.getPagesAvailable());
        assertEquals(102L, updated.getPageItems().get(0).hub().getId());
        Page<HubResourceDirectoryService.Entry> popular = f.directory.list(type, null,
            Collections.emptyList(), null, "download_count", 1, 1);
        assertEquals(101L, popular.getPageItems().get(0).hub().getId());
        Page<HubResourceDirectoryService.Entry> second = f.directory.list(type, null,
            Collections.emptyList(), null, "download_count", 2, 1);
        assertEquals(102L, second.getPageItems().get(0).hub().getId());
        assertTrue(f.directory.list(type, null, Collections.emptyList(), null, null, 3, 1)
            .getPageItems().isEmpty());
        Page<HubResourceDirectoryService.Entry> filtered = f.directory.list(type, null,
            Collections.singletonList("tag-a"), "alice", null, 1, 10,
            entry -> entry.reference().getSourceName().contains("one"));
        assertEquals(1, filtered.getTotalCount());
        assertEquals(101L, filtered.getPageItems().get(0).hub().getId());
    }
    
    @Test
    void disabledHubReturnsEmptyWithoutStorageOrAdmissionAndBadPagesAreRejected() throws Exception {
        Fixture f = new Fixture(HubResourceType.AGENTSPEC);
        try (MockedStatic<HubConfig> config = mockStatic(HubConfig.class)) {
            config.when(HubConfig::isEnabled).thenReturn(false);
            assertEquals(0, f.directory.list(HubResourceType.AGENTSPEC, null,
                Collections.emptyList(), null, null, 1, 10).getTotalCount());
        }
        verifyNoInteractions(f.storage, f.permissions, f.publications);
        assertThrows(NacosException.class, () -> f.directory.list(HubResourceType.AGENTSPEC,
            null, Collections.emptyList(), null, "unsupported", 1, 10));
        assertThrows(NacosException.class, () -> f.directory.list(HubResourceType.AGENTSPEC,
            null, Collections.emptyList(), null, null, 0, 10));
    }
    
    private static final class Fixture {
        
        private final HubEntryStorage storage = mock(HubEntryStorage.class);
        private final HubResourcePublicationService publications =
            mock(HubResourcePublicationService.class);
        private final HubResourcePermissionGuard permissions =
            mock(HubResourcePermissionGuard.class);
        private final HubResourceDirectoryService directory =
            new HubResourceDirectoryService(storage, publications, permissions);
        
        private Fixture(HubResourceType type) throws Exception {
            AiResource first = hub(101L, 9L, "2020-01-01 00:00:00");
            AiResource second = hub(102L, 1L, "2020-01-02 00:00:00");
            AiResource revoked = hub(103L, 999L, "2020-01-03 00:00:00");
            List<AiResource> rows = Arrays.asList(first, second, revoked);
            when(storage.maxEntryId(type)).thenReturn(103L);
            when(storage.nextEntryId(type, 0, 103)).thenReturn(101L);
            when(storage.entriesInIdRange(type, 101, 103)).thenReturn(rows);
            when(publications.inspectPinned(eq(type), anyLong(), isNull(), any()))
                .thenAnswer(call -> {
                    long id = call.getArgument(1);
                    if (id == 103L) {
                        throw new NacosException(NacosException.NOT_FOUND, "revoked");
                    }
                    AiResource source = new AiResource();
                    source.setOwner("alice");
                    source.setBizTags(id == 101L ? "[\"tag-a\"]" : "[]");
                    source.setDownloadCount(id == 101L ? 0L : 1000L);
                    source.setGmtModified(Timestamp
                        .valueOf(id == 101L ? "2026-01-01 00:00:00" : "2019-01-01 00:00:00"));
                    HubResourceReference reference =
                        HubResourceReferenceJsonUtil.build("source", id + 100,
                            id == 101L ? "one" : "two", id + 200, "1.0.0");
                    HubResourcePublicationService.PinnedPublicationReader<?> reader =
                        call.getArgument(3);
                    return reader.read(id == 101L ? first : second, source, reference);
                });
        }
        
        private AiResource hub(long id, long count, String date) {
            AiResource row = new AiResource();
            row.setId(id);
            row.setDownloadCount(count);
            row.setGmtModified(Timestamp.valueOf(date));
            return row;
        }
    }
    
    @Test
    void radAsciiOrderingPrecedesPagingAndDiffersFromNumericIdAndPopularity() throws Exception {
        HubResourceType type = HubResourceType.AGENT;
        HubEntryStorage storage = mock(HubEntryStorage.class);
        HubResourcePublicationService publications = mock(HubResourcePublicationService.class);
        HubResourceDirectoryService directory =
            new HubResourceDirectoryService(storage, publications,
                mock(HubResourcePermissionGuard.class));
        AiResource two = new AiResource();
        two.setId(2L);
        two.setDownloadCount(999L);
        AiResource ten = new AiResource();
        ten.setId(10L);
        ten.setDownloadCount(1L);
        when(storage.maxEntryId(type)).thenReturn(10L);
        when(storage.nextEntryId(type, 0, 10)).thenReturn(2L);
        when(storage.entriesInIdRange(type, 2, 10)).thenReturn(Arrays.asList(two, ten));
        when(publications.inspectPinned(eq(type), anyLong(), isNull(), any())).thenAnswer(call -> {
            Long id = call.getArgument(1);
            HubResourceReference reference = HubResourceReferenceJsonUtil.build("source", id + 100,
                "same-name", id + 200, "1.0.0");
            HubResourcePublicationService.PinnedPublicationReader<?> reader = call.getArgument(3);
            return reader.read(id == 2L ? two : ten, new AiResource(), reference);
        });
        Page<HubResourceDirectoryService.Entry> first = directory.list(type, null,
            Collections.emptyList(), null, "entry_id_ascii", 1, 1);
        assertEquals(10L, first.getPageItems().get(0).hub().getId());
        assertEquals(2, first.getTotalCount());
        assertEquals(2L, directory.list(type, null, Collections.emptyList(), null,
            "entry_id_ascii", 2, 1).getPageItems().get(0).hub().getId());
    }
    
}
