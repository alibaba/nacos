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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Collection;

/**
 * Projects and validates one or more canonical AI resource types for the shared search core.
 *
 * @author nacos
 */
public interface AiResourceSearchTypeHandler {
    
    /**
     * Resource types owned by this handler.
     *
     * @return non-empty resource type collection
     */
    Collection<String> resourceTypes();
    
    /**
     * Project an exact version or the current latest version when {@code version} is blank.
     *
     * @return projection, or {@code null} when the resource is not currently indexable
     */
    AiResourceIndexProjection project(String namespaceId, String resourceType,
        String resourceName, String version) throws NacosException;
    
    /**
     * Scan one bounded page of canonical resources for reconciliation.
     */
    AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * Validate that a persisted document is still current and readable.
     */
    boolean isCurrent(AiResourceSearchDocument document) throws NacosException;
    
    /**
     * Check whether the canonical resource identity still exists.
     */
    boolean exists(String namespaceId, String resourceType, String resourceName)
        throws NacosException;
}
