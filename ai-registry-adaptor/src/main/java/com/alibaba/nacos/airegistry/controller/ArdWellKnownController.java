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

import com.alibaba.nacos.ai.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standard ARD well-known discovery controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@ConditionalOnArdEnabled
@RequestMapping(ArdProtocolConstants.WELL_KNOWN_PATH)
@ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
public class ArdWellKnownController {
    
    private final ArdSearchService ardSearchService;
    
    public ArdWellKnownController(ArdSearchService ardSearchService) {
        this.ardSearchService = ardSearchService;
    }
    
    /**
     * Return the standard ARD catalog discovery document.
     */
    @Since("3.3.0")
    @GetMapping(value = "/ai-catalog.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public ArdCatalog catalog() throws NacosException {
        return ardSearchService.hostCatalog();
    }
}
