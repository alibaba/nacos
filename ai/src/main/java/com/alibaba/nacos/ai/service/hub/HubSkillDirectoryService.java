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
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Source-of-truth S2-4 Hub Skill directory with correct eligible-first pagination.
 *
 * <p>Scans only Hub-owned Skill rows in bounded primary-key ID windows. Every
 * candidate passes the S2-2 immutable-pointer resolver and the S2-3 live source
 * and read authorization checks before it counts toward a page. Only the
 * requested output page is kept in memory. Keyword and tag matching use
 * canonical source display names and current tags, not lossy Hub internal names.
 * The search index is optional: closing it does not make directory search lie.</p>
 *
 * <p>The upper ID bound and ascending order keep a scan finite and stable under
 * new inserts. They are not a cross-table transaction snapshot: concurrent
 * revocation can change the count. Selected entries receive a final revocation
 * check before response creation, and stale selected entries fail the request
 * rather than being returned.</p>
 */
@Service
public class HubSkillDirectoryService {
    
    /** Numeric ID span per scan; at most this many records can be returned. */
    private static final int ID_WINDOW = 512;
    
    private static final String HUB_NAMESPACE = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String SKILL_TYPE = AiResourceConstants.RESOURCE_TYPE_SKILL;
    
    private static final String HUB_ORIGIN = "hub";
    
    private final HubEntryStorage hubStorage;
    
    private final HubSkillReadPermissionGuard permissionGuard;
    
    private final HubSkillReferenceResolver resolver;
    
    private final HubSkillReadAccessService readAccess;
    
    private final NamespaceOperationService namespaces;
    
    public HubSkillDirectoryService(HubEntryStorage hubStorage,
        HubSkillReadPermissionGuard permissionGuard,
        HubSkillReferenceResolver resolver, HubSkillReadAccessService readAccess,
        NamespaceOperationService namespaces) {
        this.hubStorage = hubStorage;
        this.permissionGuard = permissionGuard;
        this.resolver = resolver;
        this.readAccess = readAccess;
        this.namespaces = namespaces;
    }
    
