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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeEndpointMapper;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class A2aServerOperationServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "research-agent";
    
    private static final String VERSION = "1.0.0";
    
    private static final String SECOND_VERSION = "2.0.0";
    
    @Mock
    private AgentOperationService agentOperationService;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    private A2aServerOperationService service;
    
    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service =
            new A2aServerOperationService(agentOperationService, serviceStorage, directExecutor);
    }
    
    @Test
    void testPublicConstructor() {
        assertNotNull(new A2aServerOperationService(agentOperationService, serviceStorage));
    }
    
    @Test
    void testRegisterMapsNormalizedCardToCanonicalAgentRequest() throws NacosException {
        AgentCard card = card(VERSION);
        AgentInterface duplicate = interfaceOf("https://example.com/a2a", "HTTP+JSON", "0.3");
        card.setSupportedInterfaces(Arrays.asList(card.getSupportedInterfaces().get(0), duplicate));
        ArgumentCaptor<AgentDraftCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(AgentDraftCreateRequest.class);
        
        service.registerAgent(card, NAMESPACE_ID, null);
        
        verify(agentOperationService).registerLegacyOnlineVersion(eq(NAMESPACE_ID),
            requestCaptor.capture());
        AgentDraftCreateRequest request = requestCaptor.getValue();
        assertEquals(AGENT_NAME, request.getAgentName());
        assertEquals(VERSION, request.getVersion());
        assertNull(request.getDisplayName());
        assertEquals("Research", request.getDescription());
        assertEquals("https://example.com/icon.png", request.getIconUrl());
        assertEquals("Example Org", request.getProvider().getName());
        assertNull(request.getTags());
        AgentCallInterface callInterface = request.getCallInterfaces().get(0);
        assertEquals("a2a", callInterface.getProtocol());
        assertEquals("0.3", callInterface.getProtocolVersion());
        assertEquals("application/json", callInterface.getDescriptorMediaType());
        assertInstanceOf(Map.class, callInterface.getNativeDescriptor());
        assertEquals(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME),
            callInterface.getEndpointSourceOrder());
        assertEquals(1, callInterface.getDeclaredEndpoints().size());
        assertNull(card.getAdditionalInterfaces());
    }
    
    @Test
    void testRegisterMapsAbsentProvider() throws NacosException {
        AgentCard card = card(VERSION);
        card.setProvider(null);
        ArgumentCaptor<AgentDraftCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(AgentDraftCreateRequest.class);
        
        service.registerAgent(card, NAMESPACE_ID, null);
        
        verify(agentOperationService).registerLegacyOnlineVersion(eq(NAMESPACE_ID),
            requestCaptor.capture());
        assertNull(requestCaptor.getValue().getProvider());
    }
    
    @Test
    void testReleaseDefaultsToServiceAndPreservesSetAsLatest() throws NacosException {
        ArgumentCaptor<AgentDraftCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(AgentDraftCreateRequest.class);
        
        service.releaseAgent(card(VERSION), NAMESPACE_ID, "", true);
        
        verify(agentOperationService).releaseLegacyOnlineVersion(eq(NAMESPACE_ID),
            requestCaptor.capture(), eq(true));
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            requestCaptor.getValue().getCallInterfaces().get(0).getEndpointSourceOrder());
    }
    
    @Test
    void testUpdateInheritsExactAndLatestRegistrationTypes() throws NacosException {
        Agent agent = agent(SECOND_VERSION, true, VERSION, SECOND_VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail(VERSION, true));
        ArgumentCaptor<AgentDraftCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(AgentDraftCreateRequest.class);
        
        service.updateAgentCard(card(VERSION), NAMESPACE_ID, null, false);
        
        verify(agentOperationService).updateLegacyOnlineVersion(eq(NAMESPACE_ID),
            requestCaptor.capture(), eq(false));
        assertEquals(EndpointSource.RUNTIME,
            requestCaptor.getValue().getCallInterfaces().get(0).getEndpointSourceOrder().get(0));
        
        NacosApiException missing = new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.AGENT_VERSION_NOT_FOUND, "missing");
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(missing);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, SECOND_VERSION))
            .thenReturn(versionDetail(SECOND_VERSION, false));
        service.updateAgentCard(card(VERSION), NAMESPACE_ID, "", true);
        
        verify(agentOperationService).updateLegacyOnlineVersion(eq(NAMESPACE_ID),
            requestCaptor.capture(), eq(true));
        assertEquals(EndpointSource.DECLARED,
            requestCaptor.getValue().getCallInterfaces().get(0).getEndpointSourceOrder().get(0));
    }
    
    @Test
    void testUpdateWithoutInheritableVersionDefaultsToUrl() throws NacosException {
        Agent agent = agent(null, false);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.AGENT_VERSION_NOT_FOUND, "missing"));
        ArgumentCaptor<AgentDraftCreateRequest> requestCaptor =
            ArgumentCaptor.forClass(AgentDraftCreateRequest.class);
        
        service.updateAgentCard(card(VERSION), NAMESPACE_ID, null, false);
        
        verify(agentOperationService).updateLegacyOnlineVersion(eq(NAMESPACE_ID),
            requestCaptor.capture(), eq(false));
        assertEquals(EndpointSource.DECLARED,
            requestCaptor.getValue().getCallInterfaces().get(0).getEndpointSourceOrder().get(0));
    }
    
    @Test
    void testUpdatePropagatesNonMissingVersionLookupFailure() throws NacosException {
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(new NacosApiException(NacosException.NO_RIGHT,
                ErrorCode.ACCESS_DENIED, "forbidden"));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.updateAgentCard(card(VERSION), NAMESPACE_ID, null, false));
        
        assertEquals(ErrorCode.ACCESS_DENIED.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void testWriteValidationAndDeleteRouting() throws NacosException {
        assertThrows(IllegalArgumentException.class,
            () -> service.registerAgent(null, NAMESPACE_ID, "URL"));
        NacosApiException invalid = assertThrows(NacosApiException.class,
            () -> service.registerAgent(card(VERSION), NAMESPACE_ID, "OTHER"));
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), invalid.getDetailErrCode());
        assertThrows(NacosApiException.class,
            () -> service.updateAgentCard(card(VERSION), NAMESPACE_ID, "OTHER", false));
        
        service.deleteAgent(NAMESPACE_ID, AGENT_NAME, null);
        service.deleteAgent(NAMESPACE_ID, AGENT_NAME, "");
        service.deleteAgent(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        verify(agentOperationService, org.mockito.Mockito.times(2))
            .deleteLegacyAgentIfPresent(NAMESPACE_ID, AGENT_NAME);
        verify(agentOperationService).deleteLegacyVersionIfPresent(NAMESPACE_ID, AGENT_NAME,
            VERSION);
    }
    
    @Test
    void testManagementAndClientReadProjectLatestUrlCard() throws NacosException {
        Agent agent = agent(VERSION, true, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail(VERSION, false));
        
        AgentCardDetailInfo management =
            service.getAgentCard(NAMESPACE_ID, AGENT_NAME, null, null);
        AgentCardDetailInfo client =
            service.getAgentCardForClient(NAMESPACE_ID, AGENT_NAME, "", "url");
        
        assertEquals(AGENT_NAME, management.getName());
        assertEquals(VERSION, management.getVersion());
        assertEquals("URL", management.getRegistrationType());
        assertEquals(Boolean.TRUE, management.isLatestVersion());
        assertEquals("URL", client.getRegistrationType());
        verify(serviceStorage, never()).getData(any(Service.class));
    }
    
    @Test
    void testClientReadHidesDisabledAgentButManagementCanReadIt() throws NacosException {
        Agent agent = agent(VERSION, true, VERSION);
        agent.setStatus(AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail(VERSION, false));
        
        assertEquals(VERSION,
            service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null).getVersion());
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.getAgentCardForClient(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void testLegacyReadsMapCanonicalAgentNotFound() throws NacosException {
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME))
            .thenThrow(new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND, "missing"));
        
        NacosApiException queryException = assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        NacosApiException listException = assertThrows(NacosApiException.class,
            () -> service.listAgentVersions(NAMESPACE_ID, AGENT_NAME));
        
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), queryException.getDetailErrCode());
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), listException.getDetailErrCode());
    }
    
    @Test
    void testLegacyReadPropagatesCanonicalAgentLookupFailure() throws NacosException {
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME))
            .thenThrow(new NacosApiException(NacosException.NO_RIGHT,
                ErrorCode.ACCESS_DENIED, "forbidden"));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        
        assertEquals(ErrorCode.ACCESS_DENIED.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void testReadRejectsMissingOrNonProjectableVersion() throws NacosException {
        Agent noA2a = agent(VERSION, false, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(noA2a);
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null))
            .getDetailErrCode());
        
        Agent a2a = agent(VERSION, true, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(a2a);
        assertEquals(ErrorCode.AGENT_VERSION_NOT_FOUND.getCode(),
            assertThrows(NacosApiException.class,
                () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, SECOND_VERSION, null))
                .getDetailErrCode());
        
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetailWithStatus(VERSION,
                AiConstants.Agent.VERSION_STATUS_OFFLINE, true));
        assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetailWithStatus(VERSION,
                AiConstants.Agent.VERSION_STATUS_ONLINE, false));
        assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
    }
    
    @Test
    void testReadMapsVersionStorageAndDescriptorFailuresToLegacyNotFound()
        throws NacosException {
        Agent agent = agent(VERSION, true, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        NacosApiException missing = new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.AGENT_VERSION_NOT_FOUND, "missing");
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(missing);
        assertEquals(ErrorCode.AGENT_VERSION_NOT_FOUND.getCode(),
            assertThrows(NacosApiException.class,
                () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null))
                .getDetailErrCode());
        
        NacosApiException forbidden = new NacosApiException(NacosException.NO_RIGHT,
            ErrorCode.ACCESS_DENIED, "forbidden");
        doThrow(forbidden).when(agentOperationService).getVersion(NAMESPACE_ID, AGENT_NAME,
            VERSION);
        assertEquals(forbidden, assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null)));
        
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail invalid =
            versionDetail(VERSION, false);
        invalid.getCallInterfaces().get(0).setNativeDescriptor(Collections.emptyMap());
        doReturn(invalid).when(agentOperationService).getVersion(NAMESPACE_ID, AGENT_NAME,
            VERSION);
        assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        invalid.getCallInterfaces().get(0).setNativeDescriptor("not-an-object");
        assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
        
        invalid.getCallInterfaces().get(0).setNativeDescriptor(
            JacksonUtils.toObj(JacksonUtils.toJson(card(SECOND_VERSION)), Map.class));
        assertThrows(NacosApiException.class,
            () -> service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, null));
    }
    
    @Test
    void testServiceProjectionFiltersAndStablyOrdersRuntimeEndpoints()
        throws NacosException {
        Agent agent = agent(VERSION, true, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail(VERSION, true));
        ServiceInfo serviceInfo = new ServiceInfo();
        Instance disabled = instance("10.0.0.9", 9000, "HTTP+JSON", null, null, true);
        disabled.setEnabled(false);
        Instance grpc = instance("10.0.0.1", 8001, "GRPC", null, null, false);
        grpc.setMetadata(new PriorityReadMetadata(grpc.getMetadata(), ""));
        Instance websocket = instance("10.0.0.2", 8002, "WEBSOCKET", "0.2", null, true);
        Instance json = instance("10.0.0.3", 8003, "HTTP+JSON", "0.3", "5", true);
        Instance invalidPriority =
            instance("10.0.0.5", 8005, "SSE", "0.3", "1", true);
        invalidPriority.setMetadata(
            new PriorityReadMetadata(invalidPriority.getMetadata(), "invalid"));
        Instance otherVersion = instance(SECOND_VERSION, "10.0.0.4", 8004, "HTTP+JSON",
            "0.4", "1", true);
        serviceInfo.setHosts(Arrays.asList(disabled, json, websocket, grpc, invalidPriority,
            otherVersion));
        when(serviceStorage.getData(any(Service.class))).thenReturn(serviceInfo);
        
        AgentCardDetailInfo result =
            service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, "service");
        
        assertEquals("SERVICE", result.getRegistrationType());
        assertEquals(4, result.getSupportedInterfaces().size());
        assertEquals("HTTP+JSON", result.getPreferredTransport());
        assertTrue(result.getUrl().contains("10.0.0.3:8003"));
        assertEquals(4, result.getAdditionalInterfaces().size());
        AgentInterface grpcInterface = result.getSupportedInterfaces().stream()
            .filter(each -> "GRPC".equals(each.getProtocolBinding())).findFirst().orElseThrow();
        assertEquals("0.3", grpcInterface.getProtocolVersion());
        assertFalse(grpc.isHealthy());
        ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);
        verify(serviceStorage).getData(serviceCaptor.capture());
        assertEquals("rad-research-agent-a2a", serviceCaptor.getValue().getName());
    }
    
    @Test
    void testServiceProjectionFallsBackToDeclaredEndpoints() throws NacosException {
        Agent agent = agent(VERSION, true, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail(VERSION, true));
        when(serviceStorage.getData(any(Service.class))).thenReturn(null);
        AgentCardDetailInfo noService =
            service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, "SERVICE");
        assertEquals("https://example.com/a2a", noService.getUrl());
        
        ServiceInfo disabledOnly = new ServiceInfo();
        Instance disabled = instance("10.0.0.9", 9000, "GRPC", "0.3", "1", true);
        disabled.setEnabled(false);
        disabledOnly.setHosts(Collections.singletonList(disabled));
        when(serviceStorage.getData(any(Service.class))).thenReturn(disabledOnly);
        assertEquals("https://example.com/a2a",
            service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, "SERVICE").getUrl());
    }
    
    @Test
    void testServiceProjectionUsesFirstRuntimeWhenPreferredTransportIsAbsent()
        throws NacosException {
        Agent agent = agent(VERSION, true, VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionDetail(VERSION, true));
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.singletonList(
            instance("10.0.0.1", 8001, "GRPC", "0.3", "1", true)));
        when(serviceStorage.getData(any(Service.class))).thenReturn(serviceInfo);
        
        AgentCardDetailInfo result =
            service.getAgentCard(NAMESPACE_ID, AGENT_NAME, VERSION, "SERVICE");
        
        assertEquals("GRPC", result.getPreferredTransport());
        assertEquals(1, result.getAdditionalInterfaces().size());
        assertEquals(result.getUrl(), result.getAdditionalInterfaces().get(0).getUrl());
    }
    
    @Test
    void testListFiltersBeforePagingAndProjectsOnlySelectedPage() throws NacosException {
        AgentSummary eligible = summary(AGENT_NAME, SECOND_VERSION, true, VERSION,
            SECOND_VERSION);
        AgentSummary ineligible = summary("other-agent", VERSION, false, VERSION);
        AgentSummary missingCatalog = new AgentSummary();
        missingCatalog.setAgentName("missing-catalog");
        when(agentOperationService.listAgents(eq(NAMESPACE_ID), any(), any(), any(), any(), any(),
            eq(1), eq(100))).thenReturn(page(Arrays.asList(ineligible, missingCatalog, eligible)));
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, SECOND_VERSION))
            .thenReturn(versionDetail(SECOND_VERSION, false));
        AgentVersionSummary first = versionSummary(VERSION, 1000L, 2000L);
        AgentVersionSummary second = versionSummary(SECOND_VERSION, 3000L, 4000L);
        AgentVersionSummary unrelated = versionSummary("3.0.0", 5000L, 6000L);
        when(agentOperationService.listVersions(NAMESPACE_ID, AGENT_NAME,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100))
            .thenReturn(page(Arrays.asList(first, second, unrelated)));
        
        Page<AgentCardVersionInfo> result = service.listAgents(NAMESPACE_ID, "",
            Constants.A2A.SEARCH_BLUR, 1, 10);
        
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPagesAvailable());
        assertEquals(SECOND_VERSION, result.getPageItems().get(0).getVersion());
        assertEquals(2, result.getPageItems().get(0).getVersionDetails().size());
        assertTrue(result.getPageItems().get(0).getVersionDetails().get(1).isLatest());
        assertEquals("1970-01-01T00:00:03Z",
            result.getPageItems().get(0).getVersionDetails().get(1).getCreatedAt());
    }
    
    @Test
    void testListAccurateSearchAndMultiPageScan() throws NacosException {
        List<AgentSummary> firstPage = new ArrayList<AgentSummary>();
        for (int i = 0; i < 100; i++) {
            firstPage.add(summary("other-" + i, VERSION, false, VERSION));
        }
        when(agentOperationService.listAgents(eq(NAMESPACE_ID), eq(AGENT_NAME), any(), any(),
            any(), any(), anyInt(), eq(100))).thenReturn(page(firstPage), null);
        
        Page<AgentCardVersionInfo> result = service.listAgents(NAMESPACE_ID, AGENT_NAME,
            "AcCuRaTe", 1, 10);
        
        assertEquals(0, result.getTotalCount());
        assertTrue(result.getPageItems().isEmpty());
        verify(agentOperationService).listAgents(NAMESPACE_ID, AGENT_NAME, null, null, null, null,
            2, 100);
    }
    
    @Test
    void testListProjectionPropagatesCheckedAndUnexpectedFailures() throws NacosException {
        AgentSummary eligible = summary(AGENT_NAME, VERSION, true, VERSION);
        when(agentOperationService.listAgents(eq(NAMESPACE_ID), any(), any(), any(), any(), any(),
            eq(1), eq(100))).thenReturn(page(Collections.singletonList(eligible)));
        when(agentOperationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class, () -> service.listAgents(NAMESPACE_ID, null,
                Constants.A2A.SEARCH_BLUR, 1, 10)).getErrCode());
        
        doThrow(new IllegalStateException("unexpected")).when(agentOperationService)
            .getVersion(NAMESPACE_ID, AGENT_NAME, VERSION);
        assertThrows(CompletionException.class,
            () -> service.listAgents(NAMESPACE_ID, null, Constants.A2A.SEARCH_BLUR, 1, 10));
    }
    
    @Test
    void testListAgentVersionsScansAllRowsAndKeepsOnlyCataloguedA2aVersions()
        throws NacosException {
        Agent agent = agent(SECOND_VERSION, true, VERSION, SECOND_VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        List<AgentVersionSummary> firstPage = new ArrayList<AgentVersionSummary>();
        firstPage.add(versionSummary(VERSION, 1000L, 2000L));
        firstPage.add(versionSummary(SECOND_VERSION, 3000L, 4000L));
        for (int i = 2; i < 100; i++) {
            firstPage.add(versionSummary("ignored-" + i, 5000L, 6000L));
        }
        when(agentOperationService.listVersions(NAMESPACE_ID, AGENT_NAME,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100)).thenReturn(page(firstPage));
        when(agentOperationService.listVersions(NAMESPACE_ID, AGENT_NAME,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 2, 100))
            .thenReturn(page(Collections.emptyList()));
        
        List<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail> result =
            service.listAgentVersions(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(2, result.size());
        assertFalse(result.get(0).isLatest());
        assertTrue(result.get(1).isLatest());
    }
    
    @Test
    void testListAgentVersionsSkipsCataloguedVersionWithoutSummary() throws NacosException {
        Agent agent = agent(SECOND_VERSION, true, VERSION, SECOND_VERSION);
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        when(agentOperationService.listVersions(NAMESPACE_ID, AGENT_NAME,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100))
            .thenReturn(page(Collections.singletonList(
                versionSummary(SECOND_VERSION, 3000L, 4000L))));
        
        List<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail> result =
            service.listAgentVersions(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(1, result.size());
        assertEquals(SECOND_VERSION, result.get(0).getVersion());
    }
    
    @Test
    void testListAgentVersionsRejectsAgentWithoutA2aOnlineVersion()
        throws NacosException {
        when(agentOperationService.getAgent(NAMESPACE_ID, AGENT_NAME))
            .thenReturn(agent(VERSION, false, VERSION));
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.listAgentVersions(NAMESPACE_ID, AGENT_NAME));
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    private AgentCard card(String version) {
        AgentCard result = new AgentCard();
        result.setName(AGENT_NAME);
        result.setVersion(version);
        result.setDescription("Research");
        result.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setOrganization("Example Org");
        provider.setUrl("https://example.com");
        result.setProvider(provider);
        result.setSupportedInterfaces(Collections.singletonList(
            interfaceOf("https://example.com/a2a", "HTTP+JSON", "0.3")));
        return result;
    }
    
    private static final class PriorityReadMetadata extends HashMap<String, String> {
        
        private static final long serialVersionUID = -7775134594016802544L;
        
        private final String readValue;
        
        private PriorityReadMetadata(Map<String, String> source, String readValue) {
            super(source);
            this.readValue = readValue;
        }
        
        @Override
        public String get(Object key) {
            if (Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY.equals(key)) {
                return readValue;
            }
            return super.get(key);
        }
    }
    
    private AgentInterface interfaceOf(String url, String transport, String protocolVersion) {
        AgentInterface result = new AgentInterface();
        result.setUrl(url);
        result.setProtocolBinding(transport);
        result.setProtocolVersion(protocolVersion);
        return result;
    }
    
    private Agent agent(String latest, boolean a2a, String... versions) {
        Agent result = new Agent();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        result.setVersionCatalog(catalog(latest, a2a, versions));
        return result;
    }
    
    private AgentSummary summary(String name, String latest, boolean a2a, String... versions) {
        AgentSummary result = new AgentSummary();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(name);
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        result.setVersionCatalog(catalog(latest, a2a, versions));
        return result;
    }
    
    private AgentVersionCatalog catalog(String latest, boolean a2a, String... versions) {
        AgentVersionCatalog result = new AgentVersionCatalog();
        result.setLatestVersion(latest);
        List<AgentVersionCatalogEntry> entries = new ArrayList<AgentVersionCatalogEntry>();
        for (String version : versions) {
            AgentVersionCatalogEntry entry = new AgentVersionCatalogEntry();
            entry.setVersion(version);
            entry.setProtocols(a2a ? Collections.singletonList("a2a")
                : Collections.singletonList("custom"));
            entries.add(entry);
        }
        result.setOnlineVersions(entries);
        return result;
    }
    
    private com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail versionDetail(String version,
        boolean serviceFirst) {
        return versionDetailWithStatus(version, AiConstants.Agent.VERSION_STATUS_ONLINE, true,
            serviceFirst);
    }
    
    private com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail versionDetailWithStatus(
        String version, String status, boolean includeA2a) {
        return versionDetailWithStatus(version, status, includeA2a, false);
    }
    
    private com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail versionDetailWithStatus(
        String version, String status, boolean includeA2a, boolean serviceFirst) {
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail result =
            new com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setVersion(version);
        result.setStatus(status);
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol(includeA2a ? "a2a" : "custom");
        callInterface.setProtocolVersion("0.3");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(JacksonUtils.toObj(JacksonUtils.toJson(card(version)),
            Map.class));
        callInterface.setEndpointSourceOrder(serviceFirst
            ? Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED)
            : Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        result.setCallInterfaces(Collections.singletonList(callInterface));
        return result;
    }
    
    private Instance instance(String ip, int port, String transport, String protocolVersion,
        String priority, boolean healthy) {
        return instance(VERSION, ip, port, transport, protocolVersion, priority, healthy);
    }
    
    private Instance instance(String version, String ip, int port, String transport,
        String protocolVersion, String priority, boolean healthy) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("http://" + ip + ':' + port);
        endpoint.setTransport(transport);
        if (priority != null) {
            endpoint.setPriority(Integer.valueOf(priority));
        }
        Instance result = AgentRuntimeEndpointMapper.toLegacyA2aInstance(endpoint, version,
            protocolVersion, null);
        result.setHealthy(healthy);
        return result;
    }
    
    private AgentVersionSummary versionSummary(String version, long createTime, long updateTime) {
        AgentVersionSummary result = new AgentVersionSummary();
        result.setVersion(version);
        result.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        result.setCreateTime(createTime);
        result.setUpdateTime(updateTime);
        return result;
    }
    
    private <T> Page<T> page(List<T> items) {
        Page<T> result = new Page<T>();
        result.setPageItems(items);
        result.setTotalCount(items.size());
        return result;
    }
}
