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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.model.Page;

/**
 * Persist service for ai_resource.
 *
 * @author nacos
 * @since 3.2.0
 */
public interface AiResourcePersistService {
    
    String PATTERN_STR = "*";
    
    /**
     * Convert a search argument that may contain Nacos wildcard ({@code *}) to SQL LIKE syntax ({@code %}).
     * Also escapes the SQL single-char wildcard ({@code _}) with backslash.
     *
     * <p>Aligned with {@code ConfigInfoPersistService#generateLikeArgument}.</p>
     *
     * @param s the raw search string, e.g. {@code *keyword*}
     * @return the SQL-safe LIKE argument, e.g. {@code %keyword%}
     */
    default String generateLikeArgument(String s) {
        String underscore = "_";
        if (s.contains(underscore)) {
            s = s.replaceAll(underscore, "\\\\_");
        }
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }
    
    long insert(AiResource resource);
    
    AiResource find(String namespaceId, String name, String type);
    
    /**
     * List resources with basic filters.
     */
    default Page<AiResource> list(String namespaceId, String type, String nameLike,
        String bizTagsLike, int pageNo,
        int pageSize) {
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId(namespaceId);
        condition.setType(type);
        condition.setNameLike(nameLike);
        condition.setBizTagsLike(bizTagsLike);
        return list(condition, pageNo, pageSize);
    }
    
    /**
     * List resources with optional ordering.
     *
     * @param orderBy sort field (e.g. "download_count"), null defaults to gmt_modified
     */
    default Page<AiResource> list(String namespaceId, String type, String nameLike,
        String bizTagsLike, String orderBy,
        int pageNo, int pageSize) {
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId(namespaceId);
        condition.setType(type);
        condition.setNameLike(nameLike);
        condition.setBizTagsLike(bizTagsLike);
        condition.setOrderBy(orderBy);
        return list(condition, pageNo, pageSize);
    }
    
    /**
     * List resources by unified query condition.
     *
     * @param queryCondition unified table-oriented query conditions
     * @param pageNo         page number (1-based)
     * @param pageSize       page size
     * @return paged resources
     */
    Page<AiResource> list(QueryCondition queryCondition, int pageNo, int pageSize);
    
    /**
     * Update meta with optimistic lock on meta_version.
     *
     * @return true if updated successfully (affectedRows == 1)
     */
    boolean updateMetaCas(String namespaceId, String name, String type, long expectedMetaVersion,
        AiResource newValue);
    
    /**
     * Update resource source with optimistic lock on meta_version.
     *
     * @return true if updated successfully (affectedRows == 1)
     */
    boolean updateSourceCas(String namespaceId, String name, String type, long expectedMetaVersion,
        String source);
    
    int delete(String namespaceId, String name, String type);
    
    /**
     * Update the scope (visibility) of a resource.
     *
     * @return true if updated successfully (affectedRows == 1)
     */
    boolean updateScope(String namespaceId, String name, String type, String scope);
    
    /**
     * Increment download count for a skill (total).
     *
     * @param namespaceId namespace ID
     * @param name        resource name
     * @param type        resource type
     * @param increment   amount to add
     * @return true if updated successfully
     */
    boolean incrementDownloadCount(String namespaceId, String name, String type, long increment);
    
}
