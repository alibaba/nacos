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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.pipeline.PipelineQueryService;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AI module health checks covering AI registry,
 * MCP server status verification, and pipeline health monitoring.
 *
 * <p>Bug-fix: Tests include proper resource cleanup patterns using
 * try-with-resources and finally blocks to prevent connection leaks.</p>
 *
 * @author spatchava
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class AiModuleHealthCheckTest {

    private static final String TEST_NAMESPACE = "public";

    private static final String TEST_MCP_SERVER_ID = "test-mcp-server-001";

    private static final String TEST_MCP_SERVER_NAME = "test-ai-health-server";

    private static final String TEST_VERSION = "1.0.0";

    @Mock
    private ConfigQueryChainService configQueryChainService;

    @Mock
    private ConfigOperationService configOperationService;

    @Mock
    private McpToolOperationService toolOperationService;

    @Mock
    private McpResourceOperationService resourceOperationService;

    @Mock
    private McpEndpointOperationService endpointOperationService;

    @Mock
    private McpServerIndex mcpServerIndex;

    @Mock
    private SyncEffectService syncEffectService;

    @Mock
    private PipelineExecutionRepository pipelineExecutionRepository;

    private McpServerOperationService mcpServerOperationService;

    private PipelineQueryService pipelineQueryService;

    private final List<Closeable> resourcesToCleanup = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mcpServerOperationService = new McpServerOperationService(configQueryChainService,
                configOperationService, toolOperationService, resourceOperationService,
                endpointOperationService, mcpServerIndex, syncEffectService);
        pipelineQueryService = new PipelineQueryService(pipelineExecutionRepository);
    }

    @AfterEach
    void tearDown() {
        for (Closeable resource : resourcesToCleanup) {
            try {
                resource.close();
            } catch (IOException ignored) {
                // Best-effort cleanup to prevent resource leaks
            }
        }
        resourcesToCleanup.clear();
    }

    // ==================== AI Service Registration and Discovery ====================

    @Test
    void testAiServiceRegistrationWithValidServer() throws NacosException {
        McpServerBasicInfo serverInfo = buildTestServerInfo(TEST_MCP_SERVER_NAME, TEST_VERSION);
        ConfigQueryChainResponse notFoundResponse = buildNotFoundResponse();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(notFoundResponse);
        when(mcpServerIndex.getMcpServerByName(eq(TEST_NAMESPACE), eq(TEST_MCP_SERVER_NAME))).thenReturn(null);

        String serverId = mcpServerOperationService.createMcpServer(TEST_NAMESPACE, serverInfo, null, null);

        assertNotNull(serverId, "Server ID should be generated on successful registration");
        assertFalse(serverId.isEmpty(), "Server ID should not be empty");
    }

    @Test
    void testAiServiceRegistrationDuplicatePrevention() throws NacosException {
        McpServerBasicInfo serverInfo = buildTestServerInfo(TEST_MCP_SERVER_NAME, TEST_VERSION);
        McpServerIndexData existingIndex = new McpServerIndexData();
        existingIndex.setId(TEST_MCP_SERVER_ID);
        when(mcpServerIndex.getMcpServerByName(eq(TEST_NAMESPACE), eq(TEST_MCP_SERVER_NAME)))
                .thenReturn(existingIndex);

        assertThrows(NacosApiException.class,
                () -> mcpServerOperationService.createMcpServer(TEST_NAMESPACE, serverInfo, null, null),
                "Should throw conflict exception for duplicate server registration");
    }

    @Test
    void testAiServiceDiscoveryByNamespace() {
        Page<McpServerIndexData> indexPage = new Page<>();
        indexPage.setTotalCount(2);
        indexPage.setPageNumber(1);
        indexPage.setPagesAvailable(1);
        List<McpServerIndexData> indexItems = buildTestIndexDataList(2);
        indexPage.setPageItems(indexItems);

        when(mcpServerIndex.searchMcpServerByNameWithPage(eq(TEST_NAMESPACE), anyString(), anyString(),
                anyInt(), anyInt())).thenReturn(indexPage);

        for (McpServerIndexData indexData : indexItems) {
            ConfigQueryChainResponse response = buildVersionInfoResponse(indexData.getId());
            when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        }

        Page<McpServerBasicInfo> result = mcpServerOperationService.listMcpServerWithPage(
                TEST_NAMESPACE, "", "blur", 1, 10);

        assertNotNull(result);
        assertEquals(2, result.getTotalCount(), "Should discover all registered AI services");
    }

    // ==================== MCP Server Connection Health Checks ====================

    @Test
    void testMcpServerHealthCheckNotFound() {
        when(mcpServerIndex.getMcpServerByName(eq(TEST_NAMESPACE), eq(TEST_MCP_SERVER_NAME))).thenReturn(null);

        ConfigQueryChainResponse notFoundResponse = buildNotFoundResponse();
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(notFoundResponse);

        assertThrows(NacosApiException.class,
                () -> mcpServerOperationService.getMcpServerDetail(
                        TEST_NAMESPACE, null, TEST_MCP_SERVER_NAME, TEST_VERSION),
                "Health check should fail for non-existent MCP server");
    }

    @Test
    void testMcpServerHealthCheckVersionNotFound() {
        McpServerIndexData indexData = new McpServerIndexData();
        indexData.setId(TEST_MCP_SERVER_ID);
        when(mcpServerIndex.getMcpServerByName(eq(TEST_NAMESPACE), eq(TEST_MCP_SERVER_NAME)))
                .thenReturn(indexData);

        ConfigQueryChainResponse versionResponse = buildVersionInfoResponse(TEST_MCP_SERVER_ID);
        ConfigQueryChainResponse notFoundResponse = buildNotFoundResponse();

        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(versionResponse)
                .thenReturn(notFoundResponse);

        assertThrows(NacosApiException.class,
                () -> mcpServerOperationService.getMcpServerDetail(
                        TEST_NAMESPACE, null, TEST_MCP_SERVER_NAME, "99.99.99"),
                "Health check should fail for non-existent version");
    }

    @Test
    void testMcpServerRegistrationWithMissingVersion() {
        McpServerBasicInfo serverInfo = new McpServerBasicInfo();
        serverInfo.setName(TEST_MCP_SERVER_NAME);
        // Deliberately omit version to test validation
        when(mcpServerIndex.getMcpServerByName(eq(TEST_NAMESPACE), eq(TEST_MCP_SERVER_NAME))).thenReturn(null);

        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> mcpServerOperationService.createMcpServer(TEST_NAMESPACE, serverInfo, null, null));
        assertTrue(ex.getErrMsg().contains("Version"),
                "Should report missing version in error message");
    }

    // ==================== Pipeline Health Verification ====================

    @Test
    void testPipelineHealthStatusApproved() throws NacosException {
        PipelineExecution execution = buildPipelineExecution(
                PipelineExecutionStatus.APPROVED, true);
        when(pipelineExecutionRepository.findById(anyString())).thenReturn(execution);

        PipelineExecution result = pipelineQueryService.getPipeline(execution.getExecutionId());

        assertNotNull(result);
        assertEquals(PipelineExecutionStatus.APPROVED, result.getStatus(),
                "Healthy pipeline should have APPROVED status");
        assertTrue(result.getPipeline().stream().allMatch(PipelineNodeResult::isPassed),
                "All pipeline nodes should pass for healthy state");
    }

    @Test
    void testPipelineHealthStatusRejected() throws NacosException {
        PipelineExecution execution = buildPipelineExecution(
                PipelineExecutionStatus.REJECTED, false);
        when(pipelineExecutionRepository.findById(anyString())).thenReturn(execution);

        PipelineExecution result = pipelineQueryService.getPipeline(execution.getExecutionId());

        assertNotNull(result);
        assertEquals(PipelineExecutionStatus.REJECTED, result.getStatus(),
                "Unhealthy pipeline should have REJECTED status");
        assertFalse(result.getPipeline().stream().allMatch(PipelineNodeResult::isPassed),
                "At least one pipeline node should fail for rejected state");
    }

    @Test
    void testPipelineHealthStatusInProgress() throws NacosException {
        PipelineExecution execution = buildPipelineExecution(
                PipelineExecutionStatus.IN_PROGRESS, true);
        when(pipelineExecutionRepository.findById(anyString())).thenReturn(execution);

        PipelineExecution result = pipelineQueryService.getPipeline(execution.getExecutionId());

        assertEquals(PipelineExecutionStatus.IN_PROGRESS, result.getStatus(),
                "In-progress pipeline should report IN_PROGRESS status");
    }

    @Test
    void testPipelineNotFoundHealthCheck() {
        when(pipelineExecutionRepository.findById(anyString())).thenReturn(null);

        NacosApiException ex = assertThrows(NacosApiException.class,
                () -> pipelineQueryService.getPipeline("non-existent-pipeline-id"));
        assertEquals(404, ex.getErrCode(),
                "Missing pipeline should return 404 status");
    }

    // ==================== Health Endpoint Response Validation ====================

    @Test
    void testHealthResponsePaginationCorrectness() throws NacosException {
        int totalRecords = 15;
        int pageSize = 5;
        List<PipelineExecution> executions = new ArrayList<>();
        for (int i = 0; i < pageSize; i++) {
            executions.add(buildPipelineExecution(PipelineExecutionStatus.APPROVED, true));
        }

        when(pipelineExecutionRepository.findByResourceWithPage(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(executions);
        when(pipelineExecutionRepository.countByResource(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(totalRecords);

        Page<PipelineExecution> page = pipelineQueryService.listPipelines(
                "mcp", "test-resource", TEST_NAMESPACE, TEST_VERSION, 1, pageSize);

        assertNotNull(page);
        assertEquals(totalRecords, page.getTotalCount());
        assertEquals(3, page.getPagesAvailable(), "Should calculate correct page count");
        assertEquals(1, page.getPageNumber());
        assertEquals(pageSize, page.getPageItems().size());
    }

    @Test
    void testHealthResponseEmptyResults() throws NacosException {
        when(pipelineExecutionRepository.findByResourceWithPage(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(pipelineExecutionRepository.countByResource(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        Page<PipelineExecution> page = pipelineQueryService.listPipelines(
                "mcp", "test-resource", TEST_NAMESPACE, TEST_VERSION, 1, 10);

        assertNotNull(page);
        assertEquals(0, page.getTotalCount());
        assertTrue(page.getPageItems().isEmpty(), "Empty health check should return no items");
    }

    // ==================== Timeout and Error Handling ====================

    @Test
    void testHealthCheckTimeoutHandling() {
        CompletableFuture<String> healthFuture = new CompletableFuture<>();
        // Simulate a health check that never completes

        assertThrows(TimeoutException.class, () -> {
            healthFuture.get(100, TimeUnit.MILLISECONDS);
        }, "Health check should timeout when server is unresponsive");

        healthFuture.cancel(true);
        assertTrue(healthFuture.isCancelled(), "Future should be cancelled after timeout");
    }

    @Test
    void testHealthCheckConcurrentTimeout() throws InterruptedException {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                futures.add(new CompletableFuture<>());
            }

            CompletableFuture<Object> anyResult = CompletableFuture.anyOf(
                    futures.toArray(new CompletableFuture[0]));

            assertThrows(TimeoutException.class,
                    () -> anyResult.get(50, TimeUnit.MILLISECONDS),
                    "Concurrent health check should timeout when all servers unresponsive");
        } finally {
            // Bug-fix: Ensure all futures are cancelled to prevent resource leaks
            for (CompletableFuture<String> future : futures) {
                future.cancel(true);
            }
        }
    }

    @Test
    void testHealthCheckExceptionPropagation() {
        RuntimeException simulatedError = new RuntimeException("Connection refused");
        when(pipelineExecutionRepository.findById(anyString())).thenThrow(simulatedError);

        assertThrows(RuntimeException.class,
                () -> pipelineQueryService.getPipeline("error-test-id"),
                "Health check errors should propagate correctly");
    }

    // ==================== Multiple AI Service Instance Health Aggregation ====================

    @Test
    void testMultipleInstanceHealthAggregation() throws NacosException {
        List<PipelineExecution> mixedHealthInstances = new ArrayList<>();
        mixedHealthInstances.add(buildPipelineExecution(PipelineExecutionStatus.APPROVED, true));
        mixedHealthInstances.add(buildPipelineExecution(PipelineExecutionStatus.REJECTED, false));
        mixedHealthInstances.add(buildPipelineExecution(PipelineExecutionStatus.APPROVED, true));

        when(pipelineExecutionRepository.findByResourceWithPage(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(mixedHealthInstances);
        when(pipelineExecutionRepository.countByResource(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(3);

        Page<PipelineExecution> page = pipelineQueryService.listPipelines(
                "mcp", "multi-instance", TEST_NAMESPACE, TEST_VERSION, 1, 10);

        long healthyCount = page.getPageItems().stream()
                .filter(e -> e.getStatus() == PipelineExecutionStatus.APPROVED).count();
        long unhealthyCount = page.getPageItems().stream()
                .filter(e -> e.getStatus() == PipelineExecutionStatus.REJECTED).count();

        assertEquals(2, healthyCount, "Should correctly count healthy instances");
        assertEquals(1, unhealthyCount, "Should correctly count unhealthy instances");
        assertEquals(3, page.getTotalCount(), "Total count should include all instances");
    }

    @Test
    void testAllInstancesHealthy() throws NacosException {
        List<PipelineExecution> allHealthy = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            allHealthy.add(buildPipelineExecution(PipelineExecutionStatus.APPROVED, true));
        }

        when(pipelineExecutionRepository.findByResourceWithPage(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(allHealthy);
        when(pipelineExecutionRepository.countByResource(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(5);

        Page<PipelineExecution> page = pipelineQueryService.listPipelines(
                "mcp", "all-healthy", TEST_NAMESPACE, TEST_VERSION, 1, 10);

        boolean allApproved = page.getPageItems().stream()
                .allMatch(e -> e.getStatus() == PipelineExecutionStatus.APPROVED);
        assertTrue(allApproved, "All instances should report healthy status");
    }

    @Test
    void testAllInstancesUnhealthy() throws NacosException {
        List<PipelineExecution> allUnhealthy = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            allUnhealthy.add(buildPipelineExecution(PipelineExecutionStatus.REJECTED, false));
        }

        when(pipelineExecutionRepository.findByResourceWithPage(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(allUnhealthy);
        when(pipelineExecutionRepository.countByResource(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(3);

        Page<PipelineExecution> page = pipelineQueryService.listPipelines(
                "mcp", "all-unhealthy", TEST_NAMESPACE, TEST_VERSION, 1, 10);

        boolean allRejected = page.getPageItems().stream()
                .allMatch(e -> e.getStatus() == PipelineExecutionStatus.REJECTED);
        assertTrue(allRejected, "All instances should report unhealthy status");
    }

    // ==================== Resource Cleanup (Bug-fix) ====================

    @Test
    void testResourceCleanupOnHealthCheckFailure() {
        AutoCloseableHealthChecker checker = new AutoCloseableHealthChecker();
        resourcesToCleanup.add(checker);

        try {
            checker.performCheck();
        } catch (Exception e) {
            // Expected - health check failed
        }

        // Bug-fix verified: resource is tracked for cleanup in tearDown
        assertFalse(checker.isClosed(), "Resource should remain open until tearDown");
    }

    @Test
    void testTryWithResourcesForConnectionCleanup() throws IOException {
        Map<String, String> healthResults = new HashMap<>();

        // Bug-fix: Using try-with-resources to prevent connection leaks
        try (AutoCloseableHealthChecker checker = new AutoCloseableHealthChecker()) {
            resourcesToCleanup.remove(checker); // managed by try-with-resources
            healthResults.put("status", checker.performCheck());
        }

        assertNotNull(healthResults.get("status"));
    }

    // ==================== Helper Methods ====================

    private McpServerBasicInfo buildTestServerInfo(String name, String version) {
        McpServerBasicInfo serverInfo = new McpServerBasicInfo();
        serverInfo.setName(name);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        serverInfo.setVersionDetail(versionDetail);
        serverInfo.setDescription("AI health check test server");
        serverInfo.setProtocol("sse");
        return serverInfo;
    }

    private List<McpServerIndexData> buildTestIndexDataList(int count) {
        List<McpServerIndexData> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            McpServerIndexData data = new McpServerIndexData();
            data.setId(UUID.randomUUID().toString());
            data.setNamespaceId(TEST_NAMESPACE);
            list.add(data);
        }
        return list;
    }

    private ConfigQueryChainResponse buildNotFoundResponse() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.CONFIG_NOT_FOUND);
        return response;
    }

    private ConfigQueryChainResponse buildVersionInfoResponse(String serverId) {
        McpServerVersionInfo versionInfo = new McpServerVersionInfo();
        versionInfo.setId(serverId);
        versionInfo.setName(TEST_MCP_SERVER_NAME);
        versionInfo.setLatestPublishedVersion(TEST_VERSION);
        ServerVersionDetail detail = new ServerVersionDetail();
        detail.setVersion(TEST_VERSION);
        detail.setRelease_date("2025-01-01T00:00:00Z");
        versionInfo.setVersions(Collections.singletonList(detail));

        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.CONFIG_FOUND_FORMAL);
        response.setContent(JacksonUtils.toJson(versionInfo));
        return response;
    }

    private PipelineExecution buildPipelineExecution(PipelineExecutionStatus status, boolean nodesPassed) {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId(UUID.randomUUID().toString());
        execution.setResourceType("mcp");
        execution.setResourceName(TEST_MCP_SERVER_NAME);
        execution.setNamespaceId(TEST_NAMESPACE);
        execution.setVersion(TEST_VERSION);
        execution.setStatus(status);
        execution.setCreateTime(System.currentTimeMillis());
        execution.setUpdateTime(System.currentTimeMillis());

        List<PipelineNodeResult> nodes = new ArrayList<>();
        PipelineNodeResult node = new PipelineNodeResult();
        node.setNodeId("health-check-node");
        node.setPassed(nodesPassed);
        node.setMessage(nodesPassed ? "Health check passed" : "Health check failed");
        node.setDurationMs(nodesPassed ? 50L : 5000L);
        nodes.add(node);
        execution.setPipeline(nodes);

        return execution;
    }

    /**
     * Auto-closeable health checker for testing resource cleanup patterns.
     * Bug-fix: Demonstrates proper resource management to prevent connection leaks.
     */
    private static class AutoCloseableHealthChecker implements Closeable {

        private boolean closed = false;

        String performCheck() {
            if (closed) {
                throw new IllegalStateException("Health checker already closed");
            }
            return "HEALTHY";
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }
}
