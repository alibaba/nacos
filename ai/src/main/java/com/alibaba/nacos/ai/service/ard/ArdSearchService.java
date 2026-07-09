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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.api.ai.model.ard.ArdCatalog;
import com.alibaba.nacos.api.ai.model.ard.ArdExploreRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdExploreResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdListResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Nacos Local ARD Search service.
 *
 * @author nacos
 */
public interface ArdSearchService {
    
    /**
     * Search local AI resources with ARD-compatible request and response models.
     *
     * @param request ARD search request
     * @return ARD search response
     * @throws NacosException when request validation or search fails
     */
    ArdSearchResponse search(ArdSearchRequest request) throws NacosException;
    
    /**
     * Explore local AI resources with ARD facets.
     *
     * @param request ARD explore request
     * @return ARD explore response
     * @throws NacosException when request validation or explore fails
     */
    ArdExploreResponse explore(ArdExploreRequest request) throws NacosException;
    
    /**
     * List local AI resources with ARD deterministic browsing parameters.
     *
     * @param namespaceId namespace id
     * @param filter ARD list filter expression
     * @param orderBy order expression
     * @param pageSize page size
     * @param pageToken pagination token
     * @return ARD list response
     * @throws NacosException when request validation or list fails
     */
    ArdListResponse list(String namespaceId, String filter, String orderBy, Integer pageSize,
        String pageToken) throws NacosException;
    
    /**
     * Build host-level ARD catalog manifest for standard well-known discovery.
     *
     * @return host-level ARD catalog manifest
     */
    ArdCatalog hostCatalog();
    
    /**
     * Build local ARD catalog manifest.
     *
     * @param namespaceId namespace id
     * @return ARD catalog manifest
     * @throws NacosException when catalog build fails
     */
    ArdCatalog catalog(String namespaceId) throws NacosException;
}
