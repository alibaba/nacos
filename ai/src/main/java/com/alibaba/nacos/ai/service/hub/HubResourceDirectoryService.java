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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Eligible-only bounded ID scans, filtering and independent pagination for one resource type. */
@Service
public class HubResourceDirectoryService {
    
    private final HubEntryStorage storage;
    
    private final HubResourcePublicationService publications;
    
    private final HubResourcePermissionGuard permissions;
    
    public HubResourceDirectoryService(HubEntryStorage storage,
        HubResourcePublicationService publications, HubResourcePermissionGuard permissions) {
        this.storage = storage;
        this.publications = publications;
        this.permissions = permissions;
    }
    
    /**
     * Collect valid entries before filtering, sorting and paging. The returned rows are internal;
     * each resource adapter must project its existing response type, never serialize these rows.
     */
    public Page<Entry> list(HubResourceType type, String query, List<String> tagsAll,
        String owner, String orderBy, int pageNo, int pageSize) throws NacosException {
        return list(type, query, tagsAll, owner, orderBy, pageNo, pageSize, entry -> true);
    }
    
    /** Apply resource-specific predicates before totals and paging, preserving each search contract. */
    public Page<Entry> list(HubResourceType type, String query, List<String> tagsAll,
        String owner, String orderBy, int pageNo, int pageSize, EntryFilter filter)
        throws NacosException {
        boolean radOrdering = type == HubResourceType.AGENT && "entry_id_ascii".equals(orderBy);
        if (pageNo <= 0 || pageSize <= 0 || pageSize > 500
            || (orderBy != null && !orderBy.isBlank() && !"gmt_modified".equals(orderBy)
                && !"download_count".equals(orderBy) && !radOrdering)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Invalid Hub pagination or orderBy");
        }
        if (!HubConfig.isEnabled()) {
            return page(Collections.emptyList(), pageNo, pageSize);
        }
        permissions.requireHubRead();
        long maximum = storage.maxEntryId(type);
        long cursor = 0;
        List<Entry> entries = new ArrayList<>();
        while (cursor < maximum) {
            Long next = storage.nextEntryId(type, cursor, maximum);
            if (next == null) {
                break;
            }
            if (next <= cursor || next > maximum) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Invalid Hub directory scan anchor");
            }
            long end = next + Math.min(127L, maximum - next);
            for (AiResource candidate : storage.entriesInIdRange(type, next, end)) {
                if (candidate == null || candidate.getId() == null || candidate.getId() < next
                    || candidate.getId() > end) {
                    continue;
                }
                try {
                    Entry entry = publications.inspectPinned(type, candidate.getId(), null,
                        (hub, source, reference) -> new Entry(hub, source, reference));
                    if (matches(entry, query, tagsAll, owner) && filter.test(entry)) {
                        entries.add(entry);
                    }
                } catch (NacosException unavailable) {
                    if (unavailable.getErrCode() != NacosException.NOT_FOUND
                        && unavailable.getErrCode() != NacosException.CONFLICT) {
                        throw unavailable;
                    }
                }
            }
            cursor = end;
        }
        Comparator<Entry> ordering = "download_count".equals(orderBy)
            ? Comparator.comparingLong((Entry entry) -> entry.hub().getDownloadCount() == null
                ? 0 : entry.hub().getDownloadCount()).reversed()
            : Comparator.comparing((Entry entry) -> entry.hub().getGmtModified(),
                Comparator.nullsLast(Comparator.reverseOrder()));
        if (radOrdering) {
            ordering = Comparator.comparing(entry -> entry.hub().getId().toString());
        }
        entries.sort(ordering.thenComparingLong(entry -> entry.hub().getId()));
        return page(entries, pageNo, pageSize);
    }
    
    private boolean matches(Entry entry, String query, List<String> tagsAll, String owner) {
        if (owner != null && !owner.isBlank() && !owner.equals(entry.source().getOwner())) {
            return false;
        }
        if (query != null && !query.isBlank()) {
            String haystack = (entry.reference().getSourceName() + " "
                + (entry.source().getDesc() == null ? "" : entry.source().getDesc()))
                .toLowerCase(Locale.ROOT);
            for (String term : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!haystack.contains(term)) {
                    return false;
                }
            }
        }
        if (tagsAll != null && !tagsAll.isEmpty()) {
            try {
                List<?> tags = JacksonUtils.toObj(entry.source().getBizTags(), List.class);
                return tags != null && tags.containsAll(tagsAll);
            } catch (RuntimeException malformed) {
                return false;
            }
        }
        return true;
    }
    
    private Page<Entry> page(List<Entry> entries, int pageNo, int pageSize) {
        Page<Entry> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setTotalCount(entries.size());
        page.setPagesAvailable((int) (((long) entries.size() + pageSize - 1) / pageSize));
        long offset = (long) (pageNo - 1) * pageSize;
        page.setPageItems(offset >= entries.size() ? Collections.emptyList()
            : new ArrayList<>(entries.subList((int) offset,
                (int) Math.min(entries.size(), offset + pageSize))));
        return page;
    }
    
    /** Raw internal projection input, deliberately separate from all public response models. */
    public record Entry(AiResource hub, AiResource source, HubResourceReference reference) {
    }
    
    /** A typed resource predicate, including its own declared metadata when required. */
    @FunctionalInterface
    public interface EntryFilter {
        
        /** Check the resource's original filtering semantics without exposing source rows. */
        boolean test(Entry entry) throws NacosException;
    }
}
