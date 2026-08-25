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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.mcp.McpHistoricalResourceReconciler;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpLifecycleReconciliationTaskTest {
    
    private static final String PUBLIC_NAMESPACE = "public";
    
    private static final String TEAM_NAMESPACE = "team-a";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private McpServingManifestStorage manifestStorage;
    
    @Mock
    private McpHistoricalResourceReconciler reconciler;
    
    @Mock
    private AiResourcePersistService resourcePersistService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    private McpLifecycleReconciliationTask task;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        task = new McpLifecycleReconciliationTask(namespaceOperationService, manifestStorage,
            reconciler, resourcePersistService, configQueryChainService,
            configOperationService);
        lenient().when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFoundResponse());
        lenient().when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(new Namespace(PUBLIC_NAMESPACE, "public")));
        lenient().when(manifestStorage.list(any(), anyInt(), eq(100))).thenReturn(emptyPage());
        lenient().when(resourcePersistService.list(any(QueryCondition.class), anyInt(), eq(100)))
            .thenReturn(emptyPage());
    }
    
    @AfterEach
    void tearDown() {
        if (task != null) {
            task.destroy();
        }
        System.clearProperty(McpLifecycleReconciliationTask.RECONCILIATION_ENABLED_KEY);
        System.clearProperty(
            McpLifecycleReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void testExecutePersistsChangedSyncingProgressAndReleasesLease() throws Exception {
        McpServerVersionInfo manifest = manifest("demo", MCP_ID);
        when(manifestStorage.list(PUBLIC_NAMESPACE, 1, 100))
            .thenReturn(page(Collections.singletonList(manifest), 1, 1));
        when(reconciler.reconcile(PUBLIC_NAMESPACE, manifest)).thenReturn(2);
        
        task.executeReconciliation();
        
        verify(reconciler).reconcile(PUBLIC_NAMESPACE, manifest);
        List<ConfigForm> forms = capturedForms();
        assertEquals(3, forms.size());
        assertEquals(McpLifecycleReconciliationTask.RECONCILIATION_LEASE_DATA_ID,
            forms.get(0).getDataId());
        assertEquals(McpLifecycleReconciliationTask.RECONCILIATION_PROGRESS_DATA_ID,
            forms.get(1).getDataId());
        assertEquals(McpLifecycleReconciliationTask.RECONCILIATION_LEASE_DATA_ID,
            forms.get(2).getDataId());
        assertTrue(forms.get(2).getContent().endsWith("|0"));
        Map<?, ?> progress = JacksonUtils.toObj(forms.get(1).getContent(), Map.class);
        assertEquals("SYNCING", progress.get("state"));
        assertEquals(1, progress.get("manifests"));
        assertEquals(2, progress.get("changed"));
        assertEquals(false, progress.get("zeroDifference"));
        assertEquals(true, progress.get("searchBackfillPending"));
        assertEquals(false, progress.get("managedCutoverReady"));
        assertFalse(forms.stream()
            .anyMatch(form -> "nacos.ai.mcp.resource.migration.v1".equals(form.getDataId())));
        ArgumentCaptor<ConfigRequestInfo> requestCaptor =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService, org.mockito.Mockito.times(3)).publishConfig(any(),
            requestCaptor.capture(), isNull());
        assertTrue(requestCaptor.getAllValues().get(1).getUpdateForExist());
    }
    
    @Test
    void testExecuteRecordsZeroDifferenceForCompleteEmptyScan() throws Exception {
        task.executeReconciliation();
        
        Map<?, ?> progress = capturedProgress();
        assertEquals(1, progress.get("namespaces"));
        assertEquals(0, progress.get("failed"));
        assertEquals(true, progress.get("completeNamespaceScan"));
        assertEquals(true, progress.get("zeroDifference"));
    }
    
    @Test
    void testExecutePagesSortedNamespacesAndDetectsDuplicateIdentity() throws Exception {
        when(namespaceOperationService.getNamespaceList()).thenReturn(Arrays.asList(
            new Namespace(TEAM_NAMESPACE, "team"), null,
            new Namespace(PUBLIC_NAMESPACE, "public"),
            new Namespace(PUBLIC_NAMESPACE, "duplicate")));
        McpServerVersionInfo first = manifest("demo", MCP_ID);
        McpServerVersionInfo duplicate = manifest("demo",
            "11111111-1111-1111-1111-111111111111");
        when(manifestStorage.list(PUBLIC_NAMESPACE, 1, 100))
            .thenReturn(page(Collections.singletonList(first), 101, 0));
        when(manifestStorage.list(PUBLIC_NAMESPACE, 2, 100))
            .thenReturn(page(Collections.singletonList(duplicate), 101, 0));
        when(manifestStorage.list(TEAM_NAMESPACE, 1, 100)).thenReturn(emptyPage());
        
        task.executeReconciliation();
        
        InOrder order = inOrder(manifestStorage);
        order.verify(manifestStorage).list(PUBLIC_NAMESPACE, 1, 100);
        order.verify(manifestStorage).list(PUBLIC_NAMESPACE, 2, 100);
        order.verify(manifestStorage).list(TEAM_NAMESPACE, 1, 100);
        verify(reconciler).reconcile(PUBLIC_NAMESPACE, first);
        verify(reconciler, never()).reconcile(PUBLIC_NAMESPACE, duplicate);
        Map<?, ?> progress = capturedProgress();
        assertEquals(2, progress.get("namespaces"));
        assertEquals(2, progress.get("manifests"));
        assertEquals(1, progress.get("failed"));
        assertEquals(false, progress.get("zeroDifference"));
    }
    
    @Test
    void testExecuteContinuesAfterResourceFailureAndPersistsLastError() throws Exception {
        McpServerVersionInfo first = manifest("first", MCP_ID);
        McpServerVersionInfo second = manifest("second",
            "11111111-1111-1111-1111-111111111111");
        when(manifestStorage.list(PUBLIC_NAMESPACE, 1, 100))
            .thenReturn(page(Arrays.asList(first, second), 2, 1));
        when(reconciler.reconcile(PUBLIC_NAMESPACE, first))
            .thenThrow(new IllegalStateException("conflict"));
        when(reconciler.reconcile(PUBLIC_NAMESPACE, second)).thenReturn(1);
        
        task.executeReconciliation();
        
        verify(reconciler).reconcile(PUBLIC_NAMESPACE, second);
        Map<?, ?> progress = capturedProgress();
        assertEquals(1, progress.get("failed"));
        assertEquals(1, progress.get("changed"));
        assertTrue(String.valueOf(progress.get("lastError")).contains("first"));
    }
    
    @Test
    void testExecuteDetectsOrphanWithoutDeletingIt() throws Exception {
        AiResource orphan = legacyResource("orphan");
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(100)))
            .thenReturn(page(Collections.singletonList(orphan), 101, 0));
        when(resourcePersistService.list(any(QueryCondition.class), eq(2), eq(100)))
            .thenReturn(emptyPage());
        
        task.executeReconciliation();
        
        Map<?, ?> progress = capturedProgress();
        assertEquals(1, progress.get("orphaned"));
        assertEquals(1, progress.get("failed"));
        assertTrue(String.valueOf(progress.get("lastError")).contains("orphan"));
        verify(resourcePersistService, never()).delete(any(), any(), any());
        verifyNoInteractions(reconciler);
    }
    
    @Test
    void testExecuteRejectsInconsistentResourcePage() throws Exception {
        AiResource inconsistent = legacyResource("bad");
        inconsistent.setNamespaceId(TEAM_NAMESPACE);
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(100)))
            .thenReturn(page(Collections.singletonList(inconsistent), 1, 1));
        
        task.executeReconciliation();
        
        Map<?, ?> progress = capturedProgress();
        assertEquals(1, progress.get("failed"));
        assertTrue(String.valueOf(progress.get("lastError")).contains("inconsistent"));
        
        when(resourcePersistService.list(any(QueryCondition.class), eq(1), eq(100)))
            .thenReturn(null);
        task.executeReconciliation();
        assertTrue(String.valueOf(capturedProgress().get("lastError")).contains("Unable to page"));
    }
    
    @Test
    void testExecuteRejectsNullManifestPage() throws Exception {
        when(manifestStorage.list(PUBLIC_NAMESPACE, 1, 100)).thenReturn(null);
        
        task.executeReconciliation();
        
        Map<?, ?> progress = capturedProgress();
        assertEquals(1, progress.get("failed"));
        assertTrue(String.valueOf(progress.get("lastError")).contains("empty page"));
    }
    
    @Test
    void testExecuteFallsBackToPublicAndKeepsScanIncomplete() throws Exception {
        when(namespaceOperationService.getNamespaceList())
            .thenThrow(new IllegalStateException("namespace unavailable"));
        
        task.executeReconciliation();
        
        verify(manifestStorage).list(PUBLIC_NAMESPACE, 1, 100);
        Map<?, ?> progress = capturedProgress();
        assertEquals(false, progress.get("completeNamespaceScan"));
        assertEquals(false, progress.get("zeroDifference"));
        assertTrue(((Number) progress.get("failed")).intValue() > 0);
    }
    
    @Test
    void testExecutePersistsUnexpectedScanFailureAndTruncatesError() throws Exception {
        String longError = "x".repeat(3000);
        when(manifestStorage.list(PUBLIC_NAMESPACE, 1, 100))
            .thenThrow(new IllegalStateException(longError));
        
        task.executeReconciliation();
        
        Map<?, ?> progress = capturedProgress();
        assertEquals(2048, String.valueOf(progress.get("lastError")).length());
        assertEquals(1, progress.get("failed"));
    }
    
    @Test
    void testExecuteRecordsFailureWithoutMessage() throws Exception {
        when(manifestStorage.list(PUBLIC_NAMESPACE, 1, 100))
            .thenThrow(new IllegalStateException());
        
        task.executeReconciliation();
        
        Map<?, ?> progress = capturedProgress();
        assertEquals(1, progress.get("failed"));
        assertFalse(progress.containsKey("lastError"));
    }
    
    @Test
    void testActiveOrUnverifiableLeaseSkipsReconciliation() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(
            foundResponse("owner|" + (System.currentTimeMillis() + 60000), "md5"));
        task.executeReconciliation();
        verifyNoInteractions(manifestStorage, reconciler);
        verify(configOperationService, never()).publishConfig(any(), any(), any());
        
        reset(configQueryChainService, configOperationService, manifestStorage, reconciler);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse("owner|0", null));
        task.executeReconciliation();
        verify(configOperationService, never()).publishConfig(any(), any(), any());
    }
    
    @Test
    void testExpiredLeaseUsesCasAndCompletesScan() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse("owner|0", "old-md5"));
        
        task.executeReconciliation();
        
        ArgumentCaptor<ConfigRequestInfo> requestCaptor =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService, org.mockito.Mockito.atLeastOnce()).publishConfig(any(),
            requestCaptor.capture(), isNull());
        ConfigRequestInfo acquire = requestCaptor.getAllValues().get(0);
        assertEquals("old-md5", acquire.getCasMd5());
        assertEquals(true, acquire.getUpdateForExist());
        assertNotNull(capturedProgress());
    }
    
    @Test
    void testLeaseCreateRacesAndFailuresSkipWork() throws Exception {
        doThrow(new ConfigAlreadyExistsException("race")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        task.executeReconciliation();
        verifyNoInteractions(manifestStorage, reconciler);
        
        reset(configOperationService, manifestStorage, reconciler);
        doThrow(new IllegalStateException("failed")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        task.executeReconciliation();
        verifyNoInteractions(manifestStorage, reconciler);
    }
    
    @Test
    void testExpiredLeaseTakeoverFailureSkipsWork() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse("owner|0", "old-md5"));
        doThrow(new IllegalStateException("takeover lost")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        
        task.executeReconciliation();
        
        verifyNoInteractions(manifestStorage, reconciler);
    }
    
    @Test
    void testMalformedOrConflictingLeaseNeverOverwritesExistingConfig() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(foundResponse("malformed", "md5"));
        doThrow(new ConfigAlreadyExistsException("exists")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        task.executeReconciliation();
        verifyNoInteractions(manifestStorage, reconciler);
        
        reset(configQueryChainService, configOperationService, manifestStorage, reconciler);
        ConfigQueryChainResponse conflict = new ConfigQueryChainResponse();
        conflict.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT);
        conflict.setMessage("conflict");
        conflict.setContent("owner|0");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(conflict);
        doThrow(new ConfigAlreadyExistsException("exists")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        task.executeReconciliation();
        verifyNoInteractions(manifestStorage, reconciler);
    }
    
    @Test
    void testLeaseRenewalSuccessFailureAndReleaseFailureAreContained() throws Exception {
        Object lease = ReflectionTestUtils.invokeMethod(task, "tryAcquireLease");
        assertNotNull(lease);
        ReflectionTestUtils.invokeMethod(lease, "renewSafely");
        assertEquals(true, ReflectionTestUtils.invokeMethod(lease, "isOwned"));
        ReflectionTestUtils.invokeMethod(lease, "close");
        
        reset(configOperationService);
        lease = ReflectionTestUtils.invokeMethod(task, "tryAcquireLease");
        assertNotNull(lease);
        doThrow(new IllegalStateException("renew failed")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        ReflectionTestUtils.invokeMethod(lease, "renewSafely");
        assertEquals(false, ReflectionTestUtils.invokeMethod(lease, "isOwned"));
        ReflectionTestUtils.invokeMethod(lease, "renewSafely");
        Object lostLease = lease;
        assertThrows(IllegalStateException.class,
            () -> ReflectionTestUtils.invokeMethod(lostLease, "assertOwned"));
        ReflectionTestUtils.invokeMethod(lease, "close");
        
        reset(configOperationService);
        lease = ReflectionTestUtils.invokeMethod(task, "tryAcquireLease");
        assertNotNull(lease);
        doThrow(new IllegalStateException("release failed")).when(configOperationService)
            .publishConfig(any(), any(), isNull());
        ReflectionTestUtils.invokeMethod(lease, "close");
    }
    
    @Test
    void testProgressFailureDoesNotPreventLeaseRelease() throws Exception {
        when(configOperationService.publishConfig(any(), any(), isNull())).thenReturn(true)
            .thenThrow(new IllegalStateException("progress failed")).thenReturn(true);
        
        task.executeReconciliation();
        
        ArgumentCaptor<ConfigForm> captor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService, org.mockito.Mockito.times(3))
            .publishConfig(captor.capture(), any(), isNull());
        assertTrue(captor.getAllValues().get(2).getContent().endsWith("|0"));
    }
    
    @Test
    void testApplicationReadyHonorsRootDisabledAndSingleInitialization() throws Exception {
        task.onApplicationEvent(childContextEvent());
        verifyNoInteractions(configOperationService);
        
        System.setProperty(McpLifecycleReconciliationTask.RECONCILIATION_ENABLED_KEY, "false");
        EnvUtil.setEnvironment(new StandardEnvironment());
        task.onApplicationEvent(rootContextEvent());
        task.onApplicationEvent(rootContextEvent());
        verify(configOperationService, after(300).never()).publishConfig(any(), any(), any());
    }
    
    @Test
    void testApplicationReadySchedulesEnabledReconciliation() throws Exception {
        System.setProperty(McpLifecycleReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_KEY,
            "1");
        EnvUtil.setEnvironment(new StandardEnvironment());
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(configOperationService, timeout(2000).atLeastOnce())
            .publishConfig(any(), any(), isNull());
    }
    
    @Test
    void testPositiveLongUsesConfiguredAndFallbackValues() {
        System.setProperty(McpLifecycleReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_KEY,
            "7");
        EnvUtil.setEnvironment(new StandardEnvironment());
        assertEquals(7L, invokePositiveLong());
        
        System.setProperty(McpLifecycleReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_KEY,
            "0");
        EnvUtil.setEnvironment(new StandardEnvironment());
        assertEquals(3L, invokePositiveLong());
        
        System.setProperty(McpLifecycleReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_KEY,
            "invalid");
        EnvUtil.setEnvironment(new StandardEnvironment());
        assertEquals(3L, invokePositiveLong());
    }
    
    private long invokePositiveLong() {
        Number result = ReflectionTestUtils.invokeMethod(task, "positiveLong",
            McpLifecycleReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_KEY, 3L);
        return result.longValue();
    }
    
    private List<ConfigForm> capturedForms() throws Exception {
        ArgumentCaptor<ConfigForm> captor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService, org.mockito.Mockito.atLeastOnce())
            .publishConfig(captor.capture(), any(), isNull());
        return captor.getAllValues();
    }
    
    private Map<?, ?> capturedProgress() throws Exception {
        List<ConfigForm> progressForms = capturedForms().stream()
            .filter(form -> McpLifecycleReconciliationTask.RECONCILIATION_PROGRESS_DATA_ID
                .equals(form.getDataId()))
            .toList();
        if (progressForms.isEmpty()) {
            return null;
        }
        return JacksonUtils.toObj(progressForms.get(progressForms.size() - 1).getContent(),
            Map.class);
    }
    
    private ApplicationReadyEvent rootContextEvent() {
        ApplicationReadyEvent event = org.mockito.Mockito.mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext context =
            org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        when(event.getApplicationContext()).thenReturn(context);
        when(context.getParent()).thenReturn(null);
        return event;
    }
    
    private ApplicationReadyEvent childContextEvent() {
        ApplicationReadyEvent event = org.mockito.Mockito.mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext context =
            org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        when(event.getApplicationContext()).thenReturn(context);
        when(context.getParent()).thenReturn(
            org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class));
        return event;
    }
    
    private McpServerVersionInfo manifest(String name, String id) {
        McpServerVersionInfo result = new McpServerVersionInfo();
        result.setName(name);
        result.setId(id);
        return result;
    }
    
    private AiResource legacyResource(String name) {
        AiResource result = new AiResource();
        result.setNamespaceId(PUBLIC_NAMESPACE);
        result.setName(name);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        result.setFrom(McpHistoricalResourceReconciler.LEGACY_SOURCE);
        return result;
    }
    
    private ConfigQueryChainResponse notFoundResponse() {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        return result;
    }
    
    private ConfigQueryChainResponse foundResponse(String content, String md5) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        result.setContent(content);
        result.setMd5(md5);
        return result;
    }
    
    private <T> Page<T> emptyPage() {
        return page(Collections.emptyList(), 0, 0);
    }
    
    private <T> Page<T> page(List<T> items, int totalCount, int pagesAvailable) {
        Page<T> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(totalCount);
        result.setPagesAvailable(pagesAvailable);
        return result;
    }
}
