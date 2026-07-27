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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.PublishPipelineInfo;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        traceService = org.mockito.Mockito.mockStatic(AiResourceTraceService.class);
    }
    
    @AfterEach
    void tearDown() {
        traceService.close();
        visibilityHelper.close();
    }
    
    @Test
    void testCreateAndReadOperationsDelegateWithoutChangingModels() throws NacosException {
        Agent agent = new Agent();
        agent.setAgentName(AGENT_NAME);
        AgentVersionDetail draft = new AgentVersionDetail();
        draft.setVersion(VERSION);
        AgentOverview overview = new AgentOverview();
        Agent storedAgent = new Agent();
        AiResource meta = meta(null, null);
        AgentVersionDetail detail = new AgentVersionDetail();
        Page<AgentVersionSummary> page = new Page<AgentVersionSummary>();
        when(persistenceService.create(agent, draft)).thenReturn(overview);
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(storedAgent);
        when(persistenceService.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail);
        when(persistenceService.listAgentVersions(NAMESPACE_ID, AGENT_NAME, null, 1, 20))
            .thenReturn(page);
        
        assertSame(overview, service.create(agent, draft));
        assertSame(storedAgent, service.getAgent(NAMESPACE_ID, AGENT_NAME));
        assertSame(detail, service.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        assertSame(page, service.listVersions(NAMESPACE_ID, AGENT_NAME, null, 1, 20));
        
        verify(resourceManager, org.mockito.Mockito.times(3)).ensureReadableOrNotFound(meta,
            "Agent not found: " + AGENT_NAME);
    }
    
    @Test
    void testDraftOperationsRequireWritableResourceAndDelegate() throws NacosException {
        AiResource meta = meta(VERSION, null);
        AgentVersionDetail draft = new AgentVersionDetail();
        draft.setVersion(VERSION);
        AgentVersionDetail result = new AgentVersionDetail();
        when(resourceManager.requireMeta(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(meta);
        when(persistenceService.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null))
            .thenReturn(result);
        when(persistenceService.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
            Collections.emptyList(), "change")).thenReturn(result);
        
        assertSame(result,
            service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null));
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
