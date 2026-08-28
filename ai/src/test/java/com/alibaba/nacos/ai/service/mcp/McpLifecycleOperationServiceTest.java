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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecution;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionResult;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DuplicateKeyException;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpLifecycleOperationServiceTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String MCP_NAME = "demo-mcp";
    
    private static final String MCP_ID = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
    
    private static final String VERSION_ONE = "1.0.0";
    
    private static final String VERSION_TWO = "2.0.0";
    
    @Mock
    private AiResourcePersistService resourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService versionPersistService;
    
    @Mock
    private PipelineExecutionRepository pipelineExecutionRepository;
    
    @Mock
    private McpVersionStorageService versionStorageService;
    
    @Mock
    private McpServingManifestStorage manifestStorage;
    
    @Mock
    private McpEndpointOperationService endpointOperationService;
    
    @Mock
    private AiResourceIndexMaintenanceService indexMaintenanceService;
    
    @Mock
    private McpCanonicalAuthorizationService canonicalAuthorizationService;
    
    @Mock
    private PublishPipelineExecutor publishPipelineExecutor;
    
    private final AtomicReference<AiResource> resource = new AtomicReference<>();
    
    private final Map<String, AiResourceVersion> versions = new LinkedHashMap<>();
    
    private final Map<String, McpVersionStorageContents> contents = new LinkedHashMap<>();
    
    private final AtomicReference<McpServerVersionInfo> manifest = new AtomicReference<>();
    
    private final AtomicLong sequence = new AtomicLong();
    
    private final AtomicBoolean failNextManifestPublish = new AtomicBoolean();
    
    private final AtomicBoolean returnStaleNextManifestRead = new AtomicBoolean();
    
    private final AtomicBoolean failNextStorageDelete = new AtomicBoolean();
    
    private final AtomicBoolean failNextResourceDelete = new AtomicBoolean();
    
    private McpLifecycleOperationService service;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        AiResourceManager resourceManager = new AiResourceManager(resourcePersistService,
            versionPersistService, pipelineExecutionRepository);
        McpResourceLocator resourceLocator = new McpResourceLocator(resourcePersistService);
        service = new McpLifecycleOperationService(resourceLocator, resourceManager,
            resourcePersistService, versionPersistService, versionStorageService, manifestStorage,
            endpointOperationService, canonicalAuthorizationService, publishPipelineExecutor);
        service.setIndexMaintenanceService(indexMaintenanceService);
        setUpResourceRepository();
        setUpVersionRepository();
        setUpContentStorage();
        setUpManifestStorage();
        when(resourcePersistService.generateLikeArgument("*demo*"))
            .thenReturn("%demo%");
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void testCreateListAndGetUseLifecycleRowsAndDescriptors() throws Exception {
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = resourceSpecification();
        
        String result = service.createMcpServer(NAMESPACE_ID,
            server(VERSION_ONE, "first"), tools, resources, null);
        
        assertEquals(MCP_ID, result);
        assertNotNull(resource.get());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, resource.get().getStatus());
        assertNull(versionInfo(resource.get()).getEditingVersion());
        assertEquals(VERSION_ONE, latest(resource.get()));
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE,
            versions.get(VERSION_ONE).getStatus());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertEquals(1, manifest.get().getVersionDetails().size());
        Page<McpServerBasicInfo> page = service.listMcpServerWithPage(NAMESPACE_ID, "demo",
            Constants.MCP_LIST_SEARCH_BLUR, 1, 10);
        assertEquals(1, page.getTotalCount());
        assertEquals(MCP_NAME, page.getPageItems().get(0).getName());
        McpServerDetailInfo detail = service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, MCP_NAME,
            null);
        assertEquals(VERSION_ONE, detail.getVersion());
        assertNotNull(detail.getToolSpec());
        assertNotNull(detail.getResourceSpec());
        InOrder publishOrder = inOrder(resourcePersistService, versionPersistService,
            versionStorageService, manifestStorage);
        publishOrder.verify(resourcePersistService).insert(any(AiResource.class));
        publishOrder.verify(versionStorageService).save(any(McpVersionStorageDescriptor.class),
            any(McpVersionStorageContents.class));
        publishOrder.verify(versionPersistService).insert(any(AiResourceVersion.class));
        publishOrder.verify(manifestStorage).publish(eq(NAMESPACE_ID),
            any(McpServerVersionInfo.class));
        verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME);
    }
    
    @Test
    void testIdOnlyCompatibilityOperationsAuthorizeResolvedCanonicalName() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        
        McpServerDetailInfo detail =
            service.getMcpServerDetail(NAMESPACE_ID, MCP_ID, null, VERSION_ONE);
        assertEquals(MCP_NAME, detail.getName());
        McpServerBasicInfo update = server(VERSION_ONE, "updated");
        update.setName(null);
        service.updateMcpServer(NAMESPACE_ID, true, update, null, null, null, false);
        assertEquals(MCP_NAME, update.getName());
        service.deleteMcpServer(NAMESPACE_ID, null, MCP_ID, null);
        
        verify(canonicalAuthorizationService).authorizeIdOnly(NAMESPACE_ID, MCP_NAME, null,
            MCP_ID, ActionTypes.READ);
        verify(canonicalAuthorizationService, times(2)).authorizeIdOnly(NAMESPACE_ID, MCP_NAME,
            null, MCP_ID, ActionTypes.WRITE);
    }
    
    @Test
    void testUpdateKeepsLatestUntilSameVersionIsPublished() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        McpServerBasicInfo second = server(VERSION_TWO, "second");
        
        service.updateMcpServer(NAMESPACE_ID, false, second, null, null, null, false);
        
        assertEquals(VERSION_ONE, latest(resource.get()));
        assertEquals("first", resource.get().getDesc());
        assertEquals(2, manifest.get().getVersionDetails().size());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertEquals("second",
            service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_TWO)
                .getDescription());
        
        service.updateMcpServer(NAMESPACE_ID, true, second, null, null, null, false);
        
        assertEquals(VERSION_TWO, latest(resource.get()));
        assertEquals("second", resource.get().getDesc());
        assertEquals(VERSION_TWO, manifest.get().getLatestPublishedVersion());
        assertTrue(manifest.get().getVersionDetails().stream()
            .filter(each -> VERSION_TWO.equals(each.getVersion()))
            .findFirst().orElseThrow().getIs_latest());
    }
    
    @Test
    void testSameLatestVersionDraftOverwriteRetainsPublishedPresentation() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        String releaseDate = manifest.get().getVersionDetails().get(0).getRelease_date();
        McpServerBasicInfo draft = server(VERSION_ONE, "draft");
        draft.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        
        service.updateMcpServer(NAMESPACE_ID, false, draft, null, null, null, false);
        
        assertEquals("first", resource.get().getDesc());
        assertEquals("first", manifest.get().getDescription());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, manifest.get().getProtocol());
        assertEquals(releaseDate, manifest.get().getVersionDetails().get(0).getRelease_date());
        McpServerDetailInfo detail = service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME,
            VERSION_ONE);
        assertEquals("draft", detail.getDescription());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE, detail.getProtocol());
    }
    
    @Test
    void testSameOnlineVersionDraftOverwriteRequiresServingPresentation() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        manifest.set(null);
        
        assertThrows(NacosException.class,
            () -> service.updateMcpServer(NAMESPACE_ID, false,
                server(VERSION_ONE, "draft"), null, null, null, false));
        
        assertEquals("first",
            service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE)
                .getDescription());
    }
    
    @Test
    void testSameVersionDraftOverwriteRetainsOfflineLifecycleState() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        
        service.updateMcpServer(NAMESPACE_ID, false, server(VERSION_ONE, "draft"), null, null,
            null, false);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_OFFLINE,
            versions.get(VERSION_ONE).getStatus());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, resource.get().getStatus());
        assertNull(manifest.get());
        assertEquals("draft",
            service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE)
                .getDescription());
    }
    
    @Test
    void testCreateRetriesFromRowsAfterManifestFailure() throws Exception {
        failNextManifestPublish.set(true);
        
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        assertNotNull(resource.get());
        assertNotNull(versions.get(VERSION_ONE));
        assertNull(manifest.get());
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getEditingVersion());
        
        String result = service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null,
            null, null);
        
        assertEquals(MCP_ID, result);
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertNull(versionInfo(resource.get()).getEditingVersion());
    }
    
    @Test
    void testCreateRetriesAfterStaleManifestVerification() throws Exception {
        returnStaleNextManifestRead.set(true);
        
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        
        assertNotNull(manifest.get());
        assertEquals("first", manifest.get().getDescription());
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getEditingVersion());
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        assertNull(versionInfo(resource.get()).getEditingVersion());
    }
    
    @Test
    void testCreateRejectsCompletedOfflineResourceAsDuplicate() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        
        NacosException error = assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        assertTrue(error.getErrMsg().contains("has existed"));
    }
    
    @Test
    void testOfflineAndOnlineConvergeManifestFromCanonicalStatus() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        service.updateMcpServer(NAMESPACE_ID, true, server(VERSION_TWO, "second"), null, null,
            null, false);
        
        service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_TWO);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_OFFLINE,
            versions.get(VERSION_TWO).getStatus());
        assertEquals(VERSION_ONE, latest(resource.get()));
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        
        service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, resource.get().getStatus());
        assertNull(latest(resource.get()));
        assertNull(manifest.get());
        Page<McpServerBasicInfo> offlinePage = service.listMcpServerWithPage(NAMESPACE_ID, null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10);
        assertFalse(offlinePage.getPageItems().get(0).isEnabled());
        assertFalse(offlinePage.getPageItems().get(0).getVersionDetail().getIs_latest());
        
        service.onlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_TWO, true);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE,
            versions.get(VERSION_TWO).getStatus());
        assertEquals(VERSION_TWO, latest(resource.get()));
        assertNotNull(manifest.get());
        assertTrue(manifest.get().isEnabled());
    }
    
    @Test
    void testVersionDeleteRetriesAfterStorageFailureWithoutServingDeletedVersion()
        throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        service.updateMcpServer(NAMESPACE_ID, true, server(VERSION_TWO, "second"), null, null,
            null, false);
        failNextStorageDelete.set(true);
        
        assertThrows(NacosException.class,
            () -> service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, null, VERSION_TWO));
        
        assertEquals(AiResourceConstants.VERSION_STATUS_OFFLINE,
            versions.get(VERSION_TWO).getStatus());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertNotNull(versions.get(VERSION_TWO));
        
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, null, VERSION_TWO);
        
        assertNull(versions.get(VERSION_TWO));
        assertEquals(VERSION_ONE, latest(resource.get()));
        verify(endpointOperationService,
            org.mockito.Mockito.atLeastOnce()).deleteMcpServerEndpointService(NAMESPACE_ID,
                MCP_NAME + "::" + VERSION_TWO);
    }
    
    @Test
    void testFullDeleteKeepsDisabledRowsUntilStorageCleanupSucceeds() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        failNextStorageDelete.set(true);
        
        assertThrows(NacosException.class,
            () -> service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, null));
        
        assertNotNull(resource.get());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, resource.get().getStatus());
        assertEquals(AiResourceConstants.VERSION_STATUS_OFFLINE,
            versions.get(VERSION_ONE).getStatus());
        assertNull(manifest.get());
        
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, MCP_ID, null);
        
        assertNull(resource.get());
        assertTrue(versions.isEmpty());
        assertTrue(contents.isEmpty());
    }
    
    @Test
    void testLastVersionDeleteRetriesRetainedEmptyResourceCleanup() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        failNextResourceDelete.set(true);
        
        assertThrows(IllegalStateException.class,
            () -> service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, null, VERSION_ONE));
        
        assertNotNull(resource.get());
        assertTrue(versions.isEmpty());
        assertTrue(contents.isEmpty());
        
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, null, VERSION_ONE);
        
        assertNull(resource.get());
    }
    
    @Test
    void testRejectsMismatchedNameAndIdAndMissingVersion() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID,
                "9d7939c0-72ea-4ef4-b232-418d1e16b45c", MCP_NAME, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, "missing"));
    }
    
    @Test
    void testInvalidStoredDescriptorFailsBeforeContentRead() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        versions.get(VERSION_ONE).setStorage("{}");
        
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testCreateWithEndpointPersistsServiceReferenceAndInjectsEndpoint() throws Exception {
        McpEndpointSpec endpoint = new McpEndpointSpec();
        endpoint.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        endpoint.setData(Map.of(Constants.MCP_BACKEND_INSTANCE_PROTOCOL_KEY,
            AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE));
        when(endpointOperationService.createMcpServerEndpointServiceIfNecessary(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE, endpoint, false)).thenReturn(
                Service.newService(NAMESPACE_ID, "endpoint-group",
                    MCP_NAME + "::" + VERSION_ONE));
        McpServerBasicInfo specification = server(VERSION_ONE, "endpoint");
        specification.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE);
        
        service.createMcpServer(NAMESPACE_ID, specification, null, null, endpoint);
        
        McpVersionStorageDescriptor descriptor = McpVersionStorageDescriptorSerializer
            .deserialize(versions.get(VERSION_ONE).getStorage());
        McpServerStorageInfo stored = JacksonUtils.toObj(
            new String(contents.get(storageKey(descriptor)).getServerContent(),
                StandardCharsets.UTF_8),
            McpServerStorageInfo.class);
        assertEquals("endpoint-group",
            stored.getRemoteServerConfig().getServiceRef().getGroupName());
        assertEquals(MCP_NAME + "::" + VERSION_ONE,
            stored.getRemoteServerConfig().getServiceRef().getServiceName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE,
            stored.getRemoteServerConfig().getServiceRef().getTransportProtocol());
        
        service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE);
        
        verify(endpointOperationService).injectEndpoint(any(McpServerDetailInfo.class));
    }
    
    @Test
    void testCreateGeneratesIdAndAcceptsVersionFieldInDefaultNamespace() throws Exception {
        McpServerBasicInfo specification = server("v2", "generated");
        specification.setId(null);
        specification.setNamespaceId(null);
        specification.setVersionDetail(null);
        
        String generatedId = service.createMcpServer(null, specification, null, null, null);
        
        assertTrue(UUID.fromString(generatedId).toString().equals(generatedId));
        assertEquals(NAMESPACE_ID, resource.get().getNamespaceId());
        assertEquals("v2", versions.get("v2").getVersion());
        assertEquals("v2", specification.getVersionDetail().getVersion());
    }
    
    @Test
    void testCreateRejectsInvalidSpecificationsNamespacesAndDuplicateId() throws Exception {
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, null, null, null, null));
        
        McpServerBasicInfo missingName = server(VERSION_ONE, "missing-name");
        missingName.setName(" ");
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, missingName, null, null, null));
        
        McpServerBasicInfo missingVersion = server(VERSION_ONE, "missing-version");
        missingVersion.setVersion(null);
        missingVersion.setVersionDetail(null);
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, missingVersion, null, null, null));
        
        McpServerBasicInfo invalidId = server(VERSION_ONE, "invalid-id");
        invalidId.setId("not-a-uuid");
        NacosException invalidIdError = assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, invalidId, null, null, null));
        assertTrue(invalidIdError.getMessage().contains("uuid pattern"));
        assertThrows(NacosException.class,
            () -> service.listMcpServerWithPage("invalid namespace", null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10));
        
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        McpServerBasicInfo duplicateId = server(VERSION_TWO, "duplicate-id");
        duplicateId.setName("another-mcp");
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, duplicateId, null, null, null));
    }
    
    @Test
    void testCreateRecoversWhenInsertSucceededBeforeRepositoryFailure() throws Exception {
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            AiResource inserted = invocation.getArgument(0);
            inserted.setId(sequence.incrementAndGet());
            inserted.setMetaVersion(1L);
            resource.set(inserted);
            throw new IllegalStateException("result lost");
        });
        
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        assertNotNull(manifest.get());
    }
    
    @Test
    void testCreateTranslatesInvalidInsertResultAndDuplicateKey() throws Exception {
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(0L);
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
    }
    
    @Test
    void testCreateRejectsRecoveredRowWithInvalidCompatibilityIdentity() throws Exception {
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            AiResource inserted = invocation.getArgument(0);
            inserted.setExt("{}");
            resource.set(inserted);
            throw new IllegalStateException("ambiguous insert");
        });
        
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
    }
    
    @Test
    void testCreateRetryRejectsDifferentCompatibilityIdAndContent() throws Exception {
        failNextManifestPublish.set(true);
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        
        McpServerBasicInfo differentId = server(VERSION_ONE, "first");
        differentId.setId("9d7939c0-72ea-4ef4-b232-418d1e16b45c");
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, differentId, null, null, null));
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "different"), null,
                null, null));
    }
    
    @Test
    void testCreateRetryRestoresMissingVersionContent() throws Exception {
        failNextManifestPublish.set(true);
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        contents.clear();
        
        assertEquals(MCP_ID,
            service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        assertFalse(contents.isEmpty());
    }
    
    @Test
    void testCreateFailsAfterConcurrentMetadataRetriesAreExhausted() {
        when(resourcePersistService.updateMetaCas(anyString(), anyString(), anyString(), anyLong(),
            any(AiResource.class))).thenReturn(false);
        
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
    }
    
    @Test
    void testOfflineFailsWhenResourceMetaVersionIsMissing() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        resource.get().setMetaVersion(null);
        
        assertThrows(NacosException.class,
            () -> service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testOfflineVerifiesServingManifestDeletion() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        doAnswer(invocation -> null).when(manifestStorage).delete(anyString(), anyString());
        
        assertThrows(NacosException.class,
            () -> service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE));
        assertNotNull(manifest.get());
    }
    
    @Test
    void testDraftUpdateRejectsRetainedManifestWithDifferentIdentity() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        manifest.get().setId("9d7939c0-72ea-4ef4-b232-418d1e16b45c");
        
        assertThrows(NacosException.class,
            () -> service.updateMcpServer(NAMESPACE_ID, false,
                server(VERSION_ONE, "draft"), null, null, null, false));
    }
    
    @Test
    void testRejectsInvalidResourceVersionRowsAndStoredContents() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        AiResourceVersion row = versions.get(VERSION_ONE);
        row.setNamespaceId("other");
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
        row.setNamespaceId(NAMESPACE_ID);
        
        McpVersionStorageDescriptor descriptor = McpVersionStorageDescriptorSerializer
            .deserialize(row.getStorage());
        String key = storageKey(descriptor);
        McpVersionStorageContents original = contents.get(key);
        McpServerStorageInfo stored = JacksonUtils.toObj(
            new String(original.getServerContent(), StandardCharsets.UTF_8),
            McpServerStorageInfo.class);
        stored.setId("9d7939c0-72ea-4ef4-b232-418d1e16b45c");
        contents.put(key, new McpVersionStorageContents(
            JacksonUtils.toJson(stored).getBytes(StandardCharsets.UTF_8), null, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
        
        stored.setId(MCP_ID);
        stored.setToolsDescriptionRef("missing-tools.json");
        contents.put(key, new McpVersionStorageContents(
            JacksonUtils.toJson(stored).getBytes(StandardCharsets.UTF_8), null, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
        
        contents.put(key, new McpVersionStorageContents("invalid-json".getBytes(
            StandardCharsets.UTF_8), null, null));
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testRejectsInvalidResourceAndNullVersionPage() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        resource.get().setExt("{}");
        assertThrows(NacosException.class,
            () -> service.listMcpServerWithPage(NAMESPACE_ID, null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10));
        
        resource.get().setExt(McpResourceExtSerializer.serialize(resourceExt()));
        resource.get().setName(" ");
        assertThrows(NacosException.class,
            () -> service.listMcpServerWithPage(NAMESPACE_ID, null,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10));
        resource.get().setName(MCP_NAME);
        when(versionPersistService.list(anyString(), anyString(), anyString(), any(), anyInt(),
            anyInt())).thenReturn(null);
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testListReturnsEmptyPageForAlwaysEmptyVisibilityCondition() throws Exception {
        AiResourceManager emptyManager = mock(AiResourceManager.class);
        QueryCondition emptyCondition = new QueryCondition();
        emptyCondition.setAlwaysEmpty(true);
        when(emptyManager.buildQueryCondition(NAMESPACE_ID, AiResourceConstants.RESOURCE_TYPE_MCP,
            null, null, VisibilityConstants.ACTION_READ)).thenReturn(emptyCondition);
        McpLifecycleOperationService emptyService = new McpLifecycleOperationService(
            new McpResourceLocator(resourcePersistService), emptyManager, resourcePersistService,
            versionPersistService, versionStorageService, manifestStorage,
            endpointOperationService, canonicalAuthorizationService, publishPipelineExecutor);
        
        Page<McpServerBasicInfo> result = emptyService.listMcpServerWithPage(NAMESPACE_ID, null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 3, 10);
        
        assertEquals(3, result.getPageNumber());
        assertTrue(result.getPageItems().isEmpty());
    }
    
    @Test
    void testOfflineVNumberFallsBackToStoredVersionForDetail() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server("v2", "v-number"), null, null, null);
        service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, "v2");
        
        assertEquals("v2",
            service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, null).getVersion());
    }
    
    @Test
    void testOfflineOpaqueVersionFallsBackToStoredVersionForDetail() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server("legacy", "opaque"), null, null, null);
        service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, "legacy");
        
        assertEquals("legacy",
            service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, null).getVersion());
    }
    
    @Test
    void testDisabledCreateDetectsOptionalSpecificationShapes() throws Exception {
        McpToolSpecification tools = new McpToolSpecification();
        tools.setTools(null);
        tools.setSecuritySchemes(Collections.emptyList());
        McpResourceSpecification resources = new McpResourceSpecification();
        resources.setResourceTemplates(Collections.singletonList(Map.of("uri", "resource://x")));
        McpServerBasicInfo specification = server(VERSION_ONE, "disabled");
        specification.setEnabled(false);
        
        service.createMcpServer(NAMESPACE_ID, specification, tools, resources, null);
        
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, resource.get().getStatus());
        McpVersionStorageContents stored = contents.values().iterator().next();
        assertNotNull(stored.getToolContent());
        assertNotNull(stored.getResourceContent());
    }
    
    @Test
    void testUpdateAndLifecycleActionsValidateBoundaryInputs() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        
        assertThrows(NacosException.class,
            () -> service.updateMcpServer(NAMESPACE_ID, true, null, null, null, null, false));
        assertThrows(NacosException.class,
            () -> service.onlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_TWO, true));
        assertThrows(NacosException.class,
            () -> service.offlineMcpServerVersion(NAMESPACE_ID, MCP_NAME, VERSION_TWO));
        
        Page<McpServerBasicInfo> page = service.listMcpServerWithPage(NAMESPACE_ID, MCP_NAME,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10);
        assertEquals(1, page.getTotalCount());
    }
    
    @Test
    void testLastVersionDeleteRemovesResourceInSameAttempt() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        
        service.deleteMcpServer(NAMESPACE_ID, MCP_NAME, null, VERSION_ONE);
        
        assertNull(resource.get());
        assertTrue(versions.isEmpty());
        assertTrue(contents.isEmpty());
    }
    
    @Test
    void testCreateRetryRejectsChangedStorageDescriptor() throws Exception {
        failNextManifestPublish.set(true);
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
        AiResourceVersion retained = versions.get(VERSION_ONE);
        McpVersionStorageDescriptor changed = McpVersionStorageDescriptorSerializer.deserialize(
            retained.getStorage());
        changed.setServerKey(changed.getServerKey().replace(MCP_ID,
            "6f27f843-2f55-49e7-9aa4-d6957fefbc61"));
        retained.setStorage(McpVersionStorageDescriptorSerializer.serialize(changed));
        
        assertThrows(NacosException.class,
            () -> service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
                null));
    }
    
    @Test
    void testGetRejectsNullJsonServerContent() throws Exception {
        service.createMcpServer(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null, null);
        McpVersionStorageDescriptor descriptor = McpVersionStorageDescriptorSerializer.deserialize(
            versions.get(VERSION_ONE).getStorage());
        contents.put(storageKey(descriptor), new McpVersionStorageContents(
            "null".getBytes(StandardCharsets.UTF_8), null, null));
        
        assertThrows(NacosException.class,
            () -> service.getMcpServerDetail(NAMESPACE_ID, null, MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testStandardDraftCreateUpdateListAndLabelsUseLifecycleRows() throws Exception {
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = resourceSpecification();
        
        McpLifecycleVersionDetail created = service.createLifecycleDraft(NAMESPACE_ID,
            server(VERSION_ONE, "draft"), tools, resources, null);
        
        assertEquals(NAMESPACE_ID, created.getNamespaceId());
        assertEquals(MCP_NAME, created.getMcpName());
        assertEquals(VERSION_ONE, created.getVersion());
        assertEquals(AiResourceConstants.VERSION_STATUS_DRAFT, created.getStatus());
        assertNull(created.getServerSpecification().getId());
        assertNotNull(created.getToolSpecification());
        assertNotNull(created.getResourceSpecification());
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getEditingVersion());
        assertNull(manifest.get());
        
        Page<McpLifecycleVersionSummary> page = service.listLifecycleVersions(NAMESPACE_ID,
            MCP_NAME, AiResourceConstants.VERSION_STATUS_DRAFT, 1, 10);
        assertEquals(1, page.getTotalCount());
        assertEquals(VERSION_ONE, page.getPageItems().get(0).getVersion());
        assertFalse(page.getPageItems().get(0).getLatest());
        
        McpServerBasicInfo replacement = server(VERSION_ONE, "updated draft");
        McpLifecycleVersionDetail updated = service.updateLifecycleDraft(NAMESPACE_ID,
            replacement, null, null, null);
        assertEquals("updated draft", updated.getDescription());
        assertNull(updated.getToolSpecification());
        assertNull(updated.getResourceSpecification());
        assertEquals("updated draft",
            service.getLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE).getDescription());
        
        Map<String, String> labels = service.updateLifecycleLabels(NAMESPACE_ID, MCP_NAME,
            Map.of("candidate", VERSION_TWO));
        assertEquals(VERSION_TWO, labels.get("candidate"));
        assertNull(labels.get(AiResourceConstants.LABEL_LATEST));
        verify(indexMaintenanceService, times(3)).schedule(NAMESPACE_ID,
            AiResourceConstants.RESOURCE_TYPE_MCP, MCP_NAME);
    }
    
    @Test
    void testStandardDraftCreateUsesExistingResourceAndRejectsDuplicateVersion()
        throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "first"), null, null,
            null);
        service.submitLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        
        McpLifecycleVersionDetail second = service.createLifecycleDraft(NAMESPACE_ID,
            server(VERSION_TWO, "second"), null, null, null);
        
        assertEquals(VERSION_TWO, second.getVersion());
        assertEquals(VERSION_TWO, versionInfo(resource.get()).getEditingVersion());
        service.deleteLifecycleDraft(NAMESPACE_ID, MCP_NAME, VERSION_TWO);
        assertThrows(NacosException.class, () -> service.createLifecycleDraft(NAMESPACE_ID,
            server(VERSION_ONE, "duplicate"), null, null, null));
    }
    
    @Test
    void testStandardDraftInsertFailureCanRetryWithoutWorkingPointer() throws Exception {
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(0L);
        
        assertThrows(NacosException.class, () -> service.createLifecycleDraft(NAMESPACE_ID,
            server(VERSION_ONE, "draft"), null, null, null));
        assertNull(versionInfo(resource.get()).getEditingVersion());
        assertNull(versions.get(VERSION_ONE));
        
        setUpVersionRepository();
        McpLifecycleVersionDetail retried = service.createLifecycleDraft(NAMESPACE_ID,
            server(VERSION_ONE, "draft"), null, null, null);
        assertEquals(VERSION_ONE, retried.getVersion());
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getEditingVersion());
    }
    
    @Test
    void testStandardDraftMapsDuplicateVersionInsert() throws Exception {
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        
        assertThrows(NacosException.class, () -> service.createLifecycleDraft(NAMESPACE_ID,
            server(VERSION_ONE, "draft"), null, null, null));
        assertNull(versionInfo(resource.get()).getEditingVersion());
    }
    
    @Test
    void testStandardVersionSummaryHandlesTimestampBoundaries() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        AiResourceVersion row = versions.get(VERSION_ONE);
        row.setGmtCreate(null);
        row.setGmtModified(new Timestamp(42L));
        
        McpLifecycleVersionSummary summary = service.listLifecycleVersions(NAMESPACE_ID,
            MCP_NAME, null, 1, 10).getPageItems().get(0);
        
        assertNull(summary.getCreateTime());
        assertEquals(42L, summary.getUpdateTime());
    }
    
    @Test
    void testStandardVersionListHandlesMissingSourceAndItems() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        Page<AiResourceVersion> pageWithoutItems = new Page<>();
        pageWithoutItems.setPageItems(null);
        AtomicLong lifecycleListCalls = new AtomicLong();
        when(versionPersistService.list(anyString(), anyString(), anyString(), any(), anyInt(),
            anyInt())).thenAnswer(invocation -> {
                int pageSize = invocation.getArgument(5);
                if (pageSize == 10) {
                    return lifecycleListCalls.getAndIncrement() == 0 ? null : pageWithoutItems;
                }
                return versionPage(invocation.getArgument(3), invocation.getArgument(4));
            });
        
        assertTrue(service.listLifecycleVersions(NAMESPACE_ID, MCP_NAME, null, 1, 10)
            .getPageItems().isEmpty());
        assertTrue(service.listLifecycleVersions(NAMESPACE_ID, MCP_NAME, null, 1, 10)
            .getPageItems().isEmpty());
    }
    
    @Test
    void testStandardSubmitPublishesDirectlyAndRejectsOnlineResubmit() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        
        McpLifecycleVersionSummary published = service.submitLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, published.getStatus());
        assertTrue(published.getLatest());
        assertNull(versionInfo(resource.get()).getEditingVersion());
        assertEquals(1, versionInfo(resource.get()).getOnlineCnt());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertNotNull(service.getLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE)
            .getServerSpecification().getVersionDetail().getRelease_date());
        assertThrows(NacosException.class, () -> service.submitLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE));
    }
    
    @Test
    void testStandardSubmitSupportsReviewingWorkingPointer() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        versions.get(VERSION_ONE).setStatus(AiResourceConstants.VERSION_STATUS_REVIEWING);
        ResourceVersionInfo info = versionInfo(resource.get());
        info.setEditingVersion(null);
        info.setReviewingVersion(VERSION_ONE);
        resource.get().setVersionInfo(JacksonUtils.toJson(info));
        
        McpLifecycleVersionSummary summary = service.submitLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, summary.getStatus());
        assertNull(versionInfo(resource.get()).getReviewingVersion());
    }
    
    @Test
    void testStandardSubmitUsesMcpPipelineAndPublishesApprovedReview() throws Exception {
        AtomicReference<PipelineCallback> callback = new AtomicReference<>();
        AtomicReference<ResourceFilesPipelineContext> context = new AtomicReference<>();
        AtomicReference<String> executionId = new AtomicReference<>();
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.MCP))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(ResourceFilesPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenAnswer(invocation -> {
                context.set(invocation.getArgument(0));
                callback.set(invocation.getArgument(1));
                executionId.set(invocation.getArgument(2));
                return executionId.get();
            });
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"),
            new McpToolSpecification(), resourceSpecification(), null);
        
        McpLifecycleVersionSummary reviewing = service.submitLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_REVIEWING, reviewing.getStatus());
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getReviewingVersion());
        assertNull(versionInfo(resource.get()).getEditingVersion());
        assertNull(manifest.get());
        assertEquals(PublishPipelineResourceType.MCP, context.get().getResourceType());
        assertEquals(3, context.get().getFiles().size());
        assertEquals("mcp-server.json", context.get().getFiles().get(0).getFilePath());
        assertTrue(context.get().getFiles().get(0).getContent().contains(MCP_NAME));
        service.submitLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        verify(publishPipelineExecutor, times(1)).execute(
            any(ResourceFilesPipelineContext.class), any(PipelineCallback.class), anyString());
        
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId(executionId.get());
        result.setStatus(PipelineExecutionStatus.APPROVED);
        result.setPipeline(Collections.emptyList());
        callback.get().onComplete(result);
        assertEquals(AiResourceConstants.VERSION_STATUS_REVIEWED,
            versions.get(VERSION_ONE).getStatus());
        PipelineExecution execution = new PipelineExecution();
        execution.setStatus(PipelineExecutionStatus.APPROVED);
        when(pipelineExecutionRepository.findById(executionId.get())).thenReturn(execution);
        
        McpLifecycleVersionSummary published = service.publishLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, published.getStatus());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
    }
    
    @Test
    void testStandardPipelineRejectionRequiresRedraft() throws Exception {
        AtomicReference<PipelineCallback> callback = new AtomicReference<>();
        AtomicReference<String> executionId = new AtomicReference<>();
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.MCP))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(ResourceFilesPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenAnswer(invocation -> {
                callback.set(invocation.getArgument(1));
                executionId.set(invocation.getArgument(2));
                return executionId.get();
            });
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        service.submitLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId(executionId.get());
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(Collections.emptyList());
        callback.get().onComplete(result);
        PipelineExecution execution = new PipelineExecution();
        execution.setStatus(PipelineExecutionStatus.REJECTED);
        when(pipelineExecutionRepository.findById(executionId.get())).thenReturn(execution);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_REVIEWED,
            versions.get(VERSION_ONE).getStatus());
        assertThrows(NacosException.class, () -> service.publishLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE));
        McpLifecycleVersionSummary redrafted = service.redraftLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        assertEquals(AiResourceConstants.VERSION_STATUS_DRAFT, redrafted.getStatus());
        assertNull(manifest.get());
    }
    
    @Test
    void testStandardSubmitFallsThroughWhenPipelineDisappears() throws Exception {
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.MCP))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(ResourceFilesPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenReturn(null);
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        
        McpLifecycleVersionSummary summary = service.submitLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, summary.getStatus());
        assertNull(versions.get(VERSION_ONE).getPublishPipelineInfo());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
    }
    
    @Test
    void testStandardSubmitClearsStalePipelineInfoWithoutPipeline() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        versions.get(VERSION_ONE).setPublishPipelineInfo("{}");
        
        McpLifecycleVersionSummary summary = service.submitLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, summary.getStatus());
        assertNull(versions.get(VERSION_ONE).getPublishPipelineInfo());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
    }
    
    @Test
    void testStandardPublishRetryConvergesManifestWithoutDoubleCounting() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        versions.get(VERSION_ONE).setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        ResourceVersionInfo info = versionInfo(resource.get());
        info.setEditingVersion(null);
        info.setReviewingVersion(VERSION_ONE);
        resource.get().setVersionInfo(JacksonUtils.toJson(info));
        failNextManifestPublish.set(true);
        
        assertThrows(NacosException.class, () -> service.publishLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE));
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE,
            versions.get(VERSION_ONE).getStatus());
        
        McpLifecycleVersionSummary retried = service.publishLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, retried.getStatus());
        assertEquals(1, versionInfo(resource.get()).getOnlineCnt());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
    }
    
    @Test
    void testStandardOnlineAndOfflineRetriesConvergeServingProjection() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        service.submitLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        service.offlineLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        service.offlineLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        assertNull(manifest.get());
        failNextManifestPublish.set(true);
        
        assertThrows(NacosException.class, () -> service.onlineLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE));
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE,
            versions.get(VERSION_ONE).getStatus());
        
        service.onlineLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertEquals(1, versionInfo(resource.get()).getOnlineCnt());
    }
    
    @Test
    void testStandardForcePublishRejectsTerminalStateBeforeContentMutation() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        AiResourceVersion row = versions.get(VERSION_ONE);
        row.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        McpVersionStorageDescriptor descriptor =
            McpVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        String retained = JacksonUtils.toJson(contents.get(storageKey(descriptor)));
        
        assertThrows(NacosException.class, () -> service.forcePublishLifecycleVersion(
            NAMESPACE_ID, MCP_NAME, VERSION_ONE));
        
        assertEquals(retained, JacksonUtils.toJson(contents.get(storageKey(descriptor))));
        assertNull(manifest.get());
    }
    
    @Test
    void testStandardForcePublishDraftCompletesServingProjection() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        
        McpLifecycleVersionSummary summary = service.forcePublishLifecycleVersion(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, summary.getStatus());
        assertTrue(summary.getLatest());
        assertEquals(VERSION_ONE, manifest.get().getLatestPublishedVersion());
        assertNull(versionInfo(resource.get()).getEditingVersion());
    }
    
    @Test
    void testStandardDeleteDraftRejectsNonDraftRow() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        versions.get(VERSION_ONE).setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        
        assertThrows(NacosException.class, () -> service.deleteLifecycleDraft(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE));
        assertNotNull(versions.get(VERSION_ONE));
    }
    
    @Test
    void testStandardUpdateDraftRejectsChangedWorkingPointer() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        ResourceVersionInfo info = versionInfo(resource.get());
        info.setEditingVersion(VERSION_TWO);
        resource.get().setVersionInfo(JacksonUtils.toJson(info));
        
        assertThrows(NacosException.class, () -> service.updateLifecycleDraft(NAMESPACE_ID,
            server(VERSION_ONE, "changed"), null, null, null));
    }
    
    @Test
    void testStandardDeleteDraftRetriesStorageFailureBeforeClearingPointer() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        failNextStorageDelete.set(true);
        
        assertThrows(NacosException.class, () -> service.deleteLifecycleDraft(NAMESPACE_ID,
            MCP_NAME, VERSION_ONE));
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getEditingVersion());
        assertNotNull(versions.get(VERSION_ONE));
        assertFalse(contents.isEmpty());
        
        service.deleteLifecycleDraft(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        assertNull(versionInfo(resource.get()).getEditingVersion());
        assertNull(versions.get(VERSION_ONE));
        assertTrue(contents.isEmpty());
    }
    
    @Test
    void testStandardDeleteDraftRetryClearsPointerAfterVersionRowRemoved() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        McpVersionStorageDescriptor descriptor = McpVersionStorageDescriptorSerializer.deserialize(
            versions.get(VERSION_ONE).getStorage());
        contents.remove(storageKey(descriptor));
        versions.remove(VERSION_ONE);
        
        service.deleteLifecycleDraft(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        
        assertNull(versionInfo(resource.get()).getEditingVersion());
        assertNull(versions.get(VERSION_ONE));
        assertTrue(contents.isEmpty());
    }
    
    @Test
    void testStandardRedraftIsIdempotentAfterStatusTransition() throws Exception {
        service.createLifecycleDraft(NAMESPACE_ID, server(VERSION_ONE, "draft"), null, null,
            null);
        versions.get(VERSION_ONE).setStatus(AiResourceConstants.VERSION_STATUS_REVIEWED);
        ResourceVersionInfo info = versionInfo(resource.get());
        info.setEditingVersion(null);
        info.setReviewingVersion(VERSION_ONE);
        resource.get().setVersionInfo(JacksonUtils.toJson(info));
        
        service.redraftLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        service.redraftLifecycleVersion(NAMESPACE_ID, MCP_NAME, VERSION_ONE);
        
        assertEquals(AiResourceConstants.VERSION_STATUS_DRAFT,
            versions.get(VERSION_ONE).getStatus());
        assertEquals(VERSION_ONE, versionInfo(resource.get()).getEditingVersion());
        assertNull(versionInfo(resource.get()).getReviewingVersion());
    }
    
    private void setUpResourceRepository() {
        when(resourcePersistService.find(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> matchesResource(invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2)) ? resource.get() : null);
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            if (resource.get() != null) {
                throw new DuplicateKeyException("duplicate");
            }
            AiResource inserted = invocation.getArgument(0);
            inserted.setId(sequence.incrementAndGet());
            inserted.setMetaVersion(1L);
            resource.set(inserted);
            return inserted.getId();
        });
        when(resourcePersistService.updateMetaCas(anyString(), anyString(), anyString(), anyLong(),
            any(AiResource.class))).thenAnswer(invocation -> {
                AiResource current = resource.get();
                long expected = invocation.getArgument(3);
                if (current == null || current.getMetaVersion() != expected) {
                    return false;
                }
                AiResource update = invocation.getArgument(4);
                current.setStatus(update.getStatus());
                current.setDesc(update.getDesc());
                current.setBizTags(update.getBizTags());
                current.setExt(update.getExt());
                current.setVersionInfo(update.getVersionInfo());
                current.setMetaVersion(expected + 1);
                return true;
            });
        when(resourcePersistService.delete(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                if (failNextResourceDelete.compareAndSet(true, false)) {
                    throw new IllegalStateException("resource delete failed");
                }
                if (matchesResource(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2))) {
                    resource.set(null);
                    return 1;
                }
                return 0;
            });
        when(resourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenAnswer(invocation -> resourcePage(invocation.getArgument(0),
                invocation.getArgument(1)));
    }
    
    private void setUpVersionRepository() {
        when(versionPersistService.find(anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> versions.get(invocation.getArgument(3)));
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenAnswer(invocation -> {
            AiResourceVersion row = invocation.getArgument(0);
            if (versions.containsKey(row.getVersion())) {
                throw new DuplicateKeyException("duplicate");
            }
            row.setId(sequence.incrementAndGet());
            row.setGmtCreate(new Timestamp(sequence.incrementAndGet()));
            versions.put(row.getVersion(), row);
            return row.getId();
        });
        when(versionPersistService.updateStorageAndDesc(anyString(), anyString(), anyString(),
            anyString(), anyString(), any())).thenAnswer(invocation -> {
                AiResourceVersion row = versions.get(invocation.getArgument(3));
                if (row == null) {
                    return 0;
                }
                row.setStorage(invocation.getArgument(4));
                row.setDesc(invocation.getArgument(5));
                return 1;
            });
        when(versionPersistService.updateStatus(anyString(), anyString(), anyString(), anyString(),
            anyString())).thenAnswer(invocation -> {
                AiResourceVersion row = versions.get(invocation.getArgument(3));
                if (row == null) {
                    return 0;
                }
                row.setStatus(invocation.getArgument(4));
                return 1;
            });
        when(versionPersistService.updatePublishPipelineInfo(anyString(), anyString(), anyString(),
            anyString(), any())).thenAnswer(invocation -> {
                AiResourceVersion row = versions.get(invocation.getArgument(3));
                if (row == null) {
                    return 0;
                }
                row.setPublishPipelineInfo(invocation.getArgument(4));
                return 1;
            });
        when(versionPersistService.list(anyString(), anyString(), anyString(), any(), anyInt(),
            anyInt())).thenAnswer(
                invocation -> versionPage(invocation.getArgument(3),
                    invocation.getArgument(4)));
        when(versionPersistService.delete(anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> versions.remove(invocation.getArgument(3)) == null ? 0 : 1);
        when(versionPersistService.deleteByNameAndType(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                int size = versions.size();
                versions.clear();
                return size;
            });
    }
    
    private void setUpContentStorage() throws Exception {
        doAnswer(invocation -> {
            McpVersionStorageDescriptor descriptor = invocation.getArgument(0);
            McpVersionStorageContents value = invocation.getArgument(1);
            contents.put(storageKey(descriptor), value);
            return null;
        }).when(versionStorageService).save(any(McpVersionStorageDescriptor.class),
            any(McpVersionStorageContents.class));
        when(versionStorageService.load(any(McpVersionStorageDescriptor.class)))
            .thenAnswer(invocation -> {
                McpVersionStorageContents result =
                    contents.get(storageKey(invocation.getArgument(0)));
                if (result == null) {
                    throw new NacosException(NacosException.SERVER_ERROR, "content missing");
                }
                return result;
            });
        when(versionStorageService.loadIfPresent(any(McpVersionStorageDescriptor.class)))
            .thenAnswer(invocation -> contents.get(storageKey(invocation.getArgument(0))));
        doAnswer(invocation -> {
            if (failNextStorageDelete.compareAndSet(true, false)) {
                throw new NacosException(NacosException.SERVER_ERROR, "delete failed");
            }
            contents.remove(storageKey(invocation.getArgument(0)));
            return null;
        }).when(versionStorageService).delete(any(McpVersionStorageDescriptor.class));
    }
    
    private void setUpManifestStorage() throws Exception {
        when(manifestStorage.get(anyString(), anyString()))
            .thenAnswer(invocation -> {
                McpServerVersionInfo current = manifest.get();
                if (current == null || !returnStaleNextManifestRead.compareAndSet(true, false)) {
                    return current;
                }
                McpServerVersionInfo stale = JacksonUtils.toObj(JacksonUtils.toJson(current),
                    McpServerVersionInfo.class);
                stale.setDescription("stale");
                return stale;
            });
        doAnswer(invocation -> {
            if (failNextManifestPublish.compareAndSet(true, false)) {
                throw new NacosException(NacosException.SERVER_ERROR, "publish failed");
            }
            manifest.set(invocation.getArgument(1));
            return null;
        }).when(manifestStorage).publish(anyString(), any(McpServerVersionInfo.class));
        doAnswer(invocation -> {
            manifest.set(null);
            return null;
        }).when(manifestStorage).delete(anyString(), anyString());
    }
    
    private boolean matchesResource(String namespaceId, String name, String type) {
        AiResource current = resource.get();
        return current != null && namespaceId.equals(current.getNamespaceId())
            && name.equals(current.getName()) && type.equals(current.getType());
    }
    
    private Page<AiResource> resourcePage(QueryCondition condition, int pageNo) {
        AiResource current = resource.get();
        if (current == null || !condition.getNamespaceId().equals(current.getNamespaceId())
            || !condition.getType().equals(current.getType()) || !matchesName(condition, current)) {
            return page(pageNo, Collections.emptyList());
        }
        return page(pageNo, Collections.singletonList(current));
    }
    
    private boolean matchesName(QueryCondition condition, AiResource current) {
        Object exact = condition.getOrGroup().get("name");
        if (exact != null && !exact.equals(current.getName())) {
            return false;
        }
        String nameLike = condition.getNameLike();
        if (nameLike == null) {
            return true;
        }
        return current.getName().contains(nameLike.replace("%", ""));
    }
    
    private Page<AiResourceVersion> versionPage(String status, int pageNo) {
        List<AiResourceVersion> rows = new ArrayList<>();
        for (AiResourceVersion row : versions.values()) {
            if (status == null || status.equalsIgnoreCase(row.getStatus())) {
                rows.add(row);
            }
        }
        return page(pageNo, rows);
    }
    
    private <T> Page<T> page(int pageNo, List<T> items) {
        Page<T> result = new Page<>();
        result.setPageNumber(pageNo);
        result.setTotalCount(items.size());
        result.setPagesAvailable(items.isEmpty() ? 0 : 1);
        result.setPageItems(items);
        return result;
    }
    
    private McpServerBasicInfo server(String version, String description) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setNamespaceId(NAMESPACE_ID);
        result.setId(MCP_ID);
        result.setName(MCP_NAME);
        result.setDescription(description);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setEnabled(true);
        ServerVersionDetail detail = new ServerVersionDetail();
        detail.setVersion(version);
        result.setVersionDetail(detail);
        result.setVersion(version);
        return result;
    }
    
    private McpResourceSpecification resourceSpecification() {
        McpResourceSpecification result = new McpResourceSpecification();
        result.getResources().add(Collections.singletonMap("name", "weather"));
        return result;
    }
    
    private McpResourceExt resourceExt() {
        McpResourceExt result = new McpResourceExt();
        result.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        result.setMcpId(MCP_ID);
        return result;
    }
    
    private String latest(AiResource value) {
        ResourceVersionInfo info = versionInfo(value);
        return info == null || info.getLabels() == null ? null
            : info.getLabels().get(AiResourceConstants.LABEL_LATEST);
    }
    
    private ResourceVersionInfo versionInfo(AiResource value) {
        return AiResourceManager.parseVersionInfo(value.getVersionInfo());
    }
    
    private String storageKey(McpVersionStorageDescriptor descriptor) {
        return McpVersionStorageDescriptorSerializer.serialize(descriptor);
    }
}
