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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.airegistry.annotation.ArdApi;
import com.alibaba.nacos.airegistry.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResponse;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifact;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifactService;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;

/**
 * Nacos Local ARD Search client controller.
 *
 * @author nacos
 */
@ArdApi
@RestController
@ConditionalOnArdEnabled
@RequestMapping(ArdProtocolConstants.CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
public class ArdSearchController {
    
    private final ArdSearchService ardSearchService;
    
    private final ArdArtifactService ardArtifactService;
    
    public ArdSearchController(ArdSearchService ardSearchService,
        ArdArtifactService ardArtifactService) {
        this.ardSearchService = ardSearchService;
        this.ardArtifactService = ardArtifactService;
    }
    
    /**
     * Search online/latest AI resources from the local Nacos registry.
     */
    @Since("3.3.0")
    @PostMapping("/search")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        tags = {ALLOW_ANONYMOUS})
    public ArdSearchResponse search(@RequestParam(required = false) String namespaceId,
        @RequestBody ArdSearchRequest request)
        throws NacosException {
        bindNamespaceId(namespaceId, request);
        return ardSearchService.search(request);
    }
    
    /**
     * Explore online/latest AI resources from the local Nacos registry.
     */
    @Since("3.3.0")
    @PostMapping("/explore")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        tags = {ALLOW_ANONYMOUS})
    public ArdExploreResponse explore(@RequestParam(required = false) String namespaceId,
        @RequestBody ArdExploreRequest request)
        throws NacosException {
        bindNamespaceId(namespaceId, request);
        return ardSearchService.explore(request);
    }
    
    /**
     * Return the permission-controlled local ARD catalog document.
     */
    @Since("3.3.0")
    @GetMapping("/ai-catalog.json")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        tags = {ALLOW_ANONYMOUS})
    public ArdCatalog catalog(@RequestParam(required = false) String namespaceId)
        throws NacosException {
        return ardSearchService.catalog(namespaceId);
    }
    
    /**
     * List local ARD resources from the local Nacos registry.
     */
    @Since("3.3.0")
    @GetMapping("/agents")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        tags = {ALLOW_ANONYMOUS})
    public ArdListResponse agents(
        @RequestParam(required = false) String namespaceId,
        @RequestParam(required = false) String filter,
        @RequestParam(required = false) String orderBy,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String pageToken) throws NacosException {
        return ardSearchService.list(namespaceId, filter, orderBy, pageSize, pageToken);
    }
    
    private void bindNamespaceId(String queryNamespaceId, ArdSearchRequest request)
        throws NacosApiException {
        if (request != null) {
            request.setNamespaceId(resolveNamespaceId(queryNamespaceId, request.getNamespaceId()));
        }
    }
    
    private void bindNamespaceId(String queryNamespaceId, ArdExploreRequest request)
        throws NacosApiException {
        if (request != null) {
            request.setNamespaceId(resolveNamespaceId(queryNamespaceId, request.getNamespaceId()));
        }
    }
    
    private String resolveNamespaceId(String queryNamespaceId, String bodyNamespaceId)
        throws NacosApiException {
        if (StringUtils.isBlank(queryNamespaceId)) {
            if (StringUtils.isNotBlank(bodyNamespaceId)
                && !com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID
                    .equals(bodyNamespaceId)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "ARD namespaceId should be passed as query parameter");
            }
            return bodyNamespaceId;
        }
        if (StringUtils.isNotBlank(bodyNamespaceId) && !queryNamespaceId.equals(bodyNamespaceId)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "ARD query namespaceId should match request body namespaceId");
        }
        return queryNamespaceId;
    }
    
    /**
     * Return the versioned artifact document behind an ARD catalog entry URL.
     */
    @Since("3.3.0")
    @GetMapping("/artifacts")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        tags = {ALLOW_ANONYMOUS})
    public ResponseEntity<Object> artifact(
        @RequestParam String namespaceId,
        @RequestParam String resourceType,
        @RequestParam String resourceName,
        @RequestParam String version,
        @RequestParam(required = false) String mcpName) throws NacosException {
        ArdArtifact artifact = ardArtifactService.get(namespaceId, resourceType, resourceName,
            version, mcpName);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(artifact.getMediaType()))
            .body(artifact.getBody());
    }
}
