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

import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Validate and normalize requests to the dedicated Hub read API.
 *
 * <p>Call this at the service boundary, not only at a Console controller,
 * so local and remote deployment paths enforce the same rules.</p>
 *
 * @author nacos
 */
public final class HubSkillReadRequestValidator {
    
    private static final int MAX_PAGE_SIZE = 100;
    
    private static final int MAX_QUERY_LENGTH = 1024;
    
    private static final int MAX_TAGS = 32;
    
    private static final int MAX_TAG_LENGTH = 128;
    
    private HubSkillReadRequestValidator() {
    }
    
    /**
     * Validate page boundaries and normalize keyword / tag filters.
     *
     * @param request mutable directory request
     * @throws NacosApiException if the request exceeds supported bounds
     */
    public static void validate(HubSkillListRequest request) throws NacosApiException {
        if (request == null) {
            throw invalid("Hub Skill list request is required");
        }
        if (request.getPageNo() < 1) {
            throw invalid("pageNo must be positive");
        }
        if (request.getPageSize() < 1 || request.getPageSize() > MAX_PAGE_SIZE) {
            throw invalid("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        String query = request.getQuery();
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw invalid("query exceeds " + MAX_QUERY_LENGTH + " characters");
        }
        request.setQuery(StringUtils.isBlank(query) ? null : query.trim());
        request.setTagsAll(normalizeTags(request.getTagsAll()));
    }
    
    /**
     * Check the Hub entry ID independently of the request transport.
     *
     * @param hubEntryId database primary key of the Hub entry
     * @throws NacosApiException if it is missing or invalid
     */
    public static void validateEntryId(Long hubEntryId) throws NacosApiException {
        if (hubEntryId == null || hubEntryId <= 0) {
            throw invalid("hubEntryId must be positive");
        }
    }
    
    private static List<String> normalizeTags(List<String> tags) throws NacosApiException {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        if (tags.size() > MAX_TAGS) {
            throw invalid("tagsAll exceeds " + MAX_TAGS + " values");
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.isBlank(tag)) {
                continue;
            }
            String normalized = tag.trim();
            if (normalized.length() > MAX_TAG_LENGTH) {
                throw invalid("tag exceeds " + MAX_TAG_LENGTH + " characters");
            }
            deduplicated.add(normalized);
        }
        return new ArrayList<>(deduplicated);
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
