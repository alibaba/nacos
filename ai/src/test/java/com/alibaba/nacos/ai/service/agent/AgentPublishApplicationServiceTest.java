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

import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPublishApplicationServiceTest {
    
    private static final String NAMESPACE_ID = "team";
    
    private static final String AGENT_NAME = "demo-agent";
    
    private static final String VERSION = "1.0.0";
    
    private AgentOperationService operationService;
    
    private AgentPublishApplicationService service;
    
    @BeforeEach
    void setUp() {
        operationService = mock(AgentOperationService.class);
        service = new AgentPublishApplicationService(operationService);
    }
    
    @Test
    void testRejectsNullRequest() {
        assertThrows(IllegalArgumentException.class,
            () -> service.publish(NAMESPACE_ID, null));
    }
    
    @Test
    void testCreatesDraftWithoutSubmit() throws Exception {
        AgentPublishRequest request = request(false);
        AgentVersionDetail draft = detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(notFound());
        when(operationService.createDraft(NAMESPACE_ID, request)).thenReturn(draft);
        
        assertSame(draft, service.publish(NAMESPACE_ID, request));
        verify(operationService, never()).submit(NAMESPACE_ID, AGENT_NAME, VERSION);
    }
    
    @Test
    void testCreatesAndSubmitsDraft() throws Exception {
        AgentPublishRequest request = request(true);
        AgentVersionDetail draft = detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        AgentVersionDetail online = detail(request, AiConstants.Agent.VERSION_STATUS_ONLINE);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(notFound()).thenReturn(online);
        when(operationService.createDraft(NAMESPACE_ID, request)).thenReturn(draft);
        
        assertSame(online, service.publish(NAMESPACE_ID, request));
        verify(operationService).submit(NAMESPACE_ID, AGENT_NAME, VERSION);
    }
    
    @Test
    void testEquivalentExistingDraftCanResumeSubmit() throws Exception {
        AgentPublishRequest request = request(true);
        AgentVersionDetail draft = detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        AgentVersionDetail reviewing =
            detail(request, AiConstants.Agent.VERSION_STATUS_REVIEWING);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(draft, reviewing);
        
        assertSame(reviewing, service.publish(NAMESPACE_ID, request));
        verify(operationService, never()).createDraft(NAMESPACE_ID, request);
    }
    
    @Test
    void testEquivalentSubmittedRetriesConverge() throws Exception {
        for (String status : Arrays.asList(AiConstants.Agent.VERSION_STATUS_REVIEWING,
            AiConstants.Agent.VERSION_STATUS_REVIEWED,
            AiConstants.Agent.VERSION_STATUS_ONLINE)) {
            AgentPublishRequest request = request(true);
            AgentVersionDetail existing = detail(request, status);
            when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
                .thenReturn(existing);
            assertSame(existing, service.publish(NAMESPACE_ID, request));
        }
        verify(operationService, never()).submit(NAMESPACE_ID, AGENT_NAME, VERSION);
    }
    
    @Test
    void testNonSubmitRejectsAdvancedStateAndSubmitRejectsOfflineState() throws Exception {
        AgentPublishRequest noSubmit = request(false);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail(noSubmit, AiConstants.Agent.VERSION_STATUS_ONLINE));
        NacosApiException advanced = assertThrows(NacosApiException.class,
            () -> service.publish(NAMESPACE_ID, noSubmit));
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), advanced.getDetailErrCode());
        
        AgentPublishRequest submit = request(true);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(detail(submit, AiConstants.Agent.VERSION_STATUS_OFFLINE));
        NacosApiException offline = assertThrows(NacosApiException.class,
            () -> service.publish(NAMESPACE_ID, submit));
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), offline.getDetailErrCode());
    }
    
    @Test
    void testCreateRaceRecoversEquivalentDraft() throws Exception {
        AgentPublishRequest request = request(false);
        AgentVersionDetail draft = detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(notFound()).thenReturn(draft);
        when(operationService.createDraft(NAMESPACE_ID, request)).thenThrow(conflictFailure());
        
        assertSame(draft, service.publish(NAMESPACE_ID, request));
    }
    
    @Test
    void testCreateFailurePreservesSuppressedReadFailure() throws Exception {
        AgentPublishRequest request = request(false);
        NacosException createFailure = conflictFailure();
        NacosException readFailure = new NacosException(NacosException.SERVER_ERROR, "read");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(notFound()).thenThrow(readFailure);
        when(operationService.createDraft(NAMESPACE_ID, request)).thenThrow(createFailure);
        
        assertSame(createFailure, assertThrows(NacosException.class,
            () -> service.publish(NAMESPACE_ID, request)));
        assertSame(readFailure, createFailure.getSuppressed()[0]);
    }
    
    @Test
    void testInitialReadFailureIsNotHidden() throws Exception {
        AgentPublishRequest request = request(false);
        NacosException failure = new NacosException(NacosException.SERVER_ERROR, "read");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION)).thenThrow(failure);
        assertSame(failure, assertThrows(NacosException.class,
            () -> service.publish(NAMESPACE_ID, request)));
    }
    
    @Test
    void testSubmitFailureConvergesOnlyAfterStateAdvanced() throws Exception {
        AgentPublishRequest request = request(true);
        AgentVersionDetail draft = detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        AgentVersionDetail online = detail(request, AiConstants.Agent.VERSION_STATUS_ONLINE);
        NacosException submitFailure = new NacosException(NacosException.SERVER_ERROR, "submit");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(draft, online);
        when(operationService.submit(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenThrow(submitFailure);
        assertSame(online, service.publish(NAMESPACE_ID, request));
        
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(draft);
        assertSame(submitFailure, assertThrows(NacosException.class,
            () -> service.publish(NAMESPACE_ID, request)));
    }
    
    @Test
    void testExistingContentMustBeEquivalent() throws Exception {
        AgentPublishRequest request = request(false);
        AgentVersionDetail differentContent =
            detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        differentContent.setContentDigest("sha256:different");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(differentContent);
        assertConflict(() -> service.publish(NAMESPACE_ID, request));
        
        AgentVersionDetail differentAuthor =
            detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        differentAuthor.setAuthor("bob");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(differentAuthor);
        assertConflict(() -> service.publish(NAMESPACE_ID, request));
        
        AgentVersionDetail differentDescription =
            detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        differentDescription.setChangeDescription("different");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(differentDescription);
        assertConflict(() -> service.publish(NAMESPACE_ID, request));
    }
    
    @Test
    void testBasedOnVersionUsesSourceDigest() throws Exception {
        AgentPublishRequest request = request(false);
        request.setCallInterfaces(null);
        request.setBasedOnVersion("0.9.0");
        AgentVersionDetail existing = new AgentVersionDetail();
        existing.setAgentName(AGENT_NAME);
        existing.setVersion(VERSION);
        existing.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        existing.setContentDigest("sha256:source");
        existing.setAuthor(request.getAuthor());
        existing.setChangeDescription(request.getChangeDescription());
        AgentVersionDetail source = new AgentVersionDetail();
        source.setContentDigest("sha256:source");
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(existing);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, "0.9.0"))
            .thenReturn(source);
        assertSame(existing, service.publish(NAMESPACE_ID, request));
        
        source.setContentDigest("sha256:different");
        assertConflict(() -> service.publish(NAMESPACE_ID, request));
    }
    
    @Test
    void testInitialMetadataMustBeEquivalent() throws Exception {
        AgentPublishRequest request = request(false);
        request.setDisplayName("Demo");
        request.setDescription("description");
        request.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        request.setProvider(provider);
        request.setTags(Collections.singletonList("assistant"));
        request.setExtensions(Collections.<String, Object>singletonMap("region", "east"));
        AgentVersionDetail existing = detail(request, AiConstants.Agent.VERSION_STATUS_DRAFT);
        Agent agent = matchingAgent(request);
        when(operationService.getVersion(NAMESPACE_ID, AGENT_NAME, VERSION))
            .thenReturn(existing);
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        assertSame(existing, service.publish(NAMESPACE_ID, request));
        
        agent.setDisplayName("different");
        assertConflict(() -> service.publish(NAMESPACE_ID, request));
        
        agent = matchingAgent(request);
        agent.setProvider(null);
        when(operationService.getAgent(NAMESPACE_ID, AGENT_NAME)).thenReturn(agent);
        assertConflict(() -> service.publish(NAMESPACE_ID, request));
    }
    
    private Agent matchingAgent(AgentPublishRequest request) {
        Agent result = new Agent();
        result.setDisplayName(request.getDisplayName());
        result.setDescription(request.getDescription());
        result.setIconUrl(request.getIconUrl());
        AgentProvider provider = new AgentProvider();
        provider.setName(request.getProvider().getName());
        provider.setUrl(request.getProvider().getUrl());
        result.setProvider(provider);
        result.setTags(request.getTags());
        result.setExtensions(new HashMap<String, Object>(request.getExtensions()));
        return result;
    }
    
    private AgentPublishRequest request(boolean autoSubmit) {
        AgentPublishRequest result = new AgentPublishRequest();
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        result.setCallInterfaces(Collections.singletonList(callInterface()));
        result.setAuthor("alice");
        result.setChangeDescription("initial");
        result.setAutoSubmit(autoSubmit);
        return result;
    }
    
    private AgentCallInterface callInterface() {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol("a2a");
        result.setProtocolVersion("0.3");
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(Collections.<String, Object>singletonMap("name", AGENT_NAME));
        result.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        return result;
    }
    
    private AgentVersionDetail detail(AgentPublishRequest request, String status) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setAgentName(AGENT_NAME);
        result.setVersion(VERSION);
        result.setStatus(status);
        result.setContentDigest(AgentVersionContentSerializer.serialize(
            new AgentVersionContent(request.getCallInterfaces())).getContentDigest());
        result.setAuthor(request.getAuthor());
        result.setChangeDescription(request.getChangeDescription());
        return result;
    }
    
    private NacosApiException notFound() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "not found");
    }
    
    private NacosException conflictFailure() {
        return new NacosException(NacosException.CONFLICT, "conflict");
    }
    
    private void assertConflict(ThrowingRunnable runnable) {
        NacosApiException exception = assertThrows(NacosApiException.class, runnable::run);
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), exception.getDetailErrCode());
    }
    
    @FunctionalInterface
    private interface ThrowingRunnable {
        
        void run() throws Exception;
    }
}
