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
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verify eligible-first totals, stable ID windows and fail-closed directory reads. */
class HubSkillDirectoryServiceTest {
    
    private final HubEntryStorage storage = mock(HubEntryStorage.class);
    
    private final HubSkillReadPermissionGuard permission = mock(HubSkillReadPermissionGuard.class);
    
    private final HubSkillReferenceResolver resolver = mock(HubSkillReferenceResolver.class);
    
    private final HubSkillReadAccessService readAccess = mock(HubSkillReadAccessService.class);
    
    private final NamespaceOperationService namespaces = mock(NamespaceOperationService.class);
    
    private final HubSkillDirectoryService directory = new HubSkillDirectoryService(
        storage, permission, resolver, readAccess, namespaces);
    
    private final NavigableMap<Long, AiResource> rawEntries = new TreeMap<>();
    
    private final Map<Long, ResolvedHubSkillReference> publications = new HashMap<>();
    
    private final Map<Long, AiResource> sources = new HashMap<>();
    
    private final List<Long> withdrawn = new ArrayList<>();
    
    private long scanMaximum;
    
    @BeforeEach
    void setUp() throws Exception {
        when(namespaces.getNamespace(anyString()))
            .thenAnswer(invocation -> new Namespace(invocation.getArgument(0),
                "Display " + invocation.getArgument(0)));
        when(storage.maxEntryId()).thenAnswer(invocation -> scanMaximum);
        when(storage.nextEntryId(anyLong(), anyLong())).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(0);
            long maximum = invocation.getArgument(1);
            Long next = rawEntries.higherKey(afterId);
            return next == null || next > maximum ? null : next;
        });
        when(storage.entriesInIdRange(anyLong(), anyLong())).thenAnswer(invocation -> {
            long first = invocation.getArgument(0);
            long last = invocation.getArgument(1);
            return new ArrayList<>(rawEntries.subMap(first, true, last, true).values());
        });
        when(resolver.resolve(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            ResolvedHubSkillReference resolved = publications.get(id);
            if (resolved == null) {
                throw new NacosException(NacosException.NOT_FOUND, "withdrawn");
            }
            return resolved;
        });
        when(readAccess.requireReadableSource(any(ResolvedHubSkillReference.class)))
            .thenAnswer(invocation -> {
                ResolvedHubSkillReference value = invocation.getArgument(0);
                if (withdrawn.contains(value.getHubEntryId())) {
                    throw new NacosException(NacosException.NOT_FOUND, "source offline");
                }
                return sources.get(value.getHubEntryId());
            });
    }
    
    @Test
    void sameNameFromTwoSourcesReturnsDistinctHubIdsAndSafeSummaries() throws Exception {
        add(21L, "team-a", "alice");
        add(801L, "team-b", "bob");
        Page<HubSkillSummary> page = directory.list(request(1, 10));
        assertEquals(2, page.getTotalCount());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(2, page.getPageItems().size());
        HubSkillSummary first = page.getPageItems().get(0);
        HubSkillSummary second = page.getPageItems().get(1);
        assertEquals(Long.valueOf(21L), first.getHubEntryId());
        assertEquals(Long.valueOf(801L), second.getHubEntryId());
        assertEquals("find-skills", first.getDisplayName());
        assertEquals("find-skills", second.getDisplayName());
        assertEquals("alice", first.getAuthor());
        assertEquals("bob", second.getAuthor());
        assertEquals("1.0.0", first.getSharedVersion());
        assertEquals("HUB", first.getOrigin());
        assertEquals(Arrays.asList("tools", "search"), first.getTags());
        assertEquals("Display team-a", first.getSourceDisplayName());
        assertEquals("Display team-b", second.getSourceDisplayName());
        assertEquals(0L, first.getDownloadCount());
        assertNotNull(first.getSharedAt());
    }
    
    @Test
    void managementFiltersApplyBeforeSharedPopularityAndPagination() throws Exception {
        add(1L, "team-a", "alice");
        add(2L, "team-b", "bob");
        add(700L, "team-c", "alice");
        setHubDownloads(1L, 3L);
        setHubDownloads(2L, 100L);
        setHubDownloads(700L, 20L);
        HubSkillManagementAdapter adapter = new HubSkillManagementAdapter(
            mock(HubSkillReadService.class), directory);
        Page<SkillSummary> page = adapter.list("FIND", "blur", "download_count", "alice", "PUBLIC",
            "tool", 1, 1);
        assertEquals(2, page.getTotalCount());
        assertEquals(2, page.getPagesAvailable());
        assertEquals("700", page.getPageItems().get(0).getName());
        assertEquals(20L, page.getPageItems().get(0).getDownloadCount());
        assertEquals("alice", page.getPageItems().get(0).getOwner());
        assertFalse(page.getPageItems().get(0).isWritable());
        assertEquals(0, adapter.list(null, null, null, "PRIVATE", null, 1, 10).getTotalCount());
        assertEquals(0, adapter.list("find", "accurate", null, null, null, 1, 10).getTotalCount());
        assertEquals(3,
            adapter.list("find-skills", "accurate", null, null, null, 1, 10).getTotalCount());
    }
    
    @Test
    void publicLabelUsesSourceButDownloadsBelongOnlyToTheHubEntry() throws Exception {
        add(31L, "public", "alice");
        sources.get(31L).setDownloadCount(57L);
        setHubDownloads(31L, 3L);
        HubSkillSummary item = directory.list(request(1, 10)).getPageItems().get(0);
        assertEquals("public", item.getSourceDisplayName());
        assertEquals(3L, item.getDownloadCount());
        verifyNoInteractions(namespaces);
    }
    
    @Test
    void popularityIsRankedAcrossIdWindowsBeforePaginationWithStableTies() throws Exception {
        add(1L, "a", "owner");
        add(2L, "b", "owner");
        add(700L, "c", "owner");
        add(800L, "d", "owner");
        add(900L, "e", "owner");
        setHubDownloads(1L, 3L);
        setHubDownloads(2L, 10L);
        setHubDownloads(700L, 10L);
        setHubDownloads(800L, 20L);
        setHubDownloads(900L, 100L);
        withdrawn.add(900L);
        sources.get(1L).setDownloadCount(9999L);
        HubSkillListRequest popular = request(1, 2);
        popular.setOrderBy("download_count");
        Page<HubSkillSummary> first = directory.list(popular);
        popular.setPageNo(2);
        Page<HubSkillSummary> second = directory.list(popular);
        assertEquals(Arrays.asList(800L, 2L), entryIds(first));
        assertEquals(Arrays.asList(700L, 1L), entryIds(second));
        assertEquals(4, first.getTotalCount());
        assertEquals(4, second.getTotalCount());
        assertEquals(2, first.getPagesAvailable());
        assertEquals(20L, first.getPageItems().get(0).getDownloadCount());
    }
    
    @Test
    void missingAndNegativeHubDownloadsAreZeroRegardlessOfSourceDownloads() throws Exception {
        add(1L, "a", "owner");
        add(2L, "b", "owner");
        add(3L, "c", "owner");
        setHubDownloads(2L, -1L);
        setHubDownloads(3L, 0L);
        sources.get(1L).setDownloadCount(900L);
        sources.get(2L).setDownloadCount(800L);
        Page<HubSkillSummary> page = directory.list(request(1, 3));
        assertEquals(Arrays.asList(1L, 2L, 3L), entryIds(page));
        for (HubSkillSummary summary : page.getPageItems()) {
            assertEquals(0L, summary.getDownloadCount());
        }
    }
    
    @Test
    void defaultSortUsesHubUpdateTimeBeforePagingWithStableTiesAndNullsLast() throws Exception {
        add(1L, "a", "owner");
        add(2L, "b", "owner");
        add(700L, "c", "owner");
        add(800L, "d", "owner");
        add(900L, "e", "owner");
        setHubUpdatedAt(1L, 3000L);
        setHubUpdatedAt(2L, 9000L);
        setHubUpdatedAt(700L, 9000L);
        setHubUpdatedAt(800L, null);
        setHubUpdatedAt(900L, 99999L);
        withdrawn.add(900L);
        setHubDownloads(1L, 999L);
        sources.get(800L).setGmtModified(new Timestamp(999999L));
        Page<HubSkillSummary> first = directory.list(request(1, 2));
        Page<HubSkillSummary> second = directory.list(request(2, 2));
        assertEquals(Arrays.asList(2L, 700L), entryIds(first));
        assertEquals(Arrays.asList(1L, 800L), entryIds(second));
        assertEquals(4, first.getTotalCount());
        assertEquals(2, first.getPagesAvailable());
        assertEquals(9000L, first.getPageItems().get(0).getSharedAt());
        HubSkillListRequest popular = request(1, 2);
        popular.setOrderBy("download_count");
        assertEquals(Arrays.asList(1L, 2L), entryIds(directory.list(popular)));
    }
    
    @Test
    void namespaceNameIsPublicPresentationWithoutSourceIdentifiers() throws Exception {
        add(31L, "private-namespace-id", "alice");
        when(namespaces.getNamespace("private-namespace-id"))
            .thenReturn(new Namespace("private-namespace-id", "Hub Test A"));
        HubSkillSummary item = directory.list(request(1, 10)).getPageItems().get(0);
        assertEquals("Hub Test A", item.getSourceDisplayName());
        String json = JacksonUtils.toJson(item);
        assertFalse(json.contains("private-namespace-id"));
        assertFalse(json.contains("sourceNamespaceId"));
        assertFalse(json.contains("storage"));
        assertFalse(json.contains("ext"));
    }
    
    @Test
    void removedNamespaceDoesNotFallBackToExposingItsRawId() throws Exception {
        add(31L, "private-namespace-id", "alice");
        when(namespaces.getNamespace("private-namespace-id"))
            .thenThrow(new NacosException(404, "private-namespace-id is missing"));
        HubSkillSummary item = directory.list(request(1, 10)).getPageItems().get(0);
        assertNull(item.getSourceDisplayName());
        assertFalse(JacksonUtils.toJson(item).contains("private-namespace-id"));
    }
    
    @Test
    void namespaceLookupFailureReturnsControlledErrorWithoutStorageDetails() throws Exception {
        add(31L, "private-namespace-id", "alice");
        when(namespaces.getNamespace("private-namespace-id"))
            .thenThrow(new IllegalStateException("secret database details"));
        NacosException error =
            assertThrows(NacosException.class, () -> directory.list(request(1, 10)));
        assertEquals(500, error.getErrCode());
        assertFalse(error.getMessage().contains("secret database"));
    }
    
    @Test
    void filteredEntriesDoNotOccupyPageSlotsOrIncreaseTotals() throws Exception {
        add(10L, "one", "a");
        add(12L, "two", "b");
        add(14L, "three", "c");
        add(16L, "four", "d");
        add(18L, "five", "e");
        withdrawn.addAll(Arrays.asList(10L, 14L, 18L));
        Page<HubSkillSummary> page = directory.list(request(1, 2));
        assertEquals(2, page.getTotalCount());
        assertEquals(1, page.getPagesAvailable());
        assertEquals(Arrays.asList(12L, 16L), entryIds(page));
    }
    
    @Test
    void middleAndLastPageAreCalculatedAfterFiltering() throws Exception {
        for (long id = 1; id <= 11; id++) {
            add(id, "team-" + id, "author");
        }
        withdrawn.addAll(Arrays.asList(2L, 6L, 9L));
        Page<HubSkillSummary> second = directory.list(request(2, 3));
        assertEquals(8, second.getTotalCount());
        assertEquals(3, second.getPagesAvailable());
        assertEquals(Arrays.asList(5L, 7L, 8L), entryIds(second));
        Page<HubSkillSummary> last = directory.list(request(3, 3));
        assertEquals(Arrays.asList(10L, 11L), entryIds(last));
        Page<HubSkillSummary> beyond = directory.list(request(4, 3));
        assertTrue(beyond.getPageItems().isEmpty());
        assertEquals(8, beyond.getTotalCount());
        assertEquals(3, beyond.getPagesAvailable());
    }
    
    @Test
    void emptyAndAllInvalidDirectoriesStillReturnConsistentCounts() throws Exception {
        Page<HubSkillSummary> empty = directory.list(request(1, 2));
        assertEquals(0, empty.getTotalCount());
        assertEquals(0, empty.getPagesAvailable());
        assertTrue(empty.getPageItems().isEmpty());
        add(100L, "a", "owner");
        withdrawn.add(100L);
        Page<HubSkillSummary> invalid = directory.list(request(1, 2));
        assertEquals(0, invalid.getTotalCount());
        assertEquals(0, invalid.getPagesAvailable());
    }
    
    @Test
    void longSparseIdsUseSeekInsteadOfOffsetPageOrLargeRange() throws Exception {
        add(10L, "a", "owner");
        add(1000000000L, "b", "owner");
        Page<HubSkillSummary> page = directory.list(request(2, 1));
        assertEquals(2, page.getTotalCount());
        assertEquals(Arrays.asList(1000000000L), entryIds(page));
        verify(storage).entriesInIdRange(10L, 521L);
        verify(storage).entriesInIdRange(1000000000L, 1000000000L);
    }
    
    @Test
    void hundredsOfInvalidRowsAreSkippedAcrossWindowBoundaries() throws Exception {
        for (long id = 1; id <= 700; id++) {
            add(id, "source-" + id, "owner");
            if (id % 7 != 0) {
                withdrawn.add(id);
            }
        }
        Page<HubSkillSummary> page = directory.list(request(2, 45));
        assertEquals(100, page.getTotalCount());
        assertEquals(3, page.getPagesAvailable());
        assertEquals(45, page.getPageItems().size());
        assertEquals(Long.valueOf(322L), page.getPageItems().get(0).getHubEntryId());
        assertEquals(Long.valueOf(630L), page.getPageItems().get(44).getHubEntryId());
    }
    
    @Test
    void maxIdBoundsListingWhenConcurrentNewShareAppears() throws Exception {
        add(1L, "a", "a");
        scanMaximum = 1L;
        add(2L, "b", "b");
        scanMaximum = 1L;
        Page<HubSkillSummary> page = directory.list(request(1, 10));
        assertEquals(1, page.getTotalCount());
        assertEquals(Arrays.asList(1L), entryIds(page));
    }
    
    @Test
    void tooLargePageIndexUsesLongOffset() throws Exception {
        add(1L, "a", "owner");
        Page<HubSkillSummary> page = directory.list(request(Integer.MAX_VALUE, 100));
        assertEquals(1, page.getTotalCount());
        assertTrue(page.getPageItems().isEmpty());
        assertEquals(Integer.MAX_VALUE, page.getPageNumber());
    }
    
    @Test
    void anonymousOrFailedReadAdmissionCannotEnumerateHub() throws Exception {
        doThrow(new NacosException(NacosException.NO_RIGHT, "anonymous"))
            .when(permission).requireHubRead();
        NacosException failure = assertThrows(NacosException.class,
            () -> directory.list(request(1, 10)));
        assertEquals(NacosException.NO_RIGHT, failure.getErrCode());
        verifyNoInteractions(storage);
        verifyNoInteractions(resolver);
    }
    
    @Test
    void invalidPaginationRejectsBeforeDatabaseAccess() throws Exception {
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> directory.list(request(0, 10))).getErrCode());
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> directory.list(request(1, 101))).getErrCode());
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> directory.list(null)).getErrCode());
        verifyNoInteractions(storage);
    }
    
    @Test
    void keywordAndTagsUseCanonicalDisplayNameWithAccurateTotals() throws Exception {
        add(21L, "source-a", "alice");
        add(801L, "source-b", "bob");
        add(900L, "source-c", "carol");
        sources.get(900L).setBizTags("[\"other\"]");
        HubSkillListRequest query = request(1, 1);
        query.setQuery("FIND-SKILLS");
        query.setTagsAll(Collections.singletonList("Search"));
        Page<HubSkillSummary> first = directory.list(query);
        assertEquals(2, first.getTotalCount());
        assertEquals(2, first.getPagesAvailable());
        assertEquals(Collections.singletonList(21L), entryIds(first));
        query.setPageNo(2);
        assertEquals(Collections.singletonList(801L), entryIds(directory.list(query)));
        query.setPageNo(3);
        assertTrue(directory.list(query).getPageItems().isEmpty());
    }
    
    @Test
    void truncatedInternalNameMustStillMatchFullSourceName() throws Exception {
        add(410L, "namespace-a", "owner");
        String fullName = "long-prefix-" + "z".repeat(240) + "-final-marker";
        AiResource meta = rawEntries.get(410L);
        String internalName = com.alibaba.nacos.ai.utils.HubResourceNameUtils.buildInternalName(
            fullName, 3410L);
        meta.setName(internalName);
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            "namespace-a", 3410L, fullName, 9410L, "1.0.0");
        AiResourceVersion version = new AiResourceVersion();
        version.setId(10410L);
        publications.put(410L, new ResolvedHubSkillReference(meta, version, reference));
        HubSkillListRequest request = request(1, 10);
        request.setQuery("final-marker");
        Page<HubSkillSummary> result = directory.list(request);
        assertEquals(1, result.getTotalCount());
        assertEquals(fullName, result.getPageItems().get(0).getDisplayName());
        assertTrue(internalName.length() <= 256);
        assertFalse(internalName.contains("final-marker"));
    }
    
    @Test
    void unknownKeywordOrTagReturnsExactZero() throws Exception {
        add(1L, "source-a", "alice");
        HubSkillListRequest query = request(1, 10);
        query.setQuery("unseen-long-name");
        assertEquals(0, directory.list(query).getTotalCount());
        query.setQuery(null);
        query.setTagsAll(Collections.singletonList("different"));
        assertEquals(0, directory.list(query).getTotalCount());
    }
    
    @Test
    void malformedTagJsonFailsClosedForTagFiltering() throws Exception {
        add(1L, "source-a", "alice");
        sources.get(1L).setBizTags("{malformed}");
        HubSkillListRequest query = request(1, 10);
        query.setTagsAll(Collections.singletonList("search"));
        assertEquals(0, directory.list(query).getTotalCount());
    }
    
    @Test
    void whitespaceOnlyQueryIsAnUnfilteredList() throws Exception {
        add(2L, "a", "owner");
        HubSkillListRequest request = request(1, 10);
        request.setQuery("  ");
        assertEquals(1, directory.list(request).getTotalCount());
    }
    
    @Test
    void authorizationFailureDuringCandidateReadFailsEntireRequest() throws Exception {
        add(8L, "a", "owner");
        when(readAccess.requireReadableSource(any(ResolvedHubSkillReference.class)))
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "revoked"));
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> directory.list(request(1, 5))).getErrCode());
    }
    
    @Test
    void serviceFailureMustNeverTurnIntoEmptyPage() throws Exception {
        add(3L, "a", "owner");
        doThrow(new NacosException(NacosException.SERVER_ERROR, "database unavailable"))
            .when(resolver).resolve(3L);
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosException.class,
            () -> directory.list(request(1, 5))).getErrCode());
    }
    
    @Test
    void finalWithdrawalRejectsStalePageRatherThanReturningIt() throws Exception {
        add(15L, "a", "owner");
        doThrow(new NacosException(NacosException.NOT_FOUND, "unshared"))
            .when(readAccess).requireStillReadable(any(ResolvedHubSkillReference.class));
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> directory.list(request(1, 10))).getErrCode());
    }
    
    @Test
    void invalidRawRowCannotBypassHubNamespaceAndOriginGuard() throws Exception {
        add(4L, "a", "owner");
        rawEntries.get(4L).setFrom("local");
        assertThrows(NacosException.class, () -> directory.list(request(1, 10)));
        verify(resolver, never()).resolve(4L);
    }
    
    @Test
    void nonIncreasingOrOutOfRangeScanRowsFailInsteadOfAlteringTotal() throws Exception {
        add(9L, "a", "owner");
        add(11L, "b", "owner");
        when(storage.entriesInIdRange(anyLong(), anyLong()))
            .thenReturn(Arrays.asList(rawEntries.get(11L), rawEntries.get(9L)));
        assertThrows(NacosException.class, () -> directory.list(request(1, 10)));
    }
    
    @Test
    void missingOptionalTagsDoNotExposeRawJson() throws Exception {
        add(5L, "a", "owner");
        sources.get(5L).setBizTags("{this-is-not-an-array}");
        HubSkillSummary result = directory.list(request(1, 10)).getPageItems().get(0);
        assertTrue(result.getTags().isEmpty());
        assertEquals("find-skills", result.getDisplayName());
    }
    
    private HubSkillListRequest request(int pageNo, int pageSize) {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }
    
    private List<Long> entryIds(Page<HubSkillSummary> page) {
        List<Long> result = new ArrayList<>();
        for (HubSkillSummary item : page.getPageItems()) {
            result.add(item.getHubEntryId());
        }
        return result;
    }
    
    private void add(long id, String namespace, String owner) throws Exception {
        AiResource hub = new AiResource();
        hub.setId(id);
        hub.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        hub.setName("find-skills-" + id);
        hub.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        hub.setFrom("hub");
        hub.setMetaVersion(5L);
        hub.setDesc("description");
        hub.setGmtModified(new Timestamp(2000L));
        rawEntries.put(id, hub);
        scanMaximum = Math.max(id, scanMaximum);
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(namespace,
            id + 3000L, "find-skills", id + 9000L, "1.0.0");
        AiResourceVersion pinned = new AiResourceVersion();
        pinned.setId(id + 10000L);
        publications.put(id, new ResolvedHubSkillReference(hub, pinned, reference));
        AiResource source = new AiResource();
        source.setId(id + 3000L);
        source.setNamespaceId(namespace);
        source.setName("find-skills");
        source.setOwner(owner);
        source.setDesc("public skill");
        source.setBizTags("[\"tools\",\"search\"]");
        sources.put(id, source);
    }
    
    private void setHubDownloads(long id, Long count) throws Exception {
        AiResource hub = rawEntries.get(id);
        hub.setDownloadCount(count);
        ResolvedHubSkillReference original = publications.get(id);
        AiResourceVersion pinned = new AiResourceVersion();
        pinned.setId(original.getHubVersionId());
        publications.put(id, new ResolvedHubSkillReference(hub, pinned, original.toReference()));
    }
    
    private void setHubUpdatedAt(long id, Long timestamp) throws Exception {
        rawEntries.get(id).setGmtModified(timestamp == null ? null : new Timestamp(timestamp));
        setHubDownloads(id, rawEntries.get(id).getDownloadCount());
    }
    
    @Test
    void databaseFailureIsRedactedAndDoesNotLookLikeAnEmptyHub() {
        when(storage.maxEntryId()).thenThrow(new IllegalStateException("jdbc-secret-source-id"));
        NacosException failure =
            assertThrows(NacosException.class, () -> directory.list(request(1, 10)));
        assertEquals(500, failure.getErrCode());
        assertFalse(failure.getErrMsg().contains("jdbc-secret"));
    }
    
    @Test
    void callerRevocationAfterAnEmptyScanStillDeniesTheResponse() throws Exception {
        org.mockito.Mockito.doNothing().doThrow(new NacosException(403, "revoked"))
            .when(permission).requireHubRead();
        assertEquals(403,
            assertThrows(NacosException.class, () -> directory.list(request(1, 10))).getErrCode());
    }
    
}
