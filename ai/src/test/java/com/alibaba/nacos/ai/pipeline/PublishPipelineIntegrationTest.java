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
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepositoryImpl;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the publish pipeline end-to-end flow.
 *
 * <p>Wires together PublishPipelineExecutor, PublishPipelineManager,
 * PipelineExecutionRepositoryImpl (backed by H2), and mock pipeline services
 * to verify the full pipeline lifecycle without Spring Boot.</p>
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineIntegrationTest {
    
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS pipeline_execution ("
        + "execution_id  VARCHAR(64)  PRIMARY KEY, "
        + "resource_type VARCHAR(32)  NOT NULL, "
        + "resource_name VARCHAR(256) NOT NULL, "
        + "namespace_id  VARCHAR(128), "
        + "version       VARCHAR(64), "
        + "status        VARCHAR(32)  NOT NULL, "
        + "pipeline      TEXT         NOT NULL, "
        + "create_time   BIGINT       NOT NULL, "
        + "update_time   BIGINT       NOT NULL)";
    
    private JdbcTemplate jdbcTemplate;
    
    private PipelineExecutionRepositoryImpl repository;
    
    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource
            .setURL("jdbc:h2:mem:integration_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
    }
    
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
    
    // ---- Helper: create a passing PublishPipelineService ----
    
    private static PublishPipelineService passingService(String id, int order) {
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
                return PublishPipelineResourceType.values();
            }
        };
    }
    
    // ---- Helper: create a failing PublishPipelineService ----
    
    private static PublishPipelineService failingService(String id, int order) {
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
                return PublishPipelineResourceType.values();
            }
        };
    }
    
    // ---- Helper: create a builder from a service ----
    
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
    
    // ---- Helper: build PipelineConfig ----
    
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
    
    // ---- Helper: build PipelineConfigProvider ----
    
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
    
    // ---- Helper: build PublishPipelineManager with given services ----
    
    private static PublishPipelineManager buildManager(List<PublishPipelineService> services,
        PipelineConfig config) {
        PublishPipelineManager manager = new PublishPipelineManager();
        List<PublishPipelineServiceBuilder> builders = new ArrayList<>();
        for (PublishPipelineService service : services) {
            builders.add(builderFor(service));
        }
        manager.initWithBuilders(builders, config);
        return manager;
    }
    
    // ---- Helper: build PublishPipelineContext ----
    
    private static PublishPipelineContext buildContext() {
        PublishPipelineContext ctx = new PublishPipelineContext();
        ctx.setResourceType(PublishPipelineResourceType.SKILL);
        ctx.setResourceName("test-skill");
        ctx.setNamespaceId("public");
        ctx.setVersion("v1");
        return ctx;
    }
    
    // ======================================================================
    // Test 1: End-to-end Skill publish triggers pipeline, execution record
    //         persisted, callback called with APPROVED status
    // ======================================================================
    
    /**
     * End-to-end test: enabled pipeline with two passing nodes.
     * Verifies executionId returned, callback called with APPROVED, DB record correct.
     *
     * <p><b>Validates: Requirements 1.1, 1.4</b></p>
     */
    @Test
    void endToEndAllNodesPassed() {
        List<String> nodeIds = List.of("ai-review", "security-scan");
        PipelineConfig config = buildConfig(true, nodeIds);
        
        List<PublishPipelineService> services = List.of(
            passingService("ai-review", 1),
            passingService("security-scan", 2));
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
            manager, configProvider, repository, directExecutor());
        
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        AtomicInteger callbackCount = new AtomicInteger(0);
        PipelineCallback callback = result -> {
            callbackCount.incrementAndGet();
            resultRef.set(result);
        };
        
        String executionId = executor.execute(buildContext(), callback);
        
        // Verify: execute returns non-null executionId
        assertNotNull(executionId, "executionId should be non-null");
        
        // Verify: callback called with APPROVED status
        assertEquals(1, callbackCount.get(), "Callback should be called exactly once");
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(PipelineExecutionStatus.APPROVED, result.getStatus());
        
        // Verify: findById returns the execution record with correct status and pipeline nodes
        PipelineExecution dbRecord = repository.findById(executionId);
        assertNotNull(dbRecord, "Execution record should be persisted in DB");
        assertEquals(PipelineExecutionStatus.APPROVED, dbRecord.getStatus());
        assertEquals("SKILL", dbRecord.getResourceType());
        assertEquals("test-skill", dbRecord.getResourceName());
        assertEquals("public", dbRecord.getNamespaceId());
        assertEquals("v1", dbRecord.getVersion());
        
        // Verify: pipeline has 2 nodes, both passed
        List<PipelineNodeResult> pipeline = dbRecord.getPipeline();
        assertEquals(2, pipeline.size(), "Pipeline should have 2 nodes");
        assertEquals("ai-review", pipeline.get(0).getNodeId());
        assertTrue(pipeline.get(0).isPassed());
        assertEquals("security-scan", pipeline.get(1).getNodeId());
        assertTrue(pipeline.get(1).isPassed());
    }
    
    // ======================================================================
    // Test 2: Pipeline not enabled → direct pass-through
    // ======================================================================
    
    /**
     * When pipeline is disabled, execute returns null, no callback, no DB records.
     *
     * <p><b>Validates: Requirements 1.2</b></p>
     */
    @Test
    void pipelineNotEnabledDirectPassThrough() {
        PipelineConfig config = buildConfig(false, List.of("ai-review", "security-scan"));
        
        List<PublishPipelineService> services = List.of(
            passingService("ai-review", 1),
            passingService("security-scan", 2));
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
            manager, configProvider, repository, directExecutor());
        
        AtomicInteger callbackCount = new AtomicInteger(0);
        PipelineCallback callback = result -> callbackCount.incrementAndGet();
        
        String executionId = executor.execute(buildContext(), callback);
        
        // Verify: execute returns null
        assertNull(executionId, "executionId should be null when pipeline is disabled");
        
        // Verify: callback NOT called
        assertEquals(0, callbackCount.get(), "Callback should not be called");
        
        // Verify: no records in database
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pipeline_execution", Integer.class);
        assertEquals(0, count, "No records should exist in database");
    }
    
    // ======================================================================
    // Test 3: One node rejects → pipeline stops, REJECTED status
    // ======================================================================
    
    /**
     * When the second node rejects, pipeline stops with REJECTED status.
     * First node passed, second node failed.
     *
     * <p><b>Validates: Requirements 1.1, 1.4</b></p>
     */
    @Test
    void oneNodeRejectsPipelineStopsRejected() {
        List<String> nodeIds = List.of("ai-review", "security-scan");
        PipelineConfig config = buildConfig(true, nodeIds);
        
        List<PublishPipelineService> services = List.of(
            passingService("ai-review", 1),
            failingService("security-scan", 2));
        
        PublishPipelineManager manager = buildManager(services, config);
        PipelineConfigProvider configProvider = fixedConfigProvider(config);
        
        PublishPipelineExecutor executor = new PublishPipelineExecutor(
            manager, configProvider, repository, directExecutor());
        
        AtomicReference<PipelineExecutionResult> resultRef = new AtomicReference<>();
        AtomicInteger callbackCount = new AtomicInteger(0);
        PipelineCallback callback = result -> {
            callbackCount.incrementAndGet();
            resultRef.set(result);
        };
        
        String executionId = executor.execute(buildContext(), callback);
        
        // Verify: execute returns non-null executionId
        assertNotNull(executionId);
        
        // Verify: callback called with REJECTED status
        assertEquals(1, callbackCount.get(), "Callback should be called exactly once");
        PipelineExecutionResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(PipelineExecutionStatus.REJECTED, result.getStatus());
        
        // Verify: pipeline has 2 nodes, first passed, second failed
        List<PipelineNodeResult> pipeline = result.getPipeline();
        assertEquals(2, pipeline.size(), "Pipeline should have 2 nodes");
        assertTrue(pipeline.get(0).isPassed(), "First node (ai-review) should have passed");
        assertFalse(pipeline.get(1).isPassed(), "Second node (security-scan) should have failed");
        
        // Verify: execution record in DB has REJECTED status
        PipelineExecution dbRecord = repository.findById(executionId);
        assertNotNull(dbRecord, "Execution record should be persisted in DB");
        assertEquals(PipelineExecutionStatus.REJECTED, dbRecord.getStatus());
        assertEquals(2, dbRecord.getPipeline().size());
        assertTrue(dbRecord.getPipeline().get(0).isPassed());
        assertFalse(dbRecord.getPipeline().get(1).isPassed());
    }
}
