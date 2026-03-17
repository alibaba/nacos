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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for PublishPipelineManager.
 *
 * <p><b>Validates: Requirements 5.2, 5.3, 5.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineManagerPropertyTest {
    
    /**
     * Property 11: SPI loading fault tolerance — single Builder exception doesn't affect other plugins.
     *
     * <p>For any set of PublishPipelineServiceBuilder instances where some builders' build() throws
     * an exception, PublishPipelineManager should still successfully load all non-throwing builders'
     * services. Throwing builders' services should not appear in getAllServices().</p>
     *
     * <p><b>Validates: Requirements 5.2</b></p>
     */
    @Property
    void spiLoadingFaultTolerance(@ForAll("builderDescriptorSets") BuilderDescriptorSet descriptorSet) {
        PublishPipelineManager manager = new PublishPipelineManager();
        
        List<PublishPipelineServiceBuilder> builders = new ArrayList<>();
        for (BuilderDescriptor desc : descriptorSet.descriptors) {
            builders.add(createMockBuilder(desc));
        }
        
        PipelineConfig config = new PipelineConfig();
        config.setEnabled(true);
        config.setNodes(new ArrayList<>());
        
        // Should not throw even if some builders fail
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
        
        // All normal builders should be loaded
        for (String normalId : normalIds) {
            assertTrue(loadedIds.contains(normalId),
                    "Normal builder '" + normalId + "' should be loaded but was not");
        }
        
        // No failing builder should be loaded
        for (String failingId : failingIds) {
            assertFalse(loadedIds.contains(failingId),
                    "Failing builder '" + failingId + "' should NOT be loaded but was");
        }
    }
    
    @Provide
    Arbitrary<BuilderDescriptorSet> builderDescriptorSets() {
        return builderDescriptor()
                .list().ofMinSize(1).ofMaxSize(8)
                .filter(list -> list.stream().map(d -> d.pipelineId).distinct().count() == list.size())
                .filter(list -> list.stream().anyMatch(d -> !d.shouldThrow))
                .map(BuilderDescriptorSet::new);
    }
    
    private Arbitrary<BuilderDescriptor> builderDescriptor() {
        Arbitrary<String> pipelineIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12)
                .map(String::toLowerCase);
        Arbitrary<Boolean> shouldThrow = Arbitraries.of(true, false);
        Arbitrary<Integer> orders = Arbitraries.integers().between(0, 1000);
        
        return Combinators.combine(pipelineIds, shouldThrow, orders).as(BuilderDescriptor::new);
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
    
    /**
     * Descriptor for a single builder in the test.
     */
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
    
    /**
     * Set of builder descriptors for a single test run.
     */
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
    
    // ---- Property 12: getPipelineServices filtering and sorting ----
    
    /**
     * Property 12: getPipelineServices filtering and sorting.
     *
     * <p>For any resourceType and nodes configuration list, getPipelineServices returns a service list
     * where every service supports the given resourceType, every service's pipelineId is in the nodes list,
     * the list is sorted by getPreferOrder() ascending, and contains no null elements.
     * Additionally, no service that matches both criteria is excluded from the result.</p>
     *
     * <p><b>Validates: Requirements 5.3, 5.4</b></p>
     */
    @Property
    void getPipelineServicesFilteringAndSorting(@ForAll("filterTestInputs") FilterTestInput input) {
        PublishPipelineManager manager = new PublishPipelineManager();
        
        // Build all services (none should throw) and load them into the manager
        List<PublishPipelineServiceBuilder> builders = new ArrayList<>();
        for (ServiceDescriptor desc : input.allServices) {
            builders.add(createServiceBuilder(desc));
        }
        
        PipelineConfig config = new PipelineConfig();
        config.setEnabled(true);
        config.setNodes(new ArrayList<>());
        manager.initWithBuilders(builders, config);
        
        // Build the nodes list (subset of pipelineIds to include)
        List<PipelineNodeConfig> nodes = new ArrayList<>();
        for (String nodeId : input.includedPipelineIds) {
            PipelineNodeConfig nodeConfig = new PipelineNodeConfig();
            nodeConfig.setPipelineId(nodeId);
            nodeConfig.setProperties(new Properties());
            nodes.add(nodeConfig);
        }
        
        // Call the method under test
        List<PublishPipelineService> result = manager.getPipelineServices(input.targetResourceType, nodes);
        
        // Compute expected matching pipelineIds
        Set<String> nodeIdSet = new HashSet<>(input.includedPipelineIds);
        Set<String> expectedIds = input.allServices.stream()
                .filter(d -> nodeIdSet.contains(d.pipelineId))
                .filter(d -> Arrays.asList(d.supportedTypes).contains(input.targetResourceType))
                .map(d -> d.pipelineId)
                .collect(Collectors.toSet());
        
        // 1. No null elements
        for (PublishPipelineService service : result) {
            assertNotNull(service, "Result list should not contain null elements");
        }
        
        // 2. Every returned service supports the given resourceType
        for (PublishPipelineService service : result) {
            assertTrue(
                    Arrays.asList(service.pipelineResourceTypes()).contains(input.targetResourceType),
                    "Service '" + service.pipelineId() + "' should support " + input.targetResourceType);
        }
        
        // 3. Every returned service's pipelineId is in the nodes list
        for (PublishPipelineService service : result) {
            assertTrue(nodeIdSet.contains(service.pipelineId()),
                    "Service '" + service.pipelineId() + "' should be in the nodes list");
        }
        
        // 4. Sorted by getPreferOrder() ascending
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).getPreferOrder() <= result.get(i).getPreferOrder(),
                    "Result should be sorted by preferOrder ascending, but index " + (i - 1)
                            + " (order=" + result.get(i - 1).getPreferOrder() + ") > index " + i
                            + " (order=" + result.get(i).getPreferOrder() + ")");
        }
        
        // 5. No services that match both criteria are excluded (completeness)
        Set<String> resultIds = result.stream()
                .map(PublishPipelineService::pipelineId)
                .collect(Collectors.toSet());
        for (String expectedId : expectedIds) {
            assertTrue(resultIds.contains(expectedId),
                    "Service '" + expectedId + "' matches both criteria but was not in the result");
        }
    }
    
    @Provide
    Arbitrary<FilterTestInput> filterTestInputs() {
        Arbitrary<PublishPipelineResourceType> resourceTypes = Arbitraries.of(PublishPipelineResourceType.values());
        
        Arbitrary<List<ServiceDescriptor>> serviceDescriptors = serviceDescriptor()
                .list().ofMinSize(1).ofMaxSize(10)
                .filter(list -> list.stream().map(d -> d.pipelineId).distinct().count() == list.size());
        
        return Combinators.combine(serviceDescriptors, resourceTypes).as((services, targetType) -> {
            // Pick a random subset of pipelineIds to include in nodes
            List<String> allIds = services.stream().map(d -> d.pipelineId).collect(Collectors.toList());
            return new FilterTestInput(services, targetType, allIds);
        }).flatMap(input -> {
            // Generate a subset of pipelineIds for the nodes list
            List<String> allIds = input.allServices.stream()
                    .map(d -> d.pipelineId).collect(Collectors.toList());
            return Arbitraries.of(allIds).set().ofMinSize(0).ofMaxSize(allIds.size())
                    .map(subset -> new FilterTestInput(
                            input.allServices, input.targetResourceType, new ArrayList<>(subset)));
        });
    }
    
    private Arbitrary<ServiceDescriptor> serviceDescriptor() {
        Arbitrary<String> pipelineIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12)
                .map(String::toLowerCase);
        Arbitrary<Integer> orders = Arbitraries.integers().between(0, 1000);
        Arbitrary<PublishPipelineResourceType[]> supportedTypes = Arbitraries.of(PublishPipelineResourceType.values())
                .set().ofMinSize(1).ofMaxSize(PublishPipelineResourceType.values().length)
                .map(set -> set.toArray(new PublishPipelineResourceType[0]));
        
        return Combinators.combine(pipelineIds, orders, supportedTypes).as(ServiceDescriptor::new);
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
    
    /**
     * Descriptor for a pipeline service with its supported resource types.
     */
    static class ServiceDescriptor {
        
        final String pipelineId;
        
        final int order;
        
        final PublishPipelineResourceType[] supportedTypes;
        
        ServiceDescriptor(String pipelineId, int order, PublishPipelineResourceType[] supportedTypes) {
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
    
    /**
     * Input data for the getPipelineServices filtering and sorting property test.
     */
    static class FilterTestInput {
        
        final List<ServiceDescriptor> allServices;
        
        final PublishPipelineResourceType targetResourceType;
        
        final List<String> includedPipelineIds;
        
        FilterTestInput(List<ServiceDescriptor> allServices, PublishPipelineResourceType targetResourceType,
                List<String> includedPipelineIds) {
            this.allServices = allServices;
            this.targetResourceType = targetResourceType;
            this.includedPipelineIds = includedPipelineIds;
        }
        
        @Override
        public String toString() {
            return "FilterTestInput{allServices=" + allServices + ", targetResourceType=" + targetResourceType
                    + ", includedPipelineIds=" + includedPipelineIds + "}";
        }
    }
}
