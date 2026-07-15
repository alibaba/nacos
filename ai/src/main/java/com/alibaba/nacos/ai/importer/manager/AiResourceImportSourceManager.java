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

import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.ai.importer.config.AiResourceImportSourceConfig;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportSourceProvider;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolves operator-configured AI resource import sources.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportSourceManager {
    
    private static final List<String> DEFAULT_CAPABILITIES =
        Arrays.asList("search", "validate", "execute");
    
    private final AiResourceImportPluginManager pluginManager;
    
    private Supplier<AiResourceImportProperties> propertiesSupplier;
    
    private Supplier<Properties> rawPropertiesSupplier;
    
    private Supplier<Collection<AiResourceImportSourceProvider>> sourceProvidersSupplier;
    
    public AiResourceImportSourceManager(AiResourceImportPluginManager pluginManager) {
        this.pluginManager = pluginManager;
        this.propertiesSupplier = AiResourceImportProperties::loadFromEnvironment;
        this.rawPropertiesSupplier = EnvUtil::getProperties;
        this.sourceProvidersSupplier =
            () -> NacosServiceLoader.load(AiResourceImportSourceProvider.class);
    }
    
    /**
     * List enabled sources and hide runtime-only fields such as endpoint and secrets.
     *
     * @param resourceType optional resource type filter
     * @return import source infos
     * @throws NacosException if source configuration is invalid
     */
    public List<AiResourceImportSourceInfo> listSourceInfos(String resourceType)
        throws NacosException {
        AiResourceImportProperties properties = propertiesSupplier.get();
        List<AiResourceImportSourceInfo> result = new ArrayList<>();
        for (AiResourceImportSource each : validateAndGetSources(properties)) {
            if (!each.isEnabled() || !supportsResourceType(each, resourceType)) {
                continue;
            }
            result.add(toSourceInfo(each));
        }
        return result;
    }
    
    /**
     * Resolve an enabled source for request execution.
     *
     * @param sourceId source id
     * @param resourceType expected resource type
     * @return runtime source
     * @throws NacosException if source is missing, disabled, or incompatible
     */
    public AiResourceImportSource resolveSource(String sourceId, String resourceType)
        throws NacosException {
        AiResourceImportProperties properties = propertiesSupplier.get();
        List<AiResourceImportSource> sources = validateAndGetSources(properties);
        if (sources.isEmpty() && !properties.isEnabled()) {
            throwDisabled();
        }
        for (AiResourceImportSource each : sources) {
            if (!StringUtils.equals(each.getSourceId(), sourceId)) {
                continue;
            }
            if (!each.isEnabled()) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "AI resource import source is disabled: " + sourceId);
            }
            if (!supportsResourceType(each, resourceType)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "AI resource import source does not support resource type: " + resourceType);
            }
            return each;
        }
        throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "AI resource import source not found: " + sourceId);
    }
    
    private List<AiResourceImportSource> validateAndGetSources(
        AiResourceImportProperties properties) throws NacosException {
        List<AiResourceImportSource> result = new ArrayList<>();
        result.addAll(loadConfiguredSources(properties));
        result.addAll(loadProviderSources());
        Set<String> sourceIds = new HashSet<>();
        for (AiResourceImportSource each : result) {
            validateSource(each, sourceIds);
        }
        return result;
    }
    
    private List<AiResourceImportSource> loadConfiguredSources(
        AiResourceImportProperties properties) {
        if (!properties.isEnabled() || CollectionUtils.isEmpty(properties.getSources())) {
            return Collections.emptyList();
        }
        List<AiResourceImportSource> result = new ArrayList<>(properties.getSources().size());
        for (AiResourceImportSourceConfig each : properties.getSources()) {
            result.add(toRuntimeSource(each));
        }
        return result;
    }
    
    private List<AiResourceImportSource> loadProviderSources() throws NacosException {
        Collection<AiResourceImportSourceProvider> providers = sourceProvidersSupplier.get();
        if (CollectionUtils.isEmpty(providers)) {
            return Collections.emptyList();
        }
        Properties rawProperties = rawPropertiesSupplier.get();
        if (rawProperties == null) {
            rawProperties = new Properties();
        }
        List<AiResourceImportSource> result = new ArrayList<>();
        for (AiResourceImportSourceProvider each : providers) {
            Collection<AiResourceImportSource> sources = each.loadSources(rawProperties);
            if (CollectionUtils.isNotEmpty(sources)) {
                result.addAll(sources);
            }
        }
        return result;
    }
    
    private void validateSource(AiResourceImportSource source, Set<String> sourceIds)
        throws NacosException {
        if (StringUtils.isBlank(source.getSourceId())) {
            throw invalidConfig("AI resource import source id must not be empty.");
        }
        if (!sourceIds.add(source.getSourceId())) {
            throw invalidConfig("Duplicate AI resource import source id: " + source.getSourceId());
        }
        if (StringUtils.isBlank(source.getPluginName())) {
            throw invalidConfig("AI resource import plugin name must not be empty.");
        }
        if (CollectionUtils.isEmpty(source.getResourceTypes())) {
            throw invalidConfig("AI resource import source must declare resource types: "
                + source.getSourceId());
        }
        if (!pluginManager.hasImporter(source.getPluginName())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AI resource import plugin not found: " + source.getPluginName());
        }
    }
    
    private NacosException invalidConfig(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private void throwDisabled() throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, "AI resource import is disabled.");
    }
    
    private boolean supportsResourceType(AiResourceImportSource source, String resourceType) {
        return StringUtils.isBlank(resourceType)
            || source.getResourceTypes().contains(resourceType);
    }
    
    private AiResourceImportSourceInfo toSourceInfo(AiResourceImportSource source) {
        AiResourceImportSourceInfo result = new AiResourceImportSourceInfo();
        result.setSourceId(source.getSourceId());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setPluginName(source.getPluginName());
        result.setResourceTypes(source.getResourceTypes());
        result.setEnabled(source.isEnabled());
        result.setCapabilities(DEFAULT_CAPABILITIES);
        return result;
    }
    
    private AiResourceImportSource toRuntimeSource(AiResourceImportSourceConfig source) {
        AiResourceImportSource result = new AiResourceImportSource();
        result.setSourceId(source.getSourceId());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setPluginName(source.getPluginName());
        result.setResourceTypes(source.getResourceTypes());
        result.setEndpoint(source.getEndpoint());
        result.setEnabled(source.isEnabled());
        result.setAuthRef(source.getAuthRef());
        result.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        result.setReadTimeoutMillis(source.getReadTimeoutMillis());
        result.setMaxPageCount(source.getMaxPageCount());
        result.setMaxItemCount(source.getMaxItemCount());
        result.setMaxArtifactSize(source.getMaxArtifactSize());
        result.setProperties(source.getProperties());
        return result;
    }
}
