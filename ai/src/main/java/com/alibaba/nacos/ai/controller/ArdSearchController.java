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
import com.alibaba.nacos.ai.service.ard.ArdArtifact;
import com.alibaba.nacos.ai.service.ard.ArdArtifactService;
import com.alibaba.nacos.ai.service.ard.ArdSearchService;
import com.alibaba.nacos.api.ai.model.ard.ArdCatalog;
import com.alibaba.nacos.api.ai.model.ard.ArdExploreRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdExploreResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdListResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
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
    public ArdExploreResponse explore(@RequestBody ArdExploreRequest request)
        throws NacosException {
        return ardSearchService.explore(request);
    }
    
    /**
     * Return the permission-controlled local ARD catalog document.
     */
    @Since("3.3.0")
    @GetMapping("/ai-catalog.json")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public ArdCatalog catalog(@RequestParam(required = false) String namespaceId)
        throws NacosException {
        return ardSearchService.catalog(namespaceId);
    }
    
    /**
     * List local A2A agents from the local Nacos registry.
     */
    @Since("3.3.0")
    @GetMapping("/agents")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public ArdListResponse agents(
        @RequestParam(required = false) String namespaceId,
        @RequestParam(required = false) String filter,
        @RequestParam(required = false) String orderBy,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String pageToken) throws NacosException {
        return ardSearchService.list(namespaceId, filter, orderBy, pageSize, pageToken);
    }
    
    /**
     * Return the versioned artifact document behind an ARD catalog entry URL.
     */
    @Since("3.3.0")
    @GetMapping("/artifacts")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
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
