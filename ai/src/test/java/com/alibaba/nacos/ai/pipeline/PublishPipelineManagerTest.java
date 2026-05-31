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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PublishPipelineManager.
 *
 * <p><b>Validates: Requirements 5.2, 5.3, 5.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineManagerTest {
    
    private static List<BuilderDescriptorSet> sampleBuilderDescriptorSets() {
        List<BuilderDescriptorSet> list = new ArrayList<>();
        list.add(new BuilderDescriptorSet(Arrays.asList(
            new BuilderDescriptor("alpha", false, 1),
            new BuilderDescriptor("beta", true, 2))));
        list.add(new BuilderDescriptorSet(Arrays.asList(
            new BuilderDescriptor("pipea", false, 10),
            new BuilderDescriptor("pipeb", false, 20),
            new BuilderDescriptor("failc", true, 30))));
        list.add(new BuilderDescriptorSet(Collections.singletonList(
            new BuilderDescriptor("onlyok", false, 0))));
        return list;
    }
    
    /**
     * SPI loading fault tolerance — single Builder exception doesn't affect other plugins.
     *
     * <p><b>Validates: Requirements 5.2</b></p>
     */
    @Test
    void spiLoadingFaultTolerance() {
        for (BuilderDescriptorSet descriptorSet : sampleBuilderDescriptorSets()) {
            PublishPipelineManager manager = new PublishPipelineManager();
            
            List<PublishPipelineServiceBuilder> builders = new ArrayList<>();
            for (BuilderDescriptor desc : descriptorSet.descriptors) {
                builders.add(createMockBuilder(desc));
            }
            
            PipelineConfig config = new PipelineConfig();
            config.setEnabled(true);
            config.setNodes(new ArrayList<>());
            
            manager.initWithBuilders(builders, config);
            
            Collection<PublishPipelineService> loadedServices = manager.getAllServices();
            
            Set<String> normalIds = descriptorSet.descriptors.stream()
                .filter(d -> !d.shouldThrow)
                .map(d -> d.pipelineId)
                .collect(Collectors.toSet());
            
            Set<String> failingIds = descriptorSet.descriptors.stream()
                .filter(d -> d.shouldThrow)
                .map(d -> d.pipelineId)
                .collect(Collectors.toSet());
            
            Set<String> loadedIds = loadedServices.stream()
                .map(PublishPipelineService::pipelineId)
                .collect(Collectors.toSet());
            
            for (String normalId : normalIds) {
                assertTrue(loadedIds.contains(normalId),
                    "Normal builder '" + normalId + "' should be loaded but was not");
            }
            
            for (String failingId : failingIds) {
                assertFalse(loadedIds.contains(failingId),
                    "Failing builder '" + failingId + "' should NOT be loaded but was");
            }
        }
    }
    
    private static List<FilterTestInput> sampleFilterTestInputs() {
        List<FilterTestInput> list = new ArrayList<>();
        List<ServiceDescriptor> svc1 = Arrays.asList(
            new ServiceDescriptor("a", 1,
                new PublishPipelineResourceType[] {PublishPipelineResourceType.SKILL}),
            new ServiceDescriptor("b", 2, new PublishPipelineResourceType[] {
                PublishPipelineResourceType.SKILL, PublishPipelineResourceType.AGENTSPEC}));
        list.add(
            new FilterTestInput(svc1, PublishPipelineResourceType.SKILL, Arrays.asList("a", "b")));
        list.add(new FilterTestInput(svc1, PublishPipelineResourceType.SKILL,
            Collections.singletonList("a")));
        list.add(new FilterTestInput(svc1, PublishPipelineResourceType.SKILL, new ArrayList<>()));
        
        List<ServiceDescriptor> svc2 = Arrays.asList(
            new ServiceDescriptor("p1", 0, PublishPipelineResourceType.values()),
            new ServiceDescriptor("p2", 5,
                new PublishPipelineResourceType[] {PublishPipelineResourceType.PROMPT}));
        list.add(new FilterTestInput(svc2, PublishPipelineResourceType.PROMPT,
            Arrays.asList("p1", "p2")));
        list.add(
            new FilterTestInput(svc2, PublishPipelineResourceType.AGENTSPEC, Arrays.asList("p1")));
        return list;
    }
    
    /**
     * getPipelineServices filtering and sorting.
     *
     * <p><b>Validates: Requirements 5.3, 5.4</b></p>
     */
    @Test
    void getPipelineServicesFilteringAndSorting() {
        for (FilterTestInput input : sampleFilterTestInputs()) {
            PublishPipelineManager manager = new PublishPipelineManager();
            
            List<PublishPipelineServiceBuilder> builders = new ArrayList<>();
            for (ServiceDescriptor desc : input.allServices) {
                builders.add(createServiceBuilder(desc));
            }
            
            PipelineConfig config = new PipelineConfig();
            config.setEnabled(true);
            config.setNodes(new ArrayList<>());
            manager.initWithBuilders(builders, config);
            
            List<PipelineNodeConfig> nodes = new ArrayList<>();
            for (String nodeId : input.includedPipelineIds) {
                PipelineNodeConfig nodeConfig = new PipelineNodeConfig();
                nodeConfig.setPipelineId(nodeId);
                nodeConfig.setProperties(new Properties());
                nodes.add(nodeConfig);
            }
            
            List<PublishPipelineService> result =
                manager.getPipelineServices(input.targetResourceType, nodes);
            
            Set<String> nodeIdSet = new HashSet<>(input.includedPipelineIds);
            Set<String> expectedIds = input.allServices.stream()
                .filter(d -> nodeIdSet.contains(d.pipelineId))
                .filter(d -> Arrays.asList(d.supportedTypes).contains(input.targetResourceType))
                .map(d -> d.pipelineId)
                .collect(Collectors.toSet());
            
            for (PublishPipelineService service : result) {
                assertNotNull(service, "Result list should not contain null elements");
            }
            
            for (PublishPipelineService service : result) {
                assertTrue(
                    Arrays.asList(service.pipelineResourceTypes())
                        .contains(input.targetResourceType),
                    "Service '" + service.pipelineId() + "' should support "
                        + input.targetResourceType);
            }
            
            for (PublishPipelineService service : result) {
                assertTrue(nodeIdSet.contains(service.pipelineId()),
                    "Service '" + service.pipelineId() + "' should be in the nodes list");
            }
            
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i - 1).getPreferOrder() <= result.get(i).getPreferOrder(),
                    "Result should be sorted by preferOrder ascending, but index " + (i - 1)
                        + " (order=" + result.get(i - 1).getPreferOrder() + ") > index " + i
                        + " (order=" + result.get(i).getPreferOrder() + ")");
            }
            
            Set<String> resultIds = result.stream()
                .map(PublishPipelineService::pipelineId)
                .collect(Collectors.toSet());
            for (String expectedId : expectedIds) {
                assertTrue(resultIds.contains(expectedId),
                    "Service '" + expectedId + "' matches both criteria but was not in the result");
            }
        }
    }
    
    private PublishPipelineServiceBuilder createMockBuilder(BuilderDescriptor desc) {
        return new PublishPipelineServiceBuilder() {
            
            @Override
            public String pipelineId() {
                return desc.pipelineId;
            }
            
            @Override
            public PublishPipelineService build(Properties properties) {
                if (desc.shouldThrow) {
                    throw new RuntimeException("Simulated build failure for " + desc.pipelineId);
                }
                return new PublishPipelineService() {
                    
                    @Override
                    public String pipelineId() {
                        return desc.pipelineId;
                    }
                    
                    @Override
                    public com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult execute(
                        com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext context) {
                        return null;
                    }
                    
                    @Override
                    public int getPreferOrder() {
                        return desc.order;
                    }
                    
                    @Override
                    public PublishPipelineResourceType[] pipelineResourceTypes() {
                        return PublishPipelineResourceType.values();
                    }
                };
            }
        };
    }
    
    static class BuilderDescriptor {
        
        final String pipelineId;
        
        final boolean shouldThrow;
        
        final int order;
        
        BuilderDescriptor(String pipelineId, boolean shouldThrow, int order) {
            this.pipelineId = pipelineId;
            this.shouldThrow = shouldThrow;
            this.order = order;
        }
        
        @Override
        public String toString() {
            return "BuilderDescriptor{pipelineId='" + pipelineId + "', shouldThrow=" + shouldThrow
                + ", order=" + order + "}";
        }
    }
    
    static class BuilderDescriptorSet {
        
        final List<BuilderDescriptor> descriptors;
        
        BuilderDescriptorSet(List<BuilderDescriptor> descriptors) {
            this.descriptors = descriptors;
        }
        
        @Override
        public String toString() {
            return "BuilderDescriptorSet{descriptors=" + descriptors + "}";
        }
    }
    
    private PublishPipelineServiceBuilder createServiceBuilder(ServiceDescriptor desc) {
        return new PublishPipelineServiceBuilder() {
            
            @Override
            public String pipelineId() {
                return desc.pipelineId;
            }
            
            @Override
            public PublishPipelineService build(Properties properties) {
                return new PublishPipelineService() {
                    
                    @Override
                    public String pipelineId() {
                        return desc.pipelineId;
                    }
                    
                    @Override
                    public com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult execute(
                        com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext context) {
                        return null;
                    }
                    
                    @Override
                    public int getPreferOrder() {
                        return desc.order;
                    }
                    
                    @Override
                    public PublishPipelineResourceType[] pipelineResourceTypes() {
                        return desc.supportedTypes;
                    }
                };
            }
        };
    }
    
    static class ServiceDescriptor {
        
        final String pipelineId;
        
        final int order;
        
        final PublishPipelineResourceType[] supportedTypes;
        
        ServiceDescriptor(String pipelineId, int order,
            PublishPipelineResourceType[] supportedTypes) {
            this.pipelineId = pipelineId;
            this.order = order;
            this.supportedTypes = supportedTypes;
        }
        
        @Override
        public String toString() {
            return "ServiceDescriptor{pipelineId='" + pipelineId + "', order=" + order
                + ", supportedTypes=" + Arrays.toString(supportedTypes) + "}";
        }
    }
    
    static class FilterTestInput {
        
        final List<ServiceDescriptor> allServices;
        
        final PublishPipelineResourceType targetResourceType;
        
        final List<String> includedPipelineIds;
        
        FilterTestInput(List<ServiceDescriptor> allServices,
            PublishPipelineResourceType targetResourceType,
            List<String> includedPipelineIds) {
            this.allServices = allServices;
            this.targetResourceType = targetResourceType;
            this.includedPipelineIds = includedPipelineIds;
        }
        
        @Override
        public String toString() {
            return "FilterTestInput{allServices=" + allServices + ", targetResourceType="
                + targetResourceType
                + ", includedPipelineIds=" + includedPipelineIds + "}";
        }
    }
}
