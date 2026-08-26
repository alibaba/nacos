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
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService.CutoverStatus;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService.Marker;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpLifecycleManagementStateServiceTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private AiResourceSearchReadinessService searchReadinessService;
    
    @Mock
    private ObjectProvider<AiResourceSearchReadinessService> searchReadinessServiceProvider;
    
    private McpLifecycleManagementStateService service;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        service = new McpLifecycleManagementStateService(configQueryChainService,
            configOperationService, serverMemberManager, searchReadinessService);
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void resolveModeShouldCacheAbsentMarkerTemporarily() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        
        assertEquals(McpCompatibilityMode.SYNCING, service.resolveMode());
        assertEquals(McpCompatibilityMode.SYNCING, service.resolveMode());
        verify(configQueryChainService).handle(any(ConfigQueryChainRequest.class));
    }
    
    @Test
    void resolveModeShouldLatchValidPermanentMarker() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(found(validMarkerJson()));
        
        assertEquals(McpCompatibilityMode.LIFECYCLE_MANAGED, service.resolveMode());
        assertEquals(McpCompatibilityMode.LIFECYCLE_MANAGED, service.resolveMode());
        verify(configQueryChainService).handle(any(ConfigQueryChainRequest.class));
    }
    
    @Test
    void publicConstructorAndManagedShortcutShouldUseDurableMarker() {
        when(searchReadinessServiceProvider.getIfAvailable(any()))
            .thenReturn(searchReadinessService);
        service = new McpLifecycleManagementStateService(configQueryChainService,
            configOperationService, serverMemberManager, searchReadinessServiceProvider);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(found(validMarkerJson()));
        
        assertEquals(McpCompatibilityMode.LIFECYCLE_MANAGED, service.resolveMode());
        assertTrue(service.tryCompleteCutover(false).isManaged());
        verifyNoInteractions(serverMemberManager, searchReadinessService, configOperationService);
    }
    
    @Test
    void resolveModeShouldRejectUnavailableOrInvalidMarkers() {
        assertRejectedMarker(null);
        assertRejectedMarker(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT, "conflict"));
        assertRejectedMarker(found(" "));
        assertRejectedMarker(found("not-json"));
        assertRejectedMarker(found(markerJson(2,
            McpLifecycleManagementStateService.LIFECYCLE_MANAGED_STATE, 1L)));
        assertRejectedMarker(found(markerJson(1, "SYNCING", 1L)));
        assertRejectedMarker(found(markerJson(1,
            McpLifecycleManagementStateService.LIFECYCLE_MANAGED_STATE, 0L)));
    }
    
    @Test
    void tryCompleteCutoverShouldPublishPermanentMarkerOnlyAfterAllGates() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        when(serverMemberManager.allMembers()).thenReturn(Arrays.asList(supportedMember(),
            supportedMember()));
        when(searchReadinessService.isReady(AiResourceConstants.RESOURCE_TYPE_MCP, 2))
            .thenReturn(true);
        when(configOperationService.publishConfig(any(ConfigForm.class),
            any(ConfigRequestInfo.class), isNull())).thenReturn(true);
        
        CutoverStatus status = service.tryCompleteCutover(true);
        
        assertTrue(status.isManaged());
        assertTrue(status.isMembersReady());
        assertTrue(status.isSearchReady());
        assertEquals(McpCompatibilityMode.LIFECYCLE_MANAGED, service.resolveMode());
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        ArgumentCaptor<ConfigRequestInfo> requestCaptor =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService).publishConfig(formCaptor.capture(),
            requestCaptor.capture(), isNull());
        ConfigForm form = formCaptor.getValue();
        assertEquals(McpLifecycleManagementStateService.MIGRATION_MARKER_DATA_ID,
            form.getDataId());
        assertEquals("nacos_internal", form.getGroup());
        assertEquals("json", form.getType());
        assertFalse(requestCaptor.getValue().getUpdateForExist());
        Marker marker = JacksonUtils.toObj(form.getContent(), Marker.class);
        assertEquals(1, marker.getSchemaVersion());
        assertEquals(McpLifecycleManagementStateService.LIFECYCLE_MANAGED_STATE,
            marker.getState());
        assertTrue(marker.getCompletedAt() > 0);
    }
    
    @Test
    void tryCompleteCutoverShouldKeepSyncingWhenAnyGateIsClosed() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        Member unsupported = new Member();
        when(serverMemberManager.allMembers()).thenReturn(Collections.singletonList(unsupported),
            Collections.singletonList(supportedMember()),
            Collections.singletonList(supportedMember()));
        when(searchReadinessService.isReady(AiResourceConstants.RESOURCE_TYPE_MCP, 2))
            .thenReturn(false, false, true);
        
        CutoverStatus unsupportedMember = service.tryCompleteCutover(true);
        CutoverStatus searchPending = service.tryCompleteCutover(true);
        CutoverStatus differencesRemain = service.tryCompleteCutover(false);
        
        assertFalse(unsupportedMember.isMembersReady());
        assertFalse(unsupportedMember.isManaged());
        assertTrue(searchPending.isMembersReady());
        assertFalse(searchPending.isSearchReady());
        assertFalse(differencesRemain.isManaged());
        verifyNoInteractions(configOperationService);
    }
    
    @Test
    void tryCompleteCutoverShouldNotRequireReadinessWhenSearchIsDisabled() throws Exception {
        System.setProperty(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "false");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        when(serverMemberManager.allMembers())
            .thenReturn(Collections.singletonList(supportedMember()));
        when(configOperationService.publishConfig(any(ConfigForm.class),
            any(ConfigRequestInfo.class), isNull())).thenReturn(true);
        
        assertTrue(service.tryCompleteCutover(true).isManaged());
        verifyNoInteractions(searchReadinessService);
    }
    
    @Test
    void concurrentMarkerPublisherShouldBeObservedBeforeManagedCutover() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound(), found(validMarkerJson()));
        when(serverMemberManager.allMembers())
            .thenReturn(Collections.singletonList(supportedMember()));
        when(searchReadinessService.isReady(AiResourceConstants.RESOURCE_TYPE_MCP, 2))
            .thenReturn(true);
        doThrow(new ConfigAlreadyExistsException("marker exists"))
            .when(configOperationService).publishConfig(any(ConfigForm.class),
                any(ConfigRequestInfo.class), isNull());
        
        assertTrue(service.tryCompleteCutover(true).isManaged());
        verify(configQueryChainService, times(2)).handle(any(ConfigQueryChainRequest.class));
    }
    
    @Test
    void failedMarkerPublishShouldRemainRetryable() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        when(serverMemberManager.allMembers())
            .thenReturn(Collections.singletonList(supportedMember()));
        when(searchReadinessService.isReady(AiResourceConstants.RESOURCE_TYPE_MCP, 2))
            .thenReturn(true);
        when(configOperationService.publishConfig(any(ConfigForm.class),
            any(ConfigRequestInfo.class), isNull())).thenReturn(false);
        
        assertFalse(service.tryCompleteCutover(true).isManaged());
        
        reset(configOperationService);
        doThrow(new IllegalStateException("unavailable")).when(configOperationService)
            .publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), isNull());
        assertFalse(service.tryCompleteCutover(true).isManaged());
    }
    
    @Test
    void capabilityAndReadinessInspectionFailuresShouldFailClosed() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        when(serverMemberManager.allMembers()).thenThrow(new IllegalStateException("members"));
        when(searchReadinessService.isReady(AiResourceConstants.RESOURCE_TYPE_MCP, 2))
            .thenThrow(new IllegalStateException("search"));
        
        CutoverStatus status = service.tryCompleteCutover(true);
        
        assertFalse(status.isMembersReady());
        assertFalse(status.isSearchReady());
        verifyNoInteractions(configOperationService);
    }
    
    @Test
    void localCapabilityShouldFailClosed() {
        Member supported = supportedMember();
        when(serverMemberManager.getSelf()).thenReturn(null, supported);
        
        assertFalse(service.localMemberSupportsManagedLifecycle());
        assertTrue(service.localMemberSupportsManagedLifecycle());
        
        when(serverMemberManager.getSelf()).thenThrow(new IllegalStateException("members"));
        assertFalse(service.localMemberSupportsManagedLifecycle());
    }
    
    private void assertRejectedMarker(ConfigQueryChainResponse response) {
        reset(configQueryChainService);
        service = new McpLifecycleManagementStateService(configQueryChainService,
            configOperationService, serverMemberManager, searchReadinessService);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        assertEquals(McpCompatibilityMode.SYNCING, service.resolveMode());
    }
    
    private Member supportedMember() {
        Member result = new Member();
        result.setExtendVal(MemberMetaDataConstants.SUPPORT_MCP_LIFECYCLE_MANAGEMENT, true);
        return result;
    }
    
    private String validMarkerJson() {
        return markerJson(1, McpLifecycleManagementStateService.LIFECYCLE_MANAGED_STATE, 1L);
    }
    
    private String markerJson(int schemaVersion, String state, long completedAt) {
        Marker marker = new Marker();
        marker.setSchemaVersion(schemaVersion);
        marker.setState(state);
        marker.setCompletedAt(completedAt);
        return JacksonUtils.toJson(marker);
    }
    
    private ConfigQueryChainResponse notFound() {
        return response(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND, null);
    }
    
    private ConfigQueryChainResponse found(String content) {
        return response(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL, content);
    }
    
    private ConfigQueryChainResponse response(
        ConfigQueryChainResponse.ConfigQueryStatus status, String content) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(status);
        result.setContent(content);
        return result;
    }
}
