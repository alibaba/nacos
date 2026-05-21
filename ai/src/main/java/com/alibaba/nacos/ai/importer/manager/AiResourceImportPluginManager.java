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

package com.alibaba.nacos.ai.importer.manager;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads and routes AI resource import plugins.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportPluginManager {
    
    private final Map<String, AiResourceImportServiceBuilder> builders;
    
    public AiResourceImportPluginManager() {
        this(NacosServiceLoader.load(AiResourceImportServiceBuilder.class));
    }
    
    AiResourceImportPluginManager(Collection<AiResourceImportServiceBuilder> builders) {
        Map<String, AiResourceImportServiceBuilder> loadedBuilders = new LinkedHashMap<>();
        for (AiResourceImportServiceBuilder each : builders) {
            if (StringUtils.isBlank(each.importerType())) {
                throw new IllegalStateException("AI resource importer type must not be empty.");
            }
            if (loadedBuilders.containsKey(each.importerType())) {
                throw new IllegalStateException(
                    "Duplicate AI resource importer type: " + each.importerType());
            }
            loadedBuilders.put(each.importerType(), each);
        }
        this.builders = Collections.unmodifiableMap(loadedBuilders);
    }
    
    /**
     * Whether the importer builder exists.
     *
     * @param importerType importer type
     * @return true if the importer exists
     */
    public boolean hasImporter(String importerType) {
        return builders.containsKey(importerType);
    }
    
    /**
     * Resolve an importer for a source and resource type.
     *
     * @param source resolved source
     * @param resourceType expected resource type
     * @return importer service
     * @throws NacosException if importer is missing or cannot support the resource type
     */
    public AiResourceImportService resolveImporter(AiResourceImportSource source,
        String resourceType) throws NacosException {
        AiResourceImportServiceBuilder builder = builders.get(source.getPluginName());
        if (builder == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AI resource import plugin not found: " + source.getPluginName());
        }
        AiResourceImportService service = builder.build(toProperties(source));
        if (service == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.DATA_ACCESS_ERROR,
                "AI resource import plugin returned null service: " + source.getPluginName());
        }
        if (!StringUtils.equals(source.getPluginName(), service.importerType())) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.DATA_ACCESS_ERROR,
                "AI resource import plugin type mismatch: " + source.getPluginName());
        }
        if (CollectionUtils.isEmpty(service.supportedResourceTypes())
            || !service.supportedResourceTypes().contains(resourceType)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AI resource import plugin does not support resource type: " + resourceType);
        }
        return service;
    }
    
    private Properties toProperties(AiResourceImportSource source) {
        Properties properties = new Properties();
        if (source.getProperties() != null) {
            properties.putAll(source.getProperties());
        }
        return properties;
    }
}
