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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.A2aServerOperationService;
import com.alibaba.nacos.ai.service.ard.ArdSearchService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nacos Local ARD Search client controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.ARD_CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
public class ArdSearchController {
    
    private final ArdSearchService ardSearchService;
    
    private final A2aServerOperationService a2aServerOperationService;
    
    public ArdSearchController(ArdSearchService ardSearchService,
        A2aServerOperationService a2aServerOperationService) {
        this.ardSearchService = ardSearchService;
        this.a2aServerOperationService = a2aServerOperationService;
    }
    
    /**
     * Search online/latest AI resources from the local Nacos registry.
     */
    @Since("3.3.0")
    @PostMapping("/search")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public ArdSearchResponse search(@RequestBody ArdSearchRequest request)
        throws NacosException {
        return ardSearchService.search(request);
    }
    
    /**
     * Explore online/latest AI resources from the local Nacos registry.
     */
    @Since("3.3.0")
    @PostMapping("/explore")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public ArdSearchResponse explore(@RequestBody ArdSearchRequest request)
        throws NacosException {
        return ardSearchService.search(request);
    }
    
    /**
     * List local A2A agents from the local Nacos registry.
     */
    @Since("3.3.0")
    @GetMapping("/agents")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Page<AgentCardVersionInfo> agents(
        @RequestParam(required = false) String namespaceId,
        @RequestParam(required = false) String agentName,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Integer pageNo,
        @RequestParam(required = false) Integer pageSize) throws NacosException {
        return a2aServerOperationService.listAgents(normalizeNamespaceId(namespaceId),
            agentName, normalizeAgentSearch(search), normalizePageNo(pageNo),
            normalizePageSize(pageSize));
    }
    
    private String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId)
            ? com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    private String normalizeAgentSearch(String search) throws NacosApiException {
        if (StringUtils.isBlank(search)) {
            return Constants.A2A.SEARCH_BLUR;
        }
        if (Constants.A2A.SEARCH_ACCURATE.equalsIgnoreCase(search)
            || Constants.A2A.SEARCH_BLUR.equalsIgnoreCase(search)) {
            return search;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Request parameter `search` should be `accurate` or `blur`.");
    }
    
    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo <= 0) {
            return 1;
        }
        return pageNo;
    }
    
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, Constants.MAX_LIST_SIZE);
    }
}
