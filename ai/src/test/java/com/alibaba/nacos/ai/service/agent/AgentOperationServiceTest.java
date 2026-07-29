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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.PublishPipelineInfo;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionResult;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOperationServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "Nacos Agent";
    
    private static final String VERSION = "1.0.0-RC1";
    
    @Mock
    private AgentPersistenceService persistenceService;
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private PublishPipelineExecutor publishPipelineExecutor;
    
    private AgentOperationService service;
    
    private MockedStatic<VisibilityHelper> visibilityHelper;
    
    private MockedStatic<AiResourceTraceService> traceService;
    
    @BeforeEach
    void setUp() {
        service =
            new AgentOperationService(persistenceService, resourceManager,
                publishPipelineExecutor);
        visibilityHelper = org.mockito.Mockito.mockStatic(VisibilityHelper.class);
        visibilityHelper.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("alice");
        visibilityHelper.when(VisibilityHelper::resolveClientIp).thenReturn("127.0.0.1");
        visibilityHelper.when(() -> VisibilityHelper.resolveDefaultScopeForCreate(
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn("PRIVATE");
        traceService = org.mockito.Mockito.mockStatic(AiResourceTraceService.class);
    }
    
    @AfterEach
    void tearDown() {
        traceService.close();
        visibilityHelper.close();
    }
    
    @Test
    void testReadOperationsDelegateWithoutChangingModels() throws NacosException {
        Agent storedAgent = new Agent();
        AiResource meta = meta(null, null);
        AgentVersionDetail detail = new AgentVersionDetail();
        Page<AgentVersionSummary> page = new Page<AgentVersionSummary>();
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(storedAgent);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail);
        when(persistenceService.listAgentVersions(NAMESPACE_ID, AGENT_NAME, null, 1, 20))
            .thenReturn(page);
        
        assertSame(storedAgent, service.getAgent(NAMESPACE_ID, AGENT_NAME));
        assertSame(detail, service.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(page, service.listVersions(NAMESPACE_ID, AGENT_NAME, null, 1, 20));
        
        verify(resourceManager, org.mockito.Mockito.times(3)).ensureReadableOrNotFound(meta,
            "Agent not found: " + AGENT_NAME);
    }
    
    @Test
    void testCreateFirstDraftBuildsServerGovernedAgentMetadata() throws NacosException {
        AgentDraftCreateRequest request = draftRequest();
        request.setDisplayName("Display");
        request.setDescription("Description");
        request.setIconUrl("https://example.com/icon.png");
        request.setTags(Collections.singletonList("assistant"));
        request.setExtensions(Collections.<String, Object>singletonMap("x-team", "ai"));
        AgentVersionDetail expected = new AgentVersionDetail();
        ArgumentCaptor<Agent> agentCaptor = ArgumentCaptor.forClass(Agent.class);
        ArgumentCaptor<AgentVersionDetail> draftCaptor =
            ArgumentCaptor.forClass(AgentVersionDetail.class);
        when(persistenceService.createInitialDraft(any(Agent.class),
            any(AgentVersionDetail.class))).thenReturn(expected);
        
        assertSame(expected, service.createDraft(NAMESPACE_ID, request));
        
        verify(persistenceService).createInitialDraft(agentCaptor.capture(),
            draftCaptor.capture());
        Agent agent = agentCaptor.getValue();
        assertEquals(NAMESPACE_ID, agent.getNamespaceId());
        assertEquals(AGENT_NAME, agent.getAgentName());
        assertEquals("Display", agent.getDisplayName());
        assertEquals("Description", agent.getDescription());
        assertEquals("https://example.com/icon.png", agent.getIconUrl());
        assertEquals(Collections.singletonList("assistant"), agent.getTags());
        assertEquals(Collections.singletonMap("x-team", "ai"), agent.getExtensions());
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_ENABLE, agent.getStatus());
        assertEquals("alice", agent.getOwner());
        assertEquals("PRIVATE", agent.getScope());
        assertEquals(VERSION, draftCaptor.getValue().getVersion());
        assertEquals("alice", draftCaptor.getValue().getAuthor());
        verify(resourceManager).findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
    }
    
    @Test
    void testCreateFirstDraftUsesDefaultOwnerWithoutRequestIdentity() throws NacosException {
        visibilityHelper.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("");
        AgentVersionDetail expected = new AgentVersionDetail();
        ArgumentCaptor<Agent> agentCaptor = ArgumentCaptor.forClass(Agent.class);
        when(persistenceService.createInitialDraft(any(Agent.class),
            any(AgentVersionDetail.class))).thenReturn(expected);
        
        assertSame(expected, service.createDraft(NAMESPACE_ID, draftRequest()));
        
        verify(persistenceService).createInitialDraft(agentCaptor.capture(),
            any(AgentVersionDetail.class));
        assertEquals("nacos", agentCaptor.getValue().getOwner());
    }
    
    @Test
    void testCreateFirstDraftRejectsBasedOnVersion() {
        AgentDraftCreateRequest request = draftRequest();
        request.setCallInterfaces(null);
        request.setBasedOnVersion("1.0.0");
        
        assertThrows(IllegalArgumentException.class,
            () -> service.createDraft(NAMESPACE_ID, request));
        
        verifyNoInteractions(persistenceService);
    }
    
    @Test
    void testEquivalentFirstDraftRetryUsesRecoverableInitialCreatePath()
        throws NacosException {
        AgentDraftCreateRequest request = draftRequest();
        request.setDescription("first-create-only");
        AiResource meta = meta(VERSION, null);
        AgentVersionDetail expected = new AgentVersionDetail();
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.createInitialDraft(any(Agent.class),
            any(AgentVersionDetail.class))).thenReturn(expected);
        
        assertSame(expected, service.createDraft(NAMESPACE_ID, request));
        
        visibilityHelper.verify(() -> VisibilityHelper.checkWritableResource(meta));
        verify(persistenceService).createInitialDraft(any(Agent.class),
            any(AgentVersionDetail.class));
        verify(persistenceService, never()).createDraft(eq(NAMESPACE_ID), eq(AGENT_NAME),
            any(AgentVersionDetail.class), eq(null));
    }
    
    @Test
    void testCreateSubsequentDraftRejectsFirstCreateMetadata() {
        AgentDraftCreateRequest request = draftRequest();
        request.setDescription("first-create-only");
        AiResource meta = meta(null, null);
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> service.createDraft(NAMESPACE_ID, request));
        
        assertTrue(exception.getMessage().contains("only allowed"));
        visibilityHelper.verify(() -> VisibilityHelper.checkWritableResource(meta));
        verifyNoInteractions(persistenceService);
    }
    
    @Test
    void testOverviewAndAgentUpdateReuseVisibilityRules() throws NacosException {
        AiResource meta = meta(null, null);
        AgentOverview overview = new AgentOverview();
        Agent replacement = new Agent();
        replacement.setNamespaceId(NAMESPACE_ID);
        replacement.setAgentName(AGENT_NAME);
        Agent updated = new Agent();
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.getAgentOverview(NAMESPACE_ID, AGENT_NAME, 10))
            .thenReturn(overview);
        when(persistenceService.tryUpdateAgent(replacement, meta)).thenReturn(updated);
        
        assertSame(overview, service.getOverview(NAMESPACE_ID, AGENT_NAME));
        assertSame(updated, service.updateAgent(replacement));
        
        verify(resourceManager).ensureReadableOrNotFound(meta,
            "Agent not found: " + AGENT_NAME);
        visibilityHelper.verify(() -> VisibilityHelper.checkWritableResource(meta));
    }
    
    @Test
    void testAgentUpdateAndDraftCreationRejectNullRequests() {
        assertThrows(IllegalArgumentException.class, () -> service.updateAgent(null));
        assertThrows(IllegalArgumentException.class,
            () -> service.createDraft(NAMESPACE_ID, null));
        
        verifyNoInteractions(persistenceService, resourceManager);
    }
    
    @Test
    void testAgentUpdateFailsAfterCasRetryExhaustion() throws NacosException {
        Agent replacement = new Agent();
        replacement.setNamespaceId(NAMESPACE_ID);
        replacement.setAgentName(AGENT_NAME);
        AiResource meta = meta(null, null);
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.tryUpdateAgent(replacement, meta)).thenReturn(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.updateAgent(replacement));
        
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), exception.getDetailErrCode());
        verify(persistenceService,
            org.mockito.Mockito.times(AiResourceConstants.MAX_WORKING_VERSION_RETRY))
            .tryUpdateAgent(replacement, meta);
    }
    
    @Test
    void testUpdateAgentRechecksWritePermissionBeforeEveryCasAttempt() throws NacosException {
        Agent replacement = new Agent();
        replacement.setNamespaceId(NAMESPACE_ID);
        replacement.setAgentName(AGENT_NAME);
        AiResource initial = meta(null, null);
        AiResource concurrent = meta(null, null);
        concurrent.setOwner("bob");
        NacosApiException denied = new NacosApiException(NacosException.NO_RIGHT,
            ErrorCode.ACCESS_DENIED, "denied");
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(initial, concurrent);
        when(persistenceService.tryUpdateAgent(replacement, initial)).thenReturn(null);
        visibilityHelper.when(() -> VisibilityHelper.checkWritableResource(concurrent))
            .thenThrow(denied);
        
        assertSame(denied, assertThrows(NacosApiException.class,
            () -> service.updateAgent(replacement)));
        
        verify(persistenceService).tryUpdateAgent(replacement, initial);
        verify(persistenceService, never()).tryUpdateAgent(replacement, concurrent);
        visibilityHelper.verify(() -> VisibilityHelper.checkWritableResource(initial));
        visibilityHelper.verify(() -> VisibilityHelper.checkWritableResource(concurrent));
    }
    
    @Test
    void testListAgentsBuildsVisibilityConstrainedAndTagQuery() throws NacosException {
        QueryCondition query = new QueryCondition();
        query.setScope("PRIVATE");
        query.setOwner("alice");
        Page<AgentSummary> expected = new Page<AgentSummary>();
        when(resourceManager.generateLikeArgument(anyString()))
            .thenAnswer(invocation -> invocation.<String>getArgument(0).replace('*', '%'));
        when(resourceManager.buildQueryCondition(eq(NAMESPACE_ID),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq("%Nacos%"),
            eq("%assistant%"), eq("PRIVATE"), eq("alice"), anyString()))
            .thenReturn(query);
        when(persistenceService.listAgents(eq(query), eq(2), eq(20))).thenReturn(expected);
        
        Page<AgentSummary> result = service.listAgents(NAMESPACE_ID, "Nacos", "assistant",
            "PRIVATE", "alice", "download_count", 2, 20);
        
        assertSame(expected, result);
        assertEquals("PRIVATE", query.getScope());
        assertEquals("alice", query.getOwner());
        assertEquals("download_count", query.getOrderBy());
        verify(resourceManager).buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, "%Nacos%", "%assistant%", "PRIVATE", "alice",
            VisibilityConstants.ACTION_READ);
    }
    
    @Test
    void testListAgentsTreatsBlankTagAsNoFilter() throws NacosException {
        QueryCondition query = new QueryCondition();
        Page<AgentSummary> expected = new Page<AgentSummary>();
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, null, null,
            VisibilityConstants.ACTION_READ))
            .thenReturn(query);
        when(persistenceService.listAgents(query, 1, 20)).thenReturn(expected);
        
        assertSame(expected,
            service.listAgents(NAMESPACE_ID, null, " ", null, null, null, 1, 20));
        
        verify(resourceManager, never()).generateLikeArgument(anyString());
    }
    
    @Test
    void testListAgentsReturnsEmptyPageForVisibilityDenial() throws NacosException {
        QueryCondition query = new QueryCondition();
        query.setAlwaysEmpty(true);
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, null, null,
            VisibilityConstants.ACTION_READ)).thenReturn(query);
        
        Page<AgentSummary> result =
            service.listAgents(NAMESPACE_ID, null, null, null, null, null, 3, 20);
        
        assertEquals(3, result.getPageNumber());
        assertEquals(0, result.getTotalCount());
        verifyNoInteractions(persistenceService);
    }
    
    @Test
    void testListAgentsAcceptsPublicScopeAndRejectsUnsupportedFilters()
        throws NacosException {
        QueryCondition query = new QueryCondition();
        Page<AgentSummary> expected = new Page<AgentSummary>();
        when(resourceManager.buildQueryCondition(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, null, "PUBLIC", null,
            VisibilityConstants.ACTION_READ)).thenReturn(query);
        when(persistenceService.listAgents(query, 1, 20)).thenReturn(expected);
        
        assertSame(expected,
            service.listAgents(NAMESPACE_ID, null, null, "PUBLIC", null, null, 1, 20));
        assertThrows(IllegalArgumentException.class,
            () -> service.listAgents(NAMESPACE_ID, null, null, "INTERNAL", null, null, 1, 20));
        assertThrows(IllegalArgumentException.class,
            () -> service.listAgents(NAMESPACE_ID, null, null, null, null, "name", 1, 20));
    }
    
    @Test
    void testListAgentsRejectsInvalidTagFilter() {
        assertThrows(IllegalArgumentException.class,
            () -> service.listAgents(NAMESPACE_ID, null, String.join("", Collections.nCopies(65,
                "a")), null, null, null, 1, 20));
        
        verifyNoInteractions(persistenceService);
    }
    
    @Test
    void testDraftOperationsRequireWritableResourceAndDelegate() throws NacosException {
        AiResource meta = meta(VERSION, null);
        AgentDraftCreateRequest request = draftRequest();
        AgentVersionDetail draft = new AgentVersionDetail();
        draft.setVersion(request.getVersion());
        draft.setCallInterfaces(request.getCallInterfaces());
        draft.setAuthor(request.getAuthor());
        draft.setChangeDescription(request.getChangeDescription());
        AgentVersionDetail result = new AgentVersionDetail();
        when(resourceManager.findMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.createDraft(eq(NAMESPACE_ID), eq(AGENT_NAME),
            any(AgentVersionDetail.class), eq(null)))
            .thenReturn(result);
        when(persistenceService.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
            Collections.emptyList(), "change")).thenReturn(result);
        
        assertSame(result, service.createDraft(NAMESPACE_ID, request));
        ArgumentCaptor<AgentVersionDetail> draftCaptor =
            ArgumentCaptor.forClass(AgentVersionDetail.class);
        verify(persistenceService).createDraft(eq(NAMESPACE_ID), eq(AGENT_NAME),
            draftCaptor.capture(), eq(null));
        assertEquals(draft.getVersion(), draftCaptor.getValue().getVersion());
        assertEquals(draft.getCallInterfaces(), draftCaptor.getValue().getCallInterfaces());
        assertEquals(draft.getAuthor(), draftCaptor.getValue().getAuthor());
        assertEquals(draft.getChangeDescription(),
            draftCaptor.getValue().getChangeDescription());
        assertSame(result, service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
            Collections.emptyList(), "change"));
        service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        service.deleteAgent(NAMESPACE_ID, AGENT_NAME);
        
        visibilityHelper.verify(
            () -> VisibilityHelper.checkWritableResource(meta),
            org.mockito.Mockito.times(4));
        verify(persistenceService).deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        verify(persistenceService).deleteAgent(NAMESPACE_ID, AGENT_NAME);
    }
    
    @Test
    void testInitialDraftContentGuardCoversMissingDirectContent() {
        AgentDraftCreateRequest request = new AgentDraftCreateRequest();
        
        assertThrows(IllegalArgumentException.class,
            () -> ReflectionTestUtils.invokeMethod(service, "requireInitialDraftContent",
                request));
    }
    
    @Test
    void testInitialAgentMetadataDetectionChecksEveryField() {
        AgentDraftCreateRequest request = new AgentDraftCreateRequest();
        assertFalse(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
        
        request.setDisplayName("display");
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
        request.setDisplayName(null);
        request.setDescription("description");
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
        request.setDescription(null);
        request.setIconUrl("https://example.com/icon.png");
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
        request.setIconUrl(null);
        request.setProvider(new com.alibaba.nacos.api.ai.model.agent.AgentProvider());
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
        request.setProvider(null);
        request.setTags(Collections.singletonList("assistant"));
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
        request.setTags(null);
        request.setExtensions(Collections.<String, Object>singletonMap("region", "east"));
        assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service,
            "hasInitialAgentMetadata", request)));
    }
    
    @Test
    void testSubmitWithoutPipelinePublishesVerifiedDraftAndClearsEditing()
        throws NacosException {
        AiResource meta = meta(VERSION, null);
        AiResourceVersion draft = versionRow(AiConstants.Agent.VERSION_STATUS_DRAFT);
        AgentVersionSummary summary = summary(AiConstants.Agent.VERSION_STATUS_ONLINE);
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(draft);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT))
            .thenReturn(false);
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(summary);
        
        assertSame(summary, service.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        verify(persistenceService).updateVersionStatus(NAMESPACE_ID, AGENT_NAME, VERSION,
            AiConstants.Agent.VERSION_STATUS_ONLINE);
        verify(persistenceService).synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, VERSION, null,
            VERSION, null);
        verify(resourceManager, never()).moveToReviewing(anyString(), anyString(), anyString(),
            anyString(), any(AiResource.class), any(ResourceVersionInfo.class));
    }
    
    @Test
    void testSubmitRejectsNonDraftBeforeContentOrPipelineAccess() throws NacosException {
        stubWritableMeta(meta(VERSION, null));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertIllegalState(exception);
        verify(persistenceService, never()).getAgentVersion(anyString(), anyString(), anyString());
        verify(publishPipelineExecutor, never()).isPipelineAvailable(any());
        verify(persistenceService, never()).updateVersionStatus(anyString(), anyString(),
            anyString(), anyString());
    }
    
    @Test
    void testSubmitStartsAgentPipelineAfterMovingDraftToReviewing() throws NacosException {
        AiResource meta = meta(VERSION, null);
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_DRAFT));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenAnswer(
                invocation -> invocation.getArgument(2));
        AgentVersionSummary reviewing =
            summary(AiConstants.Agent.VERSION_STATUS_REVIEWING);
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(reviewing);
        
        assertSame(reviewing, service.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        verify(resourceManager).moveToReviewing(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(VERSION), eq(meta),
            any(ResourceVersionInfo.class));
        ArgumentCaptor<PublishPipelineContext> contextCaptor =
            ArgumentCaptor.forClass(PublishPipelineContext.class);
        verify(publishPipelineExecutor).execute(contextCaptor.capture(),
            any(PipelineCallback.class), anyString());
        assertEquals(PublishPipelineResourceType.AGENT,
            contextCaptor.getValue().getResourceType());
        assertEquals(NAMESPACE_ID, contextCaptor.getValue().getNamespaceId());
        assertEquals(AGENT_NAME, contextCaptor.getValue().getResourceName());
        assertEquals(VERSION, contextCaptor.getValue().getVersion());
    }
    
    @Test
    void testSubmitPipelineFallthroughPublishesAndClearsReviewing() throws NacosException {
        AiResource meta = meta(VERSION, null);
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_DRAFT),
                versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenReturn("");
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(summary(AiConstants.Agent.VERSION_STATUS_ONLINE));
        
        service.submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        verify(persistenceService).updatePublishPipelineInfo(eq(NAMESPACE_ID),
            eq(AGENT_NAME), eq(VERSION), org.mockito.ArgumentMatchers.contains("IN_PROGRESS"));
        verify(persistenceService).updatePublishPipelineInfo(NAMESPACE_ID, AGENT_NAME, VERSION,
            null);
        verify(persistenceService).synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, VERSION, null,
            null, VERSION);
    }
    
    @Test
    void testSubmitStopsWhenPipelineMarkerCannotBeWritten() throws NacosException {
        stubWritableMeta(meta(VERSION, null));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_DRAFT));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT))
            .thenReturn(true);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "marker write failed"))
            .when(persistenceService).updatePublishPipelineInfo(eq(NAMESPACE_ID),
                eq(AGENT_NAME), eq(VERSION), anyString());
        
        assertThrows(NacosException.class,
            () -> service.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        verify(publishPipelineExecutor, never()).execute(any(PublishPipelineContext.class),
            any(PipelineCallback.class), anyString());
        verify(persistenceService, never()).synchronizeDerivedState(anyString(), anyString(),
            anyString(), any(), any(), any());
    }
    
    @Test
    void testSubmitStopsWhenPipelineMarkerCannotBeCleared() throws NacosException {
        stubWritableMeta(meta(VERSION, null));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_DRAFT));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenReturn("");
        doAnswer(invocation -> {
            if (invocation.getArgument(3) == null) {
                throw new NacosException(NacosException.SERVER_ERROR, "marker clear failed");
            }
            return null;
        }).when(persistenceService).updatePublishPipelineInfo(eq(NAMESPACE_ID),
            eq(AGENT_NAME), eq(VERSION), any());
        
        assertThrows(NacosException.class,
            () -> service.submit(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        verify(persistenceService, never()).synchronizeDerivedState(anyString(), anyString(),
            anyString(), any(), any(), any());
    }
    
    @Test
    void testApprovedPipelineCallbackTransitionsOnlyMatchingReview()
        throws NacosException {
        AtomicReference<PipelineCallback> callback = new AtomicReference<PipelineCallback>();
        AtomicReference<String> executionId = new AtomicReference<String>();
        prepareAsyncSubmit(callback, executionId);
        
        service.submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        AiResourceVersion inProgress =
            versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING);
        inProgress.setPublishPipelineInfo(pipelineInfo(executionId.get(),
            PipelineExecutionStatus.IN_PROGRESS, false));
        AiResourceVersion completed =
            versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING);
        completed.setPublishPipelineInfo(pipelineInfo(executionId.get(),
            PipelineExecutionStatus.APPROVED, false));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(inProgress, completed);
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId(executionId.get());
        result.setStatus(PipelineExecutionStatus.APPROVED);
        
        callback.get().onComplete(result);
        
        verify(persistenceService).updatePublishPipelineInfo(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(VERSION), org.mockito.ArgumentMatchers.contains("APPROVED"));
        verify(persistenceService).updateVersionStatus(NAMESPACE_ID, AGENT_NAME, VERSION,
            AiConstants.Agent.VERSION_STATUS_REVIEWED);
    }
    
    @Test
    void testPipelineCallbackIgnoresStaleExecution() throws NacosException {
        AtomicReference<PipelineCallback> callback = new AtomicReference<PipelineCallback>();
        AtomicReference<String> executionId = new AtomicReference<String>();
        prepareAsyncSubmit(callback, executionId);
        service.submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        AiResourceVersion current =
            versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING);
        current.setPublishPipelineInfo(pipelineInfo("newer-execution",
            PipelineExecutionStatus.IN_PROGRESS, false));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(current);
        
        callback.get().onComplete(new PipelineExecutionResult());
        
        verify(persistenceService, never()).updatePublishPipelineInfo(eq(NAMESPACE_ID),
            eq(AGENT_NAME), eq(VERSION),
            org.mockito.ArgumentMatchers.contains("APPROVED"));
        verify(persistenceService, never()).updateVersionStatus(anyString(), anyString(),
            anyString(), eq(AiConstants.Agent.VERSION_STATUS_REVIEWED));
    }
    
    @Test
    void testPipelineCallbackDoesNotRegressVersionThatLeftReviewing()
        throws NacosException {
        AtomicReference<PipelineCallback> callback = new AtomicReference<PipelineCallback>();
        AtomicReference<String> executionId = new AtomicReference<String>();
        prepareAsyncSubmit(callback, executionId);
        service.submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        AiResourceVersion current =
            versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING);
        current.setPublishPipelineInfo(pipelineInfo(executionId.get(),
            PipelineExecutionStatus.IN_PROGRESS, false));
        AiResourceVersion alreadyOnline =
            versionRow(AiConstants.Agent.VERSION_STATUS_ONLINE);
        alreadyOnline.setPublishPipelineInfo(pipelineInfo(executionId.get(),
            PipelineExecutionStatus.APPROVED, false));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(current, alreadyOnline);
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setStatus(PipelineExecutionStatus.APPROVED);
        
        callback.get().onComplete(result);
        
        verify(persistenceService, never()).updateVersionStatus(anyString(), anyString(),
            anyString(), eq(AiConstants.Agent.VERSION_STATUS_REVIEWED));
    }
    
    @Test
    void testPipelineCallbackFailureIsContained() throws NacosException {
        AtomicReference<PipelineCallback> callback = new AtomicReference<PipelineCallback>();
        AtomicReference<String> executionId = new AtomicReference<String>();
        prepareAsyncSubmit(callback, executionId);
        service.submit(NAMESPACE_ID, AGENT_NAME, VERSION);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(new IllegalStateException("storage unavailable"));
        
        assertDoesNotThrow(() -> callback.get().onComplete(new PipelineExecutionResult()));
        
        verify(persistenceService, never()).updatePublishPipelineInfo(eq(NAMESPACE_ID),
            eq(AGENT_NAME), eq(VERSION),
            org.mockito.ArgumentMatchers.contains("APPROVED"));
    }
    
    @Test
    void testPublishRequiresReviewedApprovedCurrentReviewAndRevalidatesContent()
        throws NacosException {
        AiResource meta = meta(null, VERSION);
        AiResourceVersion reviewed = versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWED);
        reviewed.setPublishPipelineInfo(
            pipelineInfo("execution", PipelineExecutionStatus.APPROVED, false));
        AgentVersionSummary online =
            summary(AiConstants.Agent.VERSION_STATUS_ONLINE);
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(reviewed);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(online);
        
        assertSame(online, service.publish(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        verify(persistenceService).getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION);
        verify(persistenceService).updateVersionStatus(NAMESPACE_ID, AGENT_NAME, VERSION,
            AiConstants.Agent.VERSION_STATUS_ONLINE);
        verify(persistenceService).synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, VERSION, null,
            null, VERSION);
    }
    
    @Test
    void testPublishRejectsUnapprovedOrHistoricalReview() throws NacosException {
        AiResource meta = meta(null, VERSION);
        AiResourceVersion reviewed = versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWED);
        reviewed.setPublishPipelineInfo(
            pipelineInfo("execution", PipelineExecutionStatus.APPROVED, true));
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(reviewed);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.publish(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertIllegalState(exception);
        verify(persistenceService, never()).updateVersionStatus(anyString(), anyString(),
            anyString(), anyString());
    }
    
    @Test
    void testForcePublishAcceptsReviewingAndRejectsOnline() throws NacosException {
        AiResource meta = meta(null, VERSION);
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWING));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(summary(AiConstants.Agent.VERSION_STATUS_ONLINE));
        
        service.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        verify(persistenceService).synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, VERSION, null,
            null, VERSION);
        
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_ONLINE));
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.forcePublish(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertIllegalState(exception);
    }
    
    @Test
    void testRedraftRejectsAnotherEditingVersionBeforeGenericTransition()
        throws NacosException {
        AiResource meta = meta("2.0.0", VERSION);
        stubWritableMeta(meta);
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWED));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.redraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), exception.getDetailErrCode());
        verify(resourceManager, never()).doRedraft(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void testRedraftCurrentReviewReturnsDraftSummary() throws NacosException {
        stubWritableMeta(meta(null, VERSION));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWED));
        AgentVersionSummary draft = summary(AiConstants.Agent.VERSION_STATUS_DRAFT);
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(draft);
        
        assertSame(draft, service.redraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        verify(resourceManager).doRedraft(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION);
    }
    
    @Test
    void testPublishRejectsVersionThatIsNotCurrentReview() throws NacosException {
        stubWritableMeta(meta(null, "2.0.0"));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_REVIEWED));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.publish(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertIllegalState(exception);
        verify(persistenceService, never()).updateVersionStatus(anyString(), anyString(),
            anyString(), anyString());
    }
    
    @Test
    void testOnlineAndOfflineUseExactSourceStatesAndRebuildDerivedData()
        throws NacosException {
        stubWritableMeta(meta(null, null));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_OFFLINE),
                versionRow(AiConstants.Agent.VERSION_STATUS_ONLINE));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(summary(AiConstants.Agent.VERSION_STATUS_ONLINE),
                summary(AiConstants.Agent.VERSION_STATUS_OFFLINE));
        
        service.online(NAMESPACE_ID, AGENT_NAME, VERSION);
        service.offline(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        verify(persistenceService).synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, VERSION, null,
            null, null);
        verify(persistenceService).synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null, null,
            null, null);
    }
    
    @Test
    void testUpdateLabelsDelegatesCompleteMapToDerivedStateCas() throws NacosException {
        stubWritableMeta(meta(null, null));
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("stable", VERSION);
        Agent updated = new Agent();
        when(persistenceService.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null, labels,
            null, null)).thenReturn(updated);
        
        assertSame(updated, service.updateLabels(NAMESPACE_ID, AGENT_NAME, labels));
    }
    
    private void prepareAsyncSubmit(AtomicReference<PipelineCallback> callback,
        AtomicReference<String> executionId) throws NacosException {
        stubWritableMeta(meta(VERSION, null));
        when(persistenceService.requireVersionRow(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(versionRow(AiConstants.Agent.VERSION_STATUS_DRAFT));
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(new AgentVersionDetail());
        when(publishPipelineExecutor.isPipelineAvailable(PublishPipelineResourceType.AGENT))
            .thenReturn(true);
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class),
            any(PipelineCallback.class), anyString())).thenAnswer(invocation -> {
                callback.set(invocation.getArgument(1));
                executionId.set(invocation.getArgument(2));
                return executionId.get();
            });
        when(persistenceService.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(summary(AiConstants.Agent.VERSION_STATUS_REVIEWING));
    }
    
    private void stubWritableMeta(AiResource meta) throws NacosException {
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
    }
    
    private AiResource meta(String editingVersion, String reviewingVersion) {
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setEditingVersion(editingVersion);
        versionInfo.setReviewingVersion(reviewingVersion);
        versionInfo.setOnlineCnt(0);
        versionInfo.setLabels(new LinkedHashMap<String, String>());
        AiResource result = new AiResource();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(AGENT_NAME);
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setMetaVersion(1L);
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        return result;
    }
    
    private AiResourceVersion versionRow(String status) {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(AGENT_NAME);
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setVersion(VERSION);
        result.setStatus(status);
        return result;
    }
    
    private AgentVersionSummary summary(String status) {
        AgentVersionSummary result = new AgentVersionSummary();
        result.setVersion(VERSION);
        result.setStatus(status);
        return result;
    }
    
    private AgentDraftCreateRequest draftRequest() {
        AgentDraftCreateRequest result = new AgentDraftCreateRequest();
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        result.setCallInterfaces(Collections.emptyList());
        result.setAuthor("alice");
        result.setChangeDescription("Create draft");
        return result;
    }
    
    private String pipelineInfo(String executionId, PipelineExecutionStatus status,
        boolean historical) {
        PublishPipelineInfo result = new PublishPipelineInfo();
        result.setExecutionId(executionId);
        result.setStatus(status);
        result.setHistorical(historical);
        return JacksonUtils.toJson(result);
    }
    
    private void assertIllegalState(NacosApiException exception) {
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), exception.getDetailErrCode());
    }
}
