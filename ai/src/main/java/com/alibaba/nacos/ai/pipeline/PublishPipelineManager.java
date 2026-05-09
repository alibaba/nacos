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

import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeConfig;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manager for loading, caching and querying publish pipeline SPI plugins.
 *
 * <p>Uses {@link ServiceLoader} to discover all {@link PublishPipelineServiceBuilder} implementations,
 * builds {@link PublishPipelineService} instances with per-node configuration properties, and caches
 * them by pipelineId for runtime lookup.</p>
 *
 * @author kiro
 * @since 3.2.0
 */
public class PublishPipelineManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(PublishPipelineManager.class);
    
    /**
     * Cached pipeline services keyed by pipelineId.
     */
    private final Map<String, PublishPipelineService> serviceMap = new HashMap<>();
    
    /**
     * Initialize the manager by loading all SPI builders and building pipeline service instances.
     *
     * <p>For each builder, looks up the corresponding node configuration from the config. If found,
     * passes the node's properties to {@code builder.build(properties)}; otherwise passes empty Properties.
     * If a builder throws an exception, it is logged and skipped.</p>
     *
     * @param config the pipeline configuration containing node properties
     */
    public void init(PipelineConfig config) {
        ServiceLoader<PublishPipelineServiceBuilder> builders =
            ServiceLoader.load(PublishPipelineServiceBuilder.class);
        initWithBuilders(builders, config);
    }
    
    /**
     * Initialize the manager with the given builders and config. Package-private for testability.
     *
     * @param builders iterable of pipeline service builders
     * @param config   the pipeline configuration containing node properties
     */
    void initWithBuilders(Iterable<PublishPipelineServiceBuilder> builders, PipelineConfig config) {
        Map<String, PipelineNodeConfig> nodeConfigMap = new HashMap<>();
        if (config.getNodes() != null) {
            for (PipelineNodeConfig node : config.getNodes()) {
                if (node.getPipelineId() != null) {
                    nodeConfigMap.put(node.getPipelineId(), node);
                }
            }
        }
        
        for (PublishPipelineServiceBuilder builder : builders) {
            try {
                PipelineNodeConfig nodeConfig = nodeConfigMap.get(builder.pipelineId());
                Properties properties = (nodeConfig != null && nodeConfig.getProperties() != null)
                    ? nodeConfig.getProperties() : new Properties();
                PublishPipelineService service = builder.build(properties);
                if (service != null && service.pipelineId() != null) {
                    serviceMap.put(service.pipelineId(), service);
                    LOG.info("Loaded pipeline plugin: {}", service.pipelineId());
                }
            } catch (Exception e) {
                LOG.warn("Failed to load pipeline plugin: {}", builder.pipelineId(), e);
            }
        }
    }
    
    /**
     * Get pipeline services matching the given resource type and node configuration list.
     *
     * <p>Filters services that support the specified resource type and whose pipelineId is present
     * in the nodes list. Results are sorted by {@link PublishPipelineService#getPreferOrder()} ascending.</p>
     *
     * @param resourceType the resource type to filter by
     * @param nodes        the configured pipeline nodes to match against
     * @return sorted list of matching pipeline services, never null, no null elements
     */
    public List<PublishPipelineService> getPipelineServices(
        PublishPipelineResourceType resourceType,
        List<PipelineNodeConfig> nodes) {
        Set<String> pipelineIds = nodes.stream()
            .map(PipelineNodeConfig::getPipelineId)
            .collect(Collectors.toSet());
        
        return serviceMap.values().stream()
            .filter(service -> pipelineIds.contains(service.pipelineId()))
            .filter(service -> supportsResourceType(service, resourceType))
            .sorted(Comparator.comparingInt(PublishPipelineService::getPreferOrder))
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
