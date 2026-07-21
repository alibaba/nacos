/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.pipeline;

import com.alibaba.nacos.ai.config.AiPipelineModuleConfig;
import com.alibaba.nacos.api.plugin.PluginStateChecker;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Manager for loading, caching and querying publish pipeline SPI plugins.
 *
 * <p>Uses {@link ServiceLoader} to discover {@link PublishPipelineService} implementations and
 * caches them by pipelineId for runtime lookup. Service configuration is applied by the common
 * plugin configuration lifecycle.</p>
 *
 * @author kiro
 * @since 3.2.0
 */
public class PublishPipelineManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(PublishPipelineManager.class);
    
    private final AiPipelineModuleConfig moduleConfig;
    
    /**
     * Cached pipeline services keyed by pipelineId.
     */
    private final Map<String, PublishPipelineService> serviceMap = new HashMap<>();
    
    public PublishPipelineManager(AiPipelineModuleConfig moduleConfig) {
        this.moduleConfig = Objects.requireNonNull(moduleConfig,
            "AI pipeline module config cannot be null");
    }
    
    /**
     * Load direct pipeline service implementations through Java SPI.
     */
    public void init() {
        ServiceLoader<PublishPipelineService> services =
            ServiceLoader.load(PublishPipelineService.class);
        initWithServices(services);
    }
    
    /**
     * Initialize the manager with the given services. Package-private for testability.
     *
     * @param services iterable of pipeline services
     */
    void initWithServices(Iterable<PublishPipelineService> services) {
        for (PublishPipelineService service : services) {
            try {
                if (service != null && service.pipelineId() != null) {
                    PublishPipelineService previous = serviceMap.putIfAbsent(
                        service.pipelineId(), service);
                    if (previous == null) {
                        LOG.info("Loaded pipeline plugin: {}", service.pipelineId());
                    } else {
                        LOG.warn("Ignored duplicate pipeline plugin: {}", service.pipelineId());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to load pipeline plugin", e);
            }
        }
    }
    
    /**
     * Get enabled pipeline services matching the given resource type.
     *
     * <p>The module switch controls whether the pipeline capability is entered. Unified plugin
     * state selects chain members, and each configured service supplies its applied order.</p>
     *
     * @param resourceType the resource type to filter by
     * @return sorted list of matching pipeline services, never null, no null elements
     */
    public List<PublishPipelineService> getPipelineServices(
        PublishPipelineResourceType resourceType) {
        if (!moduleConfig.isEnabled()) {
            return Collections.emptyList();
        }
        Optional<PluginStateChecker> stateChecker = PluginStateCheckerHolder.getInstance();
        if (stateChecker.isEmpty()) {
            LOG.debug("Unified plugin state is not initialized; skip publish pipelines");
            return Collections.emptyList();
        }
        return serviceMap.values().stream()
            .filter(service -> stateChecker.get().isPluginEnabled(
                PluginType.AI_PIPELINE.getType(), service.pipelineId()))
            .filter(service -> supportsResourceType(service, resourceType))
            .sorted(Comparator.comparingInt(PublishPipelineService::getPreferOrder)
                .thenComparing(PublishPipelineService::pipelineId))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all loaded pipeline services.
     *
     * @return collection of all cached pipeline services
     */
    public Collection<PublishPipelineService> getAllServices() {
        return serviceMap.values();
    }
    
    private boolean supportsResourceType(PublishPipelineService service,
        PublishPipelineResourceType resourceType) {
        PublishPipelineResourceType[] types = service.pipelineResourceTypes();
        if (types == null) {
            return false;
        }
        return Arrays.asList(types).contains(resourceType);
    }
    
}
