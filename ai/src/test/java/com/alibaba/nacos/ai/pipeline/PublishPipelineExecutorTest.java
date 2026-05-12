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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
 * Tests for PublishPipelineExecutor.
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.4, 6.4, 7.1, 7.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineExecutorTest {
    
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
            public List<PipelineExecution> findByResourceWithPage(String resourceType,
                String resourceName,
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
        public List<PipelineExecution> findByResourceWithPage(String resourceType,
            String resourceName,
            String namespaceId, String version, int offset, int limit) {
            return Collections.emptyList();
        }
        
        @Override
        public int countByResource(String resourceType, String resourceName,
            String namespaceId, String version) {
            return 0;
        }
    }
    
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
            public List<PipelineExecution> findByResourceWithPage(String resourceType,
                String resourceName,
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
    
    private static PublishPipelineContext buildContext(PublishPipelineResourceType resourceType,
        String resourceName, String namespaceId, String version) {
        PublishPipelineContext ctx = new PublishPipelineContext();
        ctx.setResourceType(resourceType);
        ctx.setResourceName(resourceName);
        ctx.setNamespaceId(namespaceId);
        ctx.setVersion(version);
        return ctx;
    }
    
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
    
    private static PublishPipelineManager buildManager(List<PublishPipelineService> services,
        PipelineConfig config) {
        PublishPipelineManager manager = new PublishPipelineManager();
        List<PublishPipelineServiceBuilder> builders = services.stream()
            .map(PublishPipelineExecutorTest::builderFor)
            .collect(Collectors.toList());
        manager.initWithBuilders(builders, config);
        return manager;
    }
    
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
    
    private static List<ExecutionInput> sampleExecutionInputs() {
        List<ExecutionInput> list = new ArrayList<>();
        list.add(
            new ExecutionInput(Arrays.asList("nodea", "nodeb"), PublishPipelineResourceType.SKILL,
                "res", "ns", "v1"));
        list.add(new ExecutionInput(Collections.singletonList("single"),
            PublishPipelineResourceType.AGENTSPEC, "name", "public", "v2"));
        return list;
    }
    
    /**
     * Enabled config → execute returns executionId.
     *
     * <p><b>Validates: Requirements 1.1</b></p>
     */
    @Test
    void enabledConfigReturnsExecutionId() {
        for (ExecutionInput input : sampleExecutionInputs()) {
            PipelineConfig config = buildConfig(true, input.nodeIds);
            List<PublishPipelineService> services = new ArrayList<>();
            for (int i = 0; i < input.nodeIds.size(); i++) {
                services.add(
                    passingService(input.nodeIds.get(i), i, PublishPipelineResourceType.values()));
            }
            
            PublishPipelineManager manager = buildManager(services, config);
            PipelineConfigProvider configProvider = fixedConfigProvider(config);
            
            PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, noOpRepository(), directExecutor());
            
            PublishPipelineContext ctx = buildContext(input.resourceType, input.resourceName,
                input.namespaceId, input.version);
            
            AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
            String executionId = executor.execute(ctx, resultRef::set);
            
            assertNotNull(executionId,
                "executionId should be non-null when pipeline is enabled with matching nodes");
        }
    }
    
    private static List<DisabledOrEmptyInput> sampleDisabledOrEmptyInputs() {
        return Arrays.asList(
            new DisabledOrEmptyInput(false, Arrays.asList("a", "b")),
            new DisabledOrEmptyInput(false, Collections.emptyList()),
            new DisabledOrEmptyInput(true, Collections.emptyList()));
    }
    
    /**
     * Disabled or no matching nodes → no side effects.
     *
     * <p><b>Validates: Requirements 1.2, 1.3</b></p>
     */
    @Test
    void disabledOrNoMatchingNodesHasNoSideEffects() {
        for (DisabledOrEmptyInput input : sampleDisabledOrEmptyInputs()) {
            PipelineConfig config = buildConfig(input.enabled, input.nodeIds);
            
            List<PublishPipelineService> services = new ArrayList<>();
            if (!input.nodeIds.isEmpty()) {
                services.add(passingService("non-matching-service", 0,
                    PublishPipelineResourceType.values()));
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
            
            assertNull(executionId,
                "executionId should be null when pipeline is disabled or no matching nodes");
            assertEquals(0, trackingRepo.saveCount.get(), "No records should be saved");
            assertEquals(0, callbackCount.get(), "Callback should not be called");
        }
    }
    
    private static List<CallbackTestInput> sampleCallbackTestInputs() {
        List<CallbackTestInput> list = new ArrayList<>();
        List<String> ids = Arrays.asList("x", "y", "z");
        for (PublishPipelineResourceType rt : new PublishPipelineResourceType[] {
            PublishPipelineResourceType.SKILL, PublishPipelineResourceType.PROMPT}) {
            for (int failAt : new int[] {-1, 0, 1}) {
                list.add(new CallbackTestInput(ids, rt, failAt));
            }
        }
        return list;
    }
    
    /**
     * Callback called exactly once.
     *
     * <p><b>Validates: Requirements 1.4, 1.5</b></p>
     */
    @Test
    void callbackCalledExactlyOnce() {
        for (CallbackTestInput input : sampleCallbackTestInputs()) {
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
    }
    
    private static List<FailStopInput> sampleFailStopInputs() {
        List<FailStopInput> list = new ArrayList<>();
        List<String> nodes = Arrays.asList("n1", "n2", "n3");
        for (int failAt = 0; failAt < nodes.size(); failAt++) {
            list.add(new FailStopInput(nodes, failAt));
        }
        return list;
    }
    
    /**
     * Fail-stop — no nodes after rejected node.
     *
     * <p><b>Validates: Requirements 2.2, 6.4</b></p>
     */
    @Test
    void failStopNoNodesAfterRejection() {
        for (FailStopInput input : sampleFailStopInputs()) {
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
            
            PublishPipelineContext ctx =
                buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
            
            AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
            executor.execute(ctx, resultRef::set);
            
            PipelineExecutionResult result = resultRef.get();
            assertNotNull(result);
            assertEquals(PipelineExecutionStatus.REJECTED, result.getStatus(),
                "Status should be REJECTED when a node fails");
            
            List<PipelineNodeResult> pipeline = result.getPipeline();
            assertEquals(input.failAtIndex + 1, pipeline.size(),
                "Pipeline should contain exactly failAtIndex+1 nodes, no more");
            
            PipelineNodeResult lastNode = pipeline.get(pipeline.size() - 1);
            assertFalse(lastNode.isPassed(), "The last node in pipeline should be the failed node");
            
            for (int i = 0; i < pipeline.size() - 1; i++) {
                assertTrue(pipeline.get(i).isPassed(),
                    "Node at index " + i + " should have passed");
            }
        }
    }
    
    private static List<ExceptionInput> sampleExceptionInputs() {
        List<ExceptionInput> list = new ArrayList<>();
        List<String> ids = Arrays.asList("a", "b");
        for (String msg : new String[] {"boom", "test-exception"}) {
            for (int throwAt = 0; throwAt < ids.size(); throwAt++) {
                list.add(new ExceptionInput(ids, throwAt, msg));
            }
        }
        return list;
    }
    
    /**
     * Exception treated as failure.
     *
     * <p><b>Validates: Requirements 2.4, 7.1</b></p>
     */
    @Test
    void exceptionTreatedAsFailure() {
        for (ExceptionInput input : sampleExceptionInputs()) {
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
            
            PublishPipelineContext ctx =
                buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
            
            AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
            executor.execute(ctx, resultRef::set);
            
            PipelineExecutionResult result = resultRef.get();
            assertNotNull(result);
            
            List<PipelineNodeResult> pipeline = result.getPipeline();
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
            
            assertEquals(input.throwAtIndex + 1, pipeline.size(),
                "No nodes should appear after the throwing node");
        }
    }
    
    private static List<OrderTestInput> sampleOrderTestInputs() {
        return Arrays.asList(
            new OrderTestInput(Arrays.asList("p", "q", "r"), Arrays.asList(2, 0, 1)),
            new OrderTestInput(Arrays.asList("a", "b"), Arrays.asList(10, 20)));
    }
    
    /**
     * Nodes executed in order ascending by getPreferOrder().
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Test
    void nodesExecutedInOrderAscending() {
        for (OrderTestInput input : sampleOrderTestInputs()) {
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
            
            PublishPipelineContext ctx =
                buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
            
            AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
            executor.execute(ctx, resultRef::set);
            
            PipelineExecutionResult result = resultRef.get();
            assertNotNull(result);
            
            List<PipelineNodeResult> pipeline = result.getPipeline();
            
            java.util.Map<String, Integer> nodeOrderMap = new java.util.HashMap<>();
            for (int i = 0; i < input.nodeIds.size(); i++) {
                nodeOrderMap.put(input.nodeIds.get(i), input.orders.get(i));
            }
            
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
    }
    
    private static List<PersistenceFailureInput> samplePersistenceFailureInputs() {
        return Arrays.asList(
            new PersistenceFailureInput(Arrays.asList("n1", "n2"), false),
            new PersistenceFailureInput(Collections.singletonList("only"), true),
            new PersistenceFailureInput(Arrays.asList("a", "b", "c"), true));
    }
    
    /**
     * Persistence failure doesn't block callback.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Test
    void persistenceFailureDoesNotBlockCallback() {
        for (PersistenceFailureInput input : samplePersistenceFailureInputs()) {
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
            
            PublishPipelineExecutor executor = new PublishPipelineExecutor(
                manager, configProvider, throwingRepository(), directExecutor());
            
            PublishPipelineContext ctx =
                buildContext(PublishPipelineResourceType.SKILL, "res", "ns", "v1");
            
            AtomicInteger callbackCount = new AtomicInteger(0);
            AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
            
            String executionId = executor.execute(ctx, result -> {
                callbackCount.incrementAndGet();
                resultRef.set(result);
            });
            
            assertNotNull(executionId,
                "executionId should still be returned despite persistence failures");
            assertEquals(1, callbackCount.get(),
                "Callback should be called exactly once even when repository throws");
            
            PipelineExecutionResult result = resultRef.get();
            assertNotNull(result, "Result should not be null");
            assertTrue(result.getStatus() == PipelineExecutionStatus.APPROVED
                || result.getStatus() == PipelineExecutionStatus.REJECTED,
                "Status should be APPROVED or REJECTED");
        }
    }
    
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
    }
    
    static class DisabledOrEmptyInput {
        
        final boolean enabled;
        
        final List<String> nodeIds;
        
        DisabledOrEmptyInput(boolean enabled, List<String> nodeIds) {
            this.enabled = enabled;
            this.nodeIds = nodeIds;
        }
    }
    
    static class CallbackTestInput {
        
        final List<String> nodeIds;
        
        final PublishPipelineResourceType resourceType;
        
        final int failAtIndex;
        
        CallbackTestInput(List<String> nodeIds, PublishPipelineResourceType resourceType,
            int failAtIndex) {
            this.nodeIds = nodeIds;
            this.resourceType = resourceType;
            this.failAtIndex = failAtIndex;
        }
    }
    
    static class FailStopInput {
        
        final List<String> nodeIds;
        
        final int failAtIndex;
        
        FailStopInput(List<String> nodeIds, int failAtIndex) {
            this.nodeIds = nodeIds;
            this.failAtIndex = failAtIndex;
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
    }
    
    static class OrderTestInput {
        
        final List<String> nodeIds;
        
        final List<Integer> orders;
        
        OrderTestInput(List<String> nodeIds, List<Integer> orders) {
            this.nodeIds = nodeIds;
            this.orders = orders;
        }
    }
    
    static class PersistenceFailureInput {
        
        final List<String> nodeIds;
        
        final boolean hasFailingNode;
        
        PersistenceFailureInput(List<String> nodeIds, boolean hasFailingNode) {
            this.nodeIds = nodeIds;
            this.hasFailingNode = hasFailingNode;
        }
    }
}
