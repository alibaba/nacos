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
import com.alibaba.nacos.ai.service.ard.ArdSearchService;
import com.alibaba.nacos.api.ai.model.ard.ArdCatalog;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
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
@RequestMapping(Constants.ARD_WELL_KNOWN_PATH)
public class ArdWellKnownController {
    
    static final String KEY_WELL_KNOWN_ENABLED = "nacos.ai.ard.well-known.enabled";
    
    static final String KEY_WELL_KNOWN_NAMESPACE_ID =
        "nacos.ai.ard.well-known.namespace-id";
    
    private static final String DEFAULT_WELL_KNOWN_ENABLED = "false";
    
    private static final String DEFAULT_WELL_KNOWN_NAMESPACE_ID =
        com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;
    
    private final ArdSearchService ardSearchService;
    
    public ArdWellKnownController(ArdSearchService ardSearchService) {
        this.ardSearchService = ardSearchService;
    }
    
    /**
     * Return the standard ARD catalog discovery document.
     */
    @Since("3.3.0")
    @GetMapping(value = "/ai-catalog.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ArdCatalog catalog() throws NacosException {
        if (!wellKnownEnabled()) {
            throw new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND, "ARD well-known catalog is disabled");
        }
        return ardSearchService.catalog(wellKnownNamespaceId());
    }
    
    private boolean wellKnownEnabled() {
        return Boolean.parseBoolean(property(KEY_WELL_KNOWN_ENABLED,
            DEFAULT_WELL_KNOWN_ENABLED));
    }
    
    private String wellKnownNamespaceId() {
        String namespaceId = property(KEY_WELL_KNOWN_NAMESPACE_ID,
            DEFAULT_WELL_KNOWN_NAMESPACE_ID);
        return StringUtils.isBlank(namespaceId) ? DEFAULT_WELL_KNOWN_NAMESPACE_ID : namespaceId;
    }
    
    private String property(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        try {
            return EnvUtil.getProperty(key, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
