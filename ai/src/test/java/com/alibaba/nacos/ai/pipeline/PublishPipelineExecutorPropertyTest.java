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

import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for PublishPipelineExecutor.
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.4, 6.4, 7.1, 7.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineExecutorPropertyTest {
    
    // ---- Synchronous executor for deterministic testing ----
    
    private static ExecutorService directExecutor() {
        return new AbstractExecutorService() {
            private volatile boolean shutdown = false;
            
            @Override
            public void execute(Runnable command) {
                command.run();
            }
            
            @Override
            public void shutdown() {
                shutdown = true;
            }
            
            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return Collections.emptyList();
            }
            
            @Override
            public boolean isShutdown() {
                return shutdown;
            }
            
            @Override
            public boolean isTerminated() {
                return shutdown;
            }
            
            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }
    
    // ---- No-op repository ----
    
    private static PipelineExecutionRepository noOpRepository() {
        return new PipelineExecutionRepository() {
            @Override
            public void save(PipelineExecution execution) {
            }
            
            @Override
            public void update(PipelineExecution execution) {
            }
            
            @Override
            public PipelineExecution findById(String executionId) {
                return null;
            }
            
            @Override
            public PipelineExecution findByResource(String resourceType, String resourceName,
                    String namespaceId, String version) {
                return null;
            }
            
            @Override
            public List<PipelineExecution> findByResourceWithPage(String resourceType, String resourceName,
                    String namespaceId, String version, int offset, int limit) {
                return Collections.emptyList();
            }
            
            @Override
            public int countByResource(String resourceType, String resourceName,
                    String namespaceId, String version) {
                return 0;
            }
        };
    }
    
    // ---- Tracking repository that counts save calls ----
    
    private static class TrackingRepository implements PipelineExecutionRepository {
        
        final AtomicInteger saveCount = new AtomicInteger(0);
        
        final AtomicInteger updateCount = new AtomicInteger(0);
        
        @Override
        public void save(PipelineExecution execution) {
            saveCount.incrementAndGet();
        }
        
        @Override
        public void update(PipelineExecution execution) {
            updateCount.incrementAndGet();
        }
        
        @Override
        public PipelineExecution findById(String executionId) {
            return null;
        }
        
        @Override
        public PipelineExecution findByResource(String resourceType, String resourceName,
                String namespaceId, String version) {
            return null;
        }
        
        @Override
        public List<PipelineExecution> findByResourceWithPage(String resourceType, String resourceName,
                String namespaceId, String version, int offset, int limit) {
            return Collections.emptyList();
        }
        
        @Override
        public int countByResource(String resourceType, String resourceName,
                String namespaceId, String version) {
            return 0;
        }
    }
    
    // ---- Throwing repository ----
    
    private static PipelineExecutionRepository throwingRepository() {
        return new PipelineExecutionRepository() {
            @Override
            public void save(PipelineExecution execution) {
                throw new RuntimeException("Simulated save failure");
            }
            
            @Override
            public void update(PipelineExecution execution) {
                throw new RuntimeException("Simulated update failure");
            }
            
            @Override
            public PipelineExecution findById(String executionId) {
                return null;
            }
            
            @Override
            public PipelineExecution findByResource(String resourceType, String resourceName,
                    String namespaceId, String version) {
                return null;
            }
            
            @Override
            public List<PipelineExecution> findByResourceWithPage(String resourceType, String resourceName,
                    String namespaceId, String version, int offset, int limit) {
                return Collections.emptyList();
            }
            
            @Override
            public int countByResource(String resourceType, String resourceName,
                    String namespaceId, String version) {
                return 0;
            }
        };
    }
    
    // ---- Helper: build a PublishPipelineContext ----
    
    private static PublishPipelineContext buildContext(PublishPipelineResourceType resourceType,
            String resourceName, String namespaceId, String version) {
        PublishPipelineContext ctx = new PublishPipelineContext();
        ctx.setResourceType(resourceType);
        ctx.setResourceName(resourceName);
        ctx.setNamespaceId(namespaceId);
        ctx.setVersion(version);
        return ctx;
    }
    
    // ---- Helper: create a passing PublishPipelineService ----
    
    private static PublishPipelineService passingService(String id, int order,
            PublishPipelineResourceType... types) {
        return new PublishPipelineService() {
            @Override
            public String pipelineId() {
                return id;
            }
            
            @Override
            public PublishPipelineResult execute(PublishPipelineContext context) {
                return PublishPipelineResult.pass("OK from " + id);
            }
            
            @Override
            public int getPreferOrder() {
                return order;
            }
            
            @Override
            public PublishPipelineResourceType[] pipelineResourceTypes() {
                return types;
            }
        };
    }
    
    // ---- Helper: create a failing PublishPipelineService ----
    
    private static PublishPipelineService failingService(String id, int order,
            PublishPipelineResourceType... types) {
        return new PublishPipelineService() {
            @Override
            public String pipelineId() {
                return id;
            }
            
            @Override
            public PublishPipelineResult execute(PublishPipelineContext context) {
                return PublishPipelineResult.reject("Rejected by " + id);
            }
            
            @Override
            public int getPreferOrder() {
                return order;
            }
            
            @Override
            public PublishPipelineResourceType[] pipelineResourceTypes() {
                return types;
            }
        };
    }
    
    // ---- Helper: create a throwing PublishPipelineService ----
    
    private static PublishPipelineService throwingService(String id, int order,
            String exceptionMsg, PublishPipelineResourceType... types) {
        return new PublishPipelineService() {
            @Override
            public String pipelineId() {
                return id;
            }
            
            @Override
            public PublishPipelineResult execute(PublishPipelineContext context) {
                throw new RuntimeException(exceptionMsg);
            }
            
            @Override
            public int getPreferOrder() {
                return order;
            }
            
            @Override
            public PublishPipelineResourceType[] pipelineResourceTypes() {
                return types;
            }
        };
    }
    
    // ---- Helper: create a PublishPipelineServiceBuilder from a service ----
    
    private static PublishPipelineServiceBuilder builderFor(PublishPipelineService service) {
        return new PublishPipelineServiceBuilder() {
            @Override
            public String pipelineId() {
                return service.pipelineId();
            }
            
            @Override
            public PublishPipelineService build(Properties properties) {
                return service;
            }
        };
    }
    
    // ---- Helper: build a PipelineConfig with given enabled flag and node ids ----
    
    private static PipelineConfig buildConfig(boolean enabled, List<String> nodeIds) {
        PipelineConfig config = new PipelineConfig();
        config.setEnabled(enabled);
        List<PipelineNodeConfig> nodes = new ArrayList<>();
        for (String id : nodeIds) {
            PipelineNodeConfig nc = new PipelineNodeConfig();
            nc.setPipelineId(id);
            nc.setProperties(new Properties());
            nodes.add(nc);
        }
        config.setNodes(nodes);
        return config;
    }
    
    // ---- Helper: build a PublishPipelineManager loaded with given services ----
    
    private static PublishPipelineManager buildManager(List<PublishPipelineService> services,
            PipelineConfig config) {
        PublishPipelineManager manager = new PublishPipelineManager();
        List<PublishPipelineServiceBuilder> builders = services.stream()
                .map(PublishPipelineExecutorPropertyTest::builderFor)
                .collect(Collectors.toList());
        manager.initWithBuilders(builders, config);
        return manager;
    }
    
    // ---- Helper: build a PipelineConfigProvider returning a fixed config ----
    
    private static PipelineConfigProvider fixedConfigProvider(PipelineConfig config) {
        return new PipelineConfigProvider() {
            @Override
            public PipelineConfig getConfig() {
                return config;
            }
            
            @Override
            public String type() {
                return "test";
            }
        };
    }

    // ======================================================================
    // Property 1 (Task 6.2): Enabled config → execute returns executionId
    // ======================================================================
    
    /**
     * Property 1: For any valid PublishPipelineContext and enabled config with matching nodes,
     * execute(context, callback) returns a non-null executionId.
     *
     * <p><b>Validates: Requirements 1.1</b></p>
     */
    @Property
    void enabledConfigReturnsExecutionId(@ForAll("validExecutionInputs") ExecutionInput input) {
        PipelineConfig config = buildConfig(true, input.nodeIds);
        List<PublishPipelineService> services = input.nodeIds.stream()
                .map(id -> passingService(id, input.nodeIds.indexOf(id),
                        PublishPipelineResourceType.values()))
                .collect(Collectors.toList());
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, noOpRepository(), directExecutor());
        
        PublishPipelineContext ctx = buildContext(input.resourceType, input.resourceName,
                input.namespaceId, input.version);
        
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        String executionId = executor.execute(ctx, resultRef::set);
        
        assertNotNull(executionId, "executionId should be non-null when pipeline is enabled with matching nodes");
    }
    
    @Provide
    Arbitrary<ExecutionInput> validExecutionInputs() {
        Arbitrary<List<String>> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(String::toLowerCase)
                .list().ofMinSize(1).ofMaxSize(5)
                .filter(list -> list.stream().distinct().count() == list.size());
        Arbitrary<PublishPipelineResourceType> resourceTypes = Arbitraries.of(PublishPipelineResourceType.values());
        Arbitrary<String> resourceNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> namespaceIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<String> versions = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                .map(s -> "v" + s);
        
        return Combinators.combine(nodeIds, resourceTypes, resourceNames, namespaceIds, versions)
                .as(ExecutionInput::new);
    }
    
    // ======================================================================
    // Property 2 (Task 6.3): Disabled or no matching nodes → no side effects
    // ======================================================================
    
    /**
     * Property 2: When pipeline is disabled OR nodes list is empty, execute returns null,
     * no records are saved, and callback is NOT called.
     *
     * <p><b>Validates: Requirements 1.2, 1.3</b></p>
     */
    @Property
    void disabledOrNoMatchingNodesHasNoSideEffects(
            @ForAll("disabledOrEmptyInputs") DisabledOrEmptyInput input) {
        PipelineConfig config = buildConfig(input.enabled, input.nodeIds);
        
        // Even if we have services loaded, if config is disabled or nodes don't match, should return null
        List<PublishPipelineService> services = new ArrayList<>();
        if (!input.nodeIds.isEmpty()) {
            // Load services with IDs that do NOT match the node IDs (to simulate no matching nodes)
            services.add(passingService("non-matching-service", 0, PublishPipelineResourceType.values()));
        }
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        TrackingRepository trackingRepo = new TrackingRepository();
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, trackingRepo, directExecutor());
        
        PublishPipelineContext ctx = buildContext(PublishPipelineResourceType.SKILL,
                "test-resource", "ns", "v1");
        
        AtomicInteger callbackCount = new AtomicInteger(0);
        String executionId = executor.execute(ctx, result -> callbackCount.incrementAndGet());
        
        assertNull(executionId, "executionId should be null when pipeline is disabled or no matching nodes");
        assertEquals(0, trackingRepo.saveCount.get(), "No records should be saved");
        assertEquals(0, callbackCount.get(), "Callback should not be called");
    }
    
    @Provide
    Arbitrary<DisabledOrEmptyInput> disabledOrEmptyInputs() {
        // Case 1: disabled config with any nodes
        Arbitrary<DisabledOrEmptyInput> disabledCase = Arbitraries.strings().alpha()
                .ofMinLength(3).ofMaxLength(8).map(String::toLowerCase)
                .list().ofMinSize(0).ofMaxSize(3)
                .map(ids -> new DisabledOrEmptyInput(false, ids));
        
        // Case 2: enabled config but empty nodes
        Arbitrary<DisabledOrEmptyInput> emptyNodesCase = Arbitraries.just(
                new DisabledOrEmptyInput(true, Collections.emptyList()));
        
        return Arbitraries.oneOf(disabledCase, emptyNodesCase);
    }
    
    // ======================================================================
    // Property 3 (Task 6.4): Callback called exactly once
    // ======================================================================
    
    /**
     * Property 3: For any valid execution scenario (enabled, matching nodes, all pass or some fail),
     * callback.onComplete() is called exactly once, and result.status is APPROVED or REJECTED.
     *
     * <p><b>Validates: Requirements 1.4, 1.5</b></p>
     */
    @Property
    void callbackCalledExactlyOnce(@ForAll("callbackTestInputs") CallbackTestInput input) {
        PipelineConfig config = buildConfig(true, input.nodeIds);
        
        List<PublishPipelineService> services = new ArrayList<>();
        for (int i = 0; i < input.nodeIds.size(); i++) {
            String id = input.nodeIds.get(i);
            int order = i;
            if (i == input.failAtIndex && input.failAtIndex >= 0) {
                services.add(failingService(id, order, PublishPipelineResourceType.values()));
            } else {
                services.add(passingService(id, order, PublishPipelineResourceType.values()));
            }
        }
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, noOpRepository(), directExecutor());
        
        PublishPipelineContext ctx = buildContext(input.resourceType, "res", "ns", "v1");
        
        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        
        String executionId = executor.execute(ctx, result -> {
            callbackCount.incrementAndGet();
            resultRef.set(result);
        });
        
        assertNotNull(executionId);
        assertEquals(1, callbackCount.get(), "Callback should be called exactly once");
        
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result);
        assertTrue(result.getStatus() == PipelineExecutionStatus.APPROVED
                        || result.getStatus() == PipelineExecutionStatus.REJECTED,
                "Status should be APPROVED or REJECTED, got: " + result.getStatus());
    }
    
    @Provide
    Arbitrary<CallbackTestInput> callbackTestInputs() {
        Arbitrary<List<String>> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(String::toLowerCase)
                .list().ofMinSize(1).ofMaxSize(5)
                .filter(list -> list.stream().distinct().count() == list.size());
        Arbitrary<PublishPipelineResourceType> resourceTypes = Arbitraries.of(PublishPipelineResourceType.values());
        
        return Combinators.combine(nodeIds, resourceTypes).flatAs((ids, rt) -> {
            // failAtIndex: -1 means all pass, 0..size-1 means fail at that index
            Arbitrary<Integer> failIdx = Arbitraries.integers().between(-1, ids.size() - 1);
            return failIdx.map(idx -> new CallbackTestInput(ids, rt, idx));
        });
    }
    
    // ======================================================================
    // Property 5 (Task 6.5): Fail-stop — no nodes after rejected node
    // ======================================================================
    
    /**
     * Property 5: When a node fails, no subsequent nodes appear in the pipeline result,
     * and the overall status is REJECTED.
     *
     * <p><b>Validates: Requirements 2.2, 6.4</b></p>
     */
    @Property
    void failStopNoNodesAfterRejection(@ForAll("failStopInputs") FailStopInput input) {
        PipelineConfig config = buildConfig(true, input.nodeIds);
        
        List<PublishPipelineService> services = new ArrayList<>();
        for (int i = 0; i < input.nodeIds.size(); i++) {
            String id = input.nodeIds.get(i);
            int order = i;
            if (i == input.failAtIndex) {
                services.add(failingService(id, order, PublishPipelineResourceType.values()));
            } else {
                services.add(passingService(id, order, PublishPipelineResourceType.values()));
            }
        }
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, noOpRepository(), directExecutor());
        
        PublishPipelineContext ctx = buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
        
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        executor.execute(ctx, resultRef::set);
        
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(PipelineExecutionStatus.REJECTED, result.getStatus(),
                "Status should be REJECTED when a node fails");
        
        List<PipelineNodeResult> pipeline = result.getPipeline();
        // The pipeline should contain exactly failAtIndex + 1 nodes (0..failAtIndex)
        assertEquals(input.failAtIndex + 1, pipeline.size(),
                "Pipeline should contain exactly failAtIndex+1 nodes, no more");
        
        // The last node should be the failed one
        PipelineNodeResult lastNode = pipeline.get(pipeline.size() - 1);
        assertFalse(lastNode.isPassed(), "The last node in pipeline should be the failed node");
        
        // All nodes before the last should have passed
        for (int i = 0; i < pipeline.size() - 1; i++) {
            assertTrue(pipeline.get(i).isPassed(),
                    "Node at index " + i + " should have passed");
        }
    }
    
    @Provide
    Arbitrary<FailStopInput> failStopInputs() {
        Arbitrary<List<String>> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(String::toLowerCase)
                .list().ofMinSize(1).ofMaxSize(6)
                .filter(list -> list.stream().distinct().count() == list.size());
        
        return nodeIds.flatMap(ids -> {
            Arbitrary<Integer> failIdx = Arbitraries.integers().between(0, ids.size() - 1);
            return failIdx.map(idx -> new FailStopInput(ids, idx));
        });
    }
    
    // ======================================================================
    // Property 6 (Task 6.6): Exception treated as failure
    // ======================================================================
    
    /**
     * Property 6: When a node throws an exception, that node has passed=false and
     * message contains the exception info.
     *
     * <p><b>Validates: Requirements 2.4, 7.1</b></p>
     */
    @Property
    void exceptionTreatedAsFailure(@ForAll("exceptionInputs") ExceptionInput input) {
        PipelineConfig config = buildConfig(true, input.nodeIds);
        
        List<PublishPipelineService> services = new ArrayList<>();
        for (int i = 0; i < input.nodeIds.size(); i++) {
            String id = input.nodeIds.get(i);
            int order = i;
            if (i == input.throwAtIndex) {
                services.add(throwingService(id, order, input.exceptionMessage,
                        PublishPipelineResourceType.values()));
            } else {
                services.add(passingService(id, order, PublishPipelineResourceType.values()));
            }
        }
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, noOpRepository(), directExecutor());
        
        PublishPipelineContext ctx = buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
        
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        executor.execute(ctx, resultRef::set);
        
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result);
        
        List<PipelineNodeResult> pipeline = result.getPipeline();
        // The throwing node should be at index throwAtIndex
        assertTrue(pipeline.size() > input.throwAtIndex,
                "Pipeline should contain the throwing node");
        
        PipelineNodeResult throwingNode = pipeline.get(input.throwAtIndex);
        assertFalse(throwingNode.isPassed(),
                "Throwing node should have passed=false");
        assertNotNull(throwingNode.getMessage(),
                "Throwing node message should not be null");
        assertTrue(throwingNode.getMessage().contains(input.exceptionMessage),
                "Throwing node message should contain exception info: '" + input.exceptionMessage
                        + "' but was: '" + throwingNode.getMessage() + "'");
        
        // No nodes after the throwing node
        assertEquals(input.throwAtIndex + 1, pipeline.size(),
                "No nodes should appear after the throwing node");
    }
    
    @Provide
    Arbitrary<ExceptionInput> exceptionInputs() {
        Arbitrary<List<String>> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(String::toLowerCase)
                .list().ofMinSize(1).ofMaxSize(5)
                .filter(list -> list.stream().distinct().count() == list.size());
        Arbitrary<String> exceptionMsgs = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30);
        
        return Combinators.combine(nodeIds, exceptionMsgs).flatAs((ids, msg) -> {
            Arbitrary<Integer> throwIdx = Arbitraries.integers().between(0, ids.size() - 1);
            return throwIdx.map(idx -> new ExceptionInput(ids, idx, msg));
        });
    }
    
    // ======================================================================
    // Property 7 (Task 6.7): Nodes executed in order ascending by getPreferOrder()
    // ======================================================================
    
    /**
     * Property 7: Pipeline result nodes appear in order ascending by getPreferOrder().
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property
    void nodesExecutedInOrderAscending(@ForAll("orderTestInputs") OrderTestInput input) {
        PipelineConfig config = buildConfig(true, input.nodeIds);
        
        List<PublishPipelineService> services = new ArrayList<>();
        for (int i = 0; i < input.nodeIds.size(); i++) {
            services.add(passingService(input.nodeIds.get(i), input.orders.get(i),
                    PublishPipelineResourceType.values()));
        }
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, noOpRepository(), directExecutor());
        
        PublishPipelineContext ctx = buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
        
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        executor.execute(ctx, resultRef::set);
        
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result);
        
        List<PipelineNodeResult> pipeline = result.getPipeline();
        
        // Build a map from nodeId to order for verification
        java.util.Map<String, Integer> nodeOrderMap = new java.util.HashMap<>();
        for (int i = 0; i < input.nodeIds.size(); i++) {
            nodeOrderMap.put(input.nodeIds.get(i), input.orders.get(i));
        }
        
        // Verify nodes appear in ascending order
        for (int i = 1; i < pipeline.size(); i++) {
            int prevOrder = nodeOrderMap.getOrDefault(pipeline.get(i - 1).getNodeId(), 0);
            int currOrder = nodeOrderMap.getOrDefault(pipeline.get(i).getNodeId(), 0);
            assertTrue(prevOrder <= currOrder,
                    "Nodes should be in ascending order by preferOrder, but node '"
                            + pipeline.get(i - 1).getNodeId() + "' (order=" + prevOrder
                            + ") appeared before '" + pipeline.get(i).getNodeId()
                            + "' (order=" + currOrder + ")");
        }
    }
    
    @Provide
    Arbitrary<OrderTestInput> orderTestInputs() {
        Arbitrary<List<String>> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(String::toLowerCase)
                .list().ofMinSize(2).ofMaxSize(6)
                .filter(list -> list.stream().distinct().count() == list.size());
        
        return nodeIds.flatMap(ids -> {
            Arbitrary<List<Integer>> orders = Arbitraries.integers().between(0, 1000)
                    .list().ofSize(ids.size());
            return orders.map(ords -> new OrderTestInput(ids, ords));
        });
    }
    
    // ======================================================================
    // Property 13 (Task 6.8): Persistence failure doesn't block callback
    // ======================================================================
    
    /**
     * Property 13: When PipelineExecutionRepository throws exceptions on save/update,
     * callback.onComplete() is still called.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property
    void persistenceFailureDoesNotBlockCallback(
            @ForAll("persistenceFailureInputs") PersistenceFailureInput input) {
        PipelineConfig config = buildConfig(true, input.nodeIds);
        
        List<PublishPipelineService> services = new ArrayList<>();
        for (int i = 0; i < input.nodeIds.size(); i++) {
            String id = input.nodeIds.get(i);
            int order = i;
            if (input.hasFailingNode && i == input.nodeIds.size() - 1) {
                services.add(failingService(id, order, PublishPipelineResourceType.values()));
            } else {
                services.add(passingService(id, order, PublishPipelineResourceType.values()));
            }
        }
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        // Use throwing repository
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, throwingRepository(), directExecutor());
        
        PublishPipelineContext ctx = buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
        
        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        
        String executionId = executor.execute(ctx, result -> {
            callbackCount.incrementAndGet();
            resultRef.set(result);
        });
        
        assertNotNull(executionId, "executionId should still be returned despite persistence failures");
        assertEquals(1, callbackCount.get(),
                "Callback should be called exactly once even when repository throws");
        
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result, "Result should not be null");
        assertTrue(result.getStatus() == PipelineExecutionStatus.APPROVED
                        || result.getStatus() == PipelineExecutionStatus.REJECTED,
                "Status should be APPROVED or REJECTED");
    }
    
    @Provide
    Arbitrary<PersistenceFailureInput> persistenceFailureInputs() {
        Arbitrary<List<String>> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(String::toLowerCase)
                .list().ofMinSize(1).ofMaxSize(4)
                .filter(list -> list.stream().distinct().count() == list.size());
        Arbitrary<Boolean> hasFailingNode = Arbitraries.of(true, false);
        
        return Combinators.combine(nodeIds, hasFailingNode).as(PersistenceFailureInput::new);
    }
    
    // ======================================================================
    // Input data classes
    // ======================================================================
    
    static class ExecutionInput {
        
        final List<String> nodeIds;
        
        final PublishPipelineResourceType resourceType;
        
        final String resourceName;
        
        final String namespaceId;
        
        final String version;
        
        ExecutionInput(List<String> nodeIds, PublishPipelineResourceType resourceType,
                String resourceName, String namespaceId, String version) {
            this.nodeIds = nodeIds;
            this.resourceType = resourceType;
            this.resourceName = resourceName;
            this.namespaceId = namespaceId;
            this.version = version;
        }
        
        @Override
        public String toString() {
            return "ExecutionInput{nodeIds=" + nodeIds + ", resourceType=" + resourceType
                    + ", resourceName='" + resourceName + "', namespaceId='" + namespaceId
                    + "', version='" + version + "'}";
        }
    }
    
    static class DisabledOrEmptyInput {
        
        final boolean enabled;
        
        final List<String> nodeIds;
        
        DisabledOrEmptyInput(boolean enabled, List<String> nodeIds) {
            this.enabled = enabled;
            this.nodeIds = nodeIds;
        }
        
        @Override
        public String toString() {
            return "DisabledOrEmptyInput{enabled=" + enabled + ", nodeIds=" + nodeIds + "}";
        }
    }
    
    static class CallbackTestInput {
        
        final List<String> nodeIds;
        
        final PublishPipelineResourceType resourceType;
        
        final int failAtIndex;
        
        CallbackTestInput(List<String> nodeIds, PublishPipelineResourceType resourceType, int failAtIndex) {
            this.nodeIds = nodeIds;
            this.resourceType = resourceType;
            this.failAtIndex = failAtIndex;
        }
        
        @Override
        public String toString() {
            return "CallbackTestInput{nodeIds=" + nodeIds + ", resourceType=" + resourceType
                    + ", failAtIndex=" + failAtIndex + "}";
        }
    }
    
    static class FailStopInput {
        
        final List<String> nodeIds;
        
        final int failAtIndex;
        
        FailStopInput(List<String> nodeIds, int failAtIndex) {
            this.nodeIds = nodeIds;
            this.failAtIndex = failAtIndex;
        }
        
        @Override
        public String toString() {
            return "FailStopInput{nodeIds=" + nodeIds + ", failAtIndex=" + failAtIndex + "}";
        }
    }
    
    static class ExceptionInput {
        
        final List<String> nodeIds;
        
        final int throwAtIndex;
        
        final String exceptionMessage;
        
        ExceptionInput(List<String> nodeIds, int throwAtIndex, String exceptionMessage) {
            this.nodeIds = nodeIds;
            this.throwAtIndex = throwAtIndex;
            this.exceptionMessage = exceptionMessage;
        }
        
        @Override
        public String toString() {
            return "ExceptionInput{nodeIds=" + nodeIds + ", throwAtIndex=" + throwAtIndex
                    + ", exceptionMessage='" + exceptionMessage + "'}";
        }
    }
    
    static class OrderTestInput {
        
        final List<String> nodeIds;
        
        final List<Integer> orders;
        
        OrderTestInput(List<String> nodeIds, List<Integer> orders) {
            this.nodeIds = nodeIds;
            this.orders = orders;
        }
        
        @Override
        public String toString() {
            return "OrderTestInput{nodeIds=" + nodeIds + ", orders=" + orders + "}";
        }
    }
    
    static class PersistenceFailureInput {
        
        final List<String> nodeIds;
        
        final boolean hasFailingNode;
        
        PersistenceFailureInput(List<String> nodeIds, boolean hasFailingNode) {
            this.nodeIds = nodeIds;
            this.hasFailingNode = hasFailingNode;
        }
        
        @Override
        public String toString() {
            return "PersistenceFailureInput{nodeIds=" + nodeIds
                    + ", hasFailingNode=" + hasFailingNode + "}";
        }
    }
}