    /**
     * List all currently usable Hub Skill shares with exact, eligible-only totals
     * for a stable data set. This is an internal read service, not an HTTP API.
     *
     * @param request validated page options without a caller-selected namespace
     * @return page of sanitized Hub summaries ordered by immutable entry ID ASC
     * @throws NacosException when validation, authorization or storage fails
     */
    public Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException {
        HubSkillReadRequestValidator.validate(request);
        // Admission precedes every scan, including an empty or unsupported query.
        permissionGuard.requireHubRead();
        if (!HubConfig.isEnabled()) {
            return emptyPage(request);
        }
        try {
            Page<HubSkillSummary> result = scan(request);
            return HubConfig.isEnabled() ? result : emptyPage(request);
        } catch (RuntimeException failure) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Hub Skill directory unavailable");
        }
    }
    
    private Page<HubSkillSummary> emptyPage(HubSkillListRequest request) {
        Page<HubSkillSummary> result = new Page<>();
        result.setPageNumber(request.getPageNo());
        result.setTotalCount(0);
        result.setPagesAvailable(0);
        result.setPageItems(Collections.emptyList());
        return result;
    }
    
    private Page<HubSkillSummary> scan(HubSkillListRequest request) throws NacosException {
        long offset = ((long) request.getPageNo() - 1L) * request.getPageSize();
        long validCount = 0L;
        List<DirectoryEntry> selected = new ArrayList<>(request.getPageSize());
        long maxId = hubStorage.maxEntryId();
        long afterId = 0L;
        while (afterId < maxId) {
            Long firstId = hubStorage.nextEntryId(afterId, maxId);
            if (firstId == null) {
                break;
            }
            if (firstId <= afterId || firstId > maxId) {
                throw new IllegalStateException("Hub directory scan returned an invalid ID anchor");
            }
            long upper = firstId > Long.MAX_VALUE - (ID_WINDOW - 1L)
                ? Long.MAX_VALUE : firstId + ID_WINDOW - 1L;
            upper = Math.min(upper, maxId);
            List<AiResource> batch = hubStorage.entriesInIdRange(firstId, upper);
            if (batch == null) {
                throw new IllegalStateException("Hub directory scan returned a null batch");
            }
            long previousId = firstId - 1L;
            for (AiResource candidate : batch) {
                if (candidate == null || candidate.getId() == null
                    || candidate.getId() <= previousId || candidate.getId() > upper
                    || !HUB_NAMESPACE.equals(candidate.getNamespaceId())
                    || !SKILL_TYPE.equals(candidate.getType())
                    || !HUB_ORIGIN.equals(candidate.getFrom())) {
                    throw new IllegalStateException(
                        "Hub directory scan returned a noncanonical row");
                }
                previousId = candidate.getId();
                DirectoryEntry qualified = qualify(candidate.getId());
                if (qualified == null || !matches(request, qualified)) {
                    continue;
                }
                if (validCount >= offset && selected.size() < request.getPageSize()) {
                    selected.add(qualified);
                }
                validCount++;
            }
            // Advance the numeric window even if its rows were removed after MIN(id).
            afterId = upper;
        }
        List<HubSkillSummary> items = new ArrayList<>(selected.size());
        for (DirectoryEntry entry : selected) {
            // Reject a newly withdrawn selected share rather than returning stale data.
            readAccess.requireStillReadable(entry.publication);
            items.add(toSummary(entry.publication, entry.source));
        }
        permissionGuard.requireHubRead();
        Page<HubSkillSummary> result = new Page<>();
        result.setPageNumber(request.getPageNo());
        result.setTotalCount((int) Math.min(Integer.MAX_VALUE, validCount));
        long pages = validCount / request.getPageSize()
            + (validCount % request.getPageSize() == 0L ? 0L : 1L);
        result.setPagesAvailable((int) Math.min(Integer.MAX_VALUE, pages));
        result.setPageItems(items);
        return result;
    }
    
    private DirectoryEntry qualify(long hubEntryId) throws NacosException {
        try {
            ResolvedHubSkillReference publication = resolver.resolve(hubEntryId);
            AiResource source = readAccess.requireReadableSource(publication);
            return new DirectoryEntry(publication, source);
        } catch (NacosException unavailable) {
            if (unavailable.getErrCode() == NacosException.NOT_FOUND) {
                // Invalid and revoked Hub references never count towards pagination.
                return null;
            }
            // Access denied or a storage/authorization failure is not "empty Hub".
            throw unavailable;
        }
    }
    
    /** Project only safe public fields; usable by Hub detail API. */
    HubSkillSummary toSummary(ResolvedHubSkillReference publication, AiResource source)
        throws NacosException {
        HubSkillSummary result = new HubSkillSummary();
        result.setHubEntryId(publication.getHubEntryId());
        result.setDisplayName(publication.getSourceName());
        // The public Hub label contains only the Namespace display name, never a raw source ID.
        result.setSourceDisplayName(resolveSourceDisplayName(publication.getSourceNamespaceId()));
        result.setDownloadCount(
            source.getDownloadCount() == null ? 0L : Math.max(0L, source.getDownloadCount()));
        result.setAuthor(source.getOwner());
        result.setDescription(source.getDesc());
        result.setSharedVersion(publication.getSourceVersion());
        result.setTags(readPublicTags(source.getBizTags()));
        result.setSharedAt(publication.getUpdatedAt());
        return result;
    }
    
    private String resolveSourceDisplayName(String namespaceId) throws NacosException {
        if (NamespaceUtil.isDefaultNamespaceId(namespaceId)) {
            return "public";
        }
        try {
            Namespace namespace = namespaces.getNamespace(namespaceId);
            String name = namespace == null ? null : namespace.getNamespaceShowName();
            return StringUtils.isBlank(name) ? null : name.trim();
        } catch (NacosException failure) {
            if (failure.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw namespaceDisplayUnavailable();
        } catch (RuntimeException failure) {
            throw namespaceDisplayUnavailable();
        }
    }
    
    private NacosApiException namespaceDisplayUnavailable() {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
            "Hub source Namespace display is unavailable");
    }
    
    private boolean matches(HubSkillListRequest request, DirectoryEntry entry) {
        String query = request.getQuery();
        if (StringUtils.isNotBlank(query)) {
            String normalized = query.toLowerCase(Locale.ROOT);
            if (!entry.publication.getSourceName().toLowerCase(Locale.ROOT).contains(normalized)
                && (entry.source.getDesc() == null
                    || !entry.source.getDesc().toLowerCase(Locale.ROOT).contains(normalized))) {
                return false;
            }
        }
        if (request.getTagsAll() != null && !request.getTagsAll().isEmpty()) {
            List<String> actual = readPublicTags(entry.source.getBizTags());
            for (String tag : request.getTagsAll()) {
                boolean found = false;
                for (String present : actual) {
                    if (present.equalsIgnoreCase(tag)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private List<String> readPublicTags(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            String[] values = JacksonUtils.toObj(json, String[].class);
            if (values == null) {
                return Collections.emptyList();
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null || StringUtils.isBlank(value) || value.length() > 128) {
                    continue;
                }
                normalized.add(value.trim());
                if (normalized.size() >= 32) {
                    break;
                }
            }
            return new ArrayList<>(normalized);
        } catch (RuntimeException malformedTags) {
            // A bad optional tag list cannot become raw JSON in a public response.
            return Collections.emptyList();
        }
    }
    
    private static final class DirectoryEntry {
        
        private final ResolvedHubSkillReference publication;
        
        private final AiResource source;
        
        private DirectoryEntry(ResolvedHubSkillReference publication, AiResource source) {
            this.publication = publication;
            this.source = source;
        }
    }
}
