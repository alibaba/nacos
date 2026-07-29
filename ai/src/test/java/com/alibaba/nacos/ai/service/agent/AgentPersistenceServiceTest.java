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
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentResourceExt;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.metadata.AgentResourceExtSerializer;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionCatalogBuilder;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DuplicateKeyException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPersistenceServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "Nacos Agent";
    
    private static final String VERSION = "1.0.0-RC1";
    
    private static final long VERSION_ID = 101L;
    
    private static final long RESOURCE_ID = 201L;
    
    @Mock
    private AiResourcePersistService resourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService versionPersistService;
    
    @Mock
    private AgentVersionStorageService storageService;
    
    private AgentPersistenceService service;
    
    private Agent agent;
    
    private AgentVersionDetail initialDraft;
    
    private AgentVersionContent content;
    
    private PreparedAgentVersionWrite prepared;
    
    private ConfigurableEnvironment previousEnvironment;
    
    @BeforeEach
    void setUp() {
        previousEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        service = new AgentPersistenceService(resourcePersistService, versionPersistService,
            storageService);
        agent = newAgent();
        initialDraft = newInitialDraft();
        content = new AgentVersionContent(initialDraft.getCallInterfaces());
        prepared = new AgentVersionStorageService().prepare(NAMESPACE_ID, AGENT_NAME, VERSION,
            content);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(previousEnvironment);
    }
    
    @Test
    void testCreateClaimsVersionBeforeStorageAndPublishesResourceLast() throws NacosException {
        AtomicReference<AiResource> persistedResource = new AtomicReference<AiResource>();
        AtomicReference<AiResourceVersion> persistedVersion =
            new AtomicReference<AiResourceVersion>();
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(invocation -> persistedResource.get());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenAnswer(
                invocation -> persistedVersion.get());
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenAnswer(invocation -> {
            persistedVersion.set(
                asPersisted(invocation.<AiResourceVersion>getArgument(0), VERSION_ID));
            return VERSION_ID;
        });
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            persistedResource.set(
                asPersisted(invocation.<AiResource>getArgument(0), RESOURCE_ID));
            return RESOURCE_ID;
        });
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        assertPersistedResource(persistedResource.get());
        assertPersistedVersion(persistedVersion.get());
        InOrder order = inOrder(storageService, resourcePersistService, versionPersistService);
        order.verify(storageService).prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(VERSION),
            any(AgentVersionContent.class));
        order.verify(resourcePersistService).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        order.verify(versionPersistService).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION);
        order.verify(versionPersistService).insert(any(AiResourceVersion.class));
        order.verify(storageService).save(prepared);
        order.verify(resourcePersistService).insert(any(AiResource.class));
        order.verify(resourcePersistService).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        order.verify(versionPersistService).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION);
        order.verify(storageService).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testCreateRejectsReadOnlyAndMismatchedInputsBeforePersistence() {
        agent.setMetaVersion(1L);
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        agent.setMetaVersion(null);
        initialDraft.setNamespaceId("other");
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        initialDraft.setNamespaceId(null);
        initialDraft.setAgentName("other");
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        initialDraft.setAgentName(null);
        initialDraft.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        initialDraft.setStatus(null);
        initialDraft.setContentDigest(prepared.getDescriptor().getContentDigest());
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testCreateRejectsNullInputsBeforePersistence() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(null, initialDraft));
        assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, null));
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testCreateRejectsTagsExceedingPersistedColumnCapacity() throws NacosException {
        List<String> tags = new ArrayList<String>();
        for (int i = 0; i < 32; i++) {
            tags.add(newTag(i, 64));
        }
        agent.setTags(tags);
        stubPrepare();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertTrue(exception.getMessage().contains("persisted characters"));
        verifyNoInteractions(resourcePersistService, versionPersistService);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testCreateAcceptsTagsAtPersistedColumnCapacity() throws NacosException {
        List<String> tags = new ArrayList<String>();
        for (int i = 0; i < 16; i++) {
            tags.add(newTag(i, i == 15 ? 60 : 61));
        }
        agent.setTags(tags);
        AiResource equivalentResource = equivalentStoredResource();
        assertEquals(1024, equivalentResource.getBizTags().length());
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(equivalentResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertNotNull(result);
        assertEquals(tags, service.getAgent(NAMESPACE_ID, AGENT_NAME).getTags());
    }
    
    @Test
    void testCreateNormalizesNullTagsToEmptyArray() throws NacosException {
        agent.setTags(null);
        AiResource equivalentResource = equivalentStoredResource();
        equivalentResource.setBizTags("[]");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(equivalentResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertNotNull(result);
        assertTrue(service.getAgent(NAMESPACE_ID, AGENT_NAME).getTags().isEmpty());
    }
    
    @Test
    void testCreateRejectsExistingResourceBeforeAnyWrite() throws NacosException {
        stubPrepare();
        AiResource conflictingResource = storedResource();
        conflictingResource.setDesc("different metadata");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(conflictingResource);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(versionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testCreateRejectsExistingVersionBeforeAnyWrite() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        AiResourceVersion conflictingVersion = storedVersion();
        conflictingVersion.setAuthor("other");
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(conflictingVersion);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(versionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testCreateRejectsEveryChangedResourceIdentityAndMetadata() throws NacosException {
        List<AiResource> conflictingResources = createConflictingResources();
        AtomicInteger index = new AtomicInteger();
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(
                invocation -> conflictingResources.get(index.getAndIncrement()));
        
        for (int i = 0; i < conflictingResources.size(); i++) {
            assertConflict(assertThrows(NacosApiException.class,
                () -> service.createInitialDraft(agent, initialDraft)));
        }
        
        assertEquals(conflictingResources.size(), index.get());
        verifyNoInteractions(versionPersistService);
        verify(resourcePersistService, never()).insert(any(AiResource.class));
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testCreateRejectsEveryChangedVersionIdentityAndMetadata() throws NacosException {
        List<AiResourceVersion> conflictingVersions = createConflictingVersions();
        AtomicInteger index = new AtomicInteger();
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenAnswer(
                invocation -> conflictingVersions.get(index.getAndIncrement()));
        
        for (int i = 0; i < conflictingVersions.size(); i++) {
            assertConflict(assertThrows(NacosApiException.class,
                () -> service.createInitialDraft(agent, initialDraft)));
        }
        
        assertEquals(conflictingVersions.size(), index.get());
        verify(versionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testVersionInsertRaceMapsToConflictWithoutStorageWrite() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
    }
    
    @Test
    void testVersionInsertRaceAdoptsEquivalentConcurrentVersion() throws NacosException {
        AtomicReference<AiResource> persistedResource = new AtomicReference<AiResource>();
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(invocation -> persistedResource.get());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null, storedVersion(),
                storedVersion());
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            persistedResource.set(
                asPersisted(invocation.<AiResource>getArgument(0), RESOURCE_ID));
            return RESOURCE_ID;
        });
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(storageService).save(prepared);
    }
    
    @Test
    void testVersionInsertUnknownFailureLeavesPossibleClaimUntouched() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new IllegalStateException("unknown insert outcome"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testVersionInsertInvalidIdMapsToServerError() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(0L);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
    }
    
    @Test
    void testVersionDuplicateInsertAndReadbackFailureMapsToConflict() throws NacosException {
        DuplicateKeyException insertFailure = new DuplicateKeyException("duplicate");
        IllegalStateException readFailure = new IllegalStateException("read failed");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null)
            .thenThrow(readFailure);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenThrow(insertFailure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        assertSame(insertFailure, exception.getCause());
        assertSame(readFailure, insertFailure.getSuppressed()[0]);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testVersionUnknownInsertAndReadbackFailureMapsToServerError() throws NacosException {
        IllegalStateException insertFailure =
            new IllegalStateException("unknown insert outcome");
        IllegalStateException readFailure = new IllegalStateException("read failed");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null)
            .thenThrow(readFailure);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenThrow(insertFailure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        assertSame(insertFailure, exception.getCause());
        assertSame(readFailure, insertFailure.getSuppressed()[0]);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testStorageFailurePreservesOwnedVersionForRetry() throws NacosException {
        NacosException storageFailure =
            new NacosException(NacosException.SERVER_ERROR, "storage unavailable");
        stubUntilVersionInsert();
        doThrow(storageFailure).when(storageService).save(prepared);
        
        NacosException result = assertThrows(NacosException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertSame(storageFailure, result);
        verify(storageService).save(prepared);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
    }
    
    @Test
    void testStorageFailureAfterSharedVersionDoesNotDeleteSharedState() throws NacosException {
        NacosException storageFailure =
            new NacosException(NacosException.SERVER_ERROR, "storage unavailable");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        doThrow(storageFailure).when(storageService).save(prepared);
        
        NacosException result = assertThrows(NacosException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertSame(storageFailure, result);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
    }
    
    @Test
    void testEquivalentCreateRetryReturnsExistingAgentAndRepairsStorage()
        throws NacosException {
        stubPrepare();
        AiResource equivalentResource = equivalentStoredResource();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(equivalentResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(versionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
        verify(storageService).save(prepared);
    }
    
    @Test
    void testEquivalentCreateRetryIgnoresExtensionObjectMemberOrder()
        throws NacosException {
        Map<String, Object> expectedNested = new LinkedHashMap<String, Object>();
        expectedNested.put("first", 1);
        expectedNested.put("second", 2);
        Map<String, Object> expectedExtensions = new LinkedHashMap<String, Object>();
        expectedExtensions.put("x-a", expectedNested);
        expectedExtensions.put("x-b", true);
        agent.setExtensions(expectedExtensions);
        stubPrepare();
        
        AiResource equivalentResource = storedResource();
        AgentResourceExt resourceExt =
            AgentResourceExtSerializer.deserialize(equivalentResource.getExt());
        Map<String, Object> reorderedNested = new LinkedHashMap<String, Object>();
        reorderedNested.put("second", 2);
        reorderedNested.put("first", 1);
        Map<String, Object> reorderedExtensions = new LinkedHashMap<String, Object>();
        reorderedExtensions.put("x-b", true);
        reorderedExtensions.put("x-a", reorderedNested);
        resourceExt.setExtensions(reorderedExtensions);
        equivalentResource.setExt(AgentResourceExtSerializer.serialize(resourceExt));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(equivalentResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(versionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(resourcePersistService, never()).insert(any(AiResource.class));
    }
    
    @Test
    void testRetryReusesEquivalentOrphanVersionAndPublishesResource() throws NacosException {
        AtomicReference<AiResource> persistedResource = new AtomicReference<AiResource>();
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(invocation -> persistedResource.get());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(reorderedStoredVersion());
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            persistedResource.set(
                asPersisted(invocation.<AiResource>getArgument(0), RESOURCE_ID));
            return RESOURCE_ID;
        });
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(versionPersistService, never()).insert(any(AiResourceVersion.class));
        verify(storageService).save(prepared);
        verify(resourcePersistService).insert(any(AiResource.class));
    }
    
    @Test
    void testVersionInsertUnknownOutcomeRecoversEquivalentClaim() throws NacosException {
        AtomicReference<AiResource> persistedResource = new AtomicReference<AiResource>();
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(invocation -> persistedResource.get());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null, storedVersion(),
                storedVersion());
        when(versionPersistService.insert(any(AiResourceVersion.class)))
            .thenThrow(new IllegalStateException("unknown insert outcome"));
        when(resourcePersistService.insert(any(AiResource.class))).thenAnswer(invocation -> {
            persistedResource.set(
                asPersisted(invocation.<AiResource>getArgument(0), RESOURCE_ID));
            return RESOURCE_ID;
        });
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(storageService).save(prepared);
    }
    
    @Test
    void testResourceInsertDuplicateForDifferentVersionPreservesForOrphanRecovery()
        throws NacosException {
        stubPrepare();
        AiResource winningResource = equivalentStoredResource();
        winningResource.setVersionInfo(serializeVersionInfo("2.0.0"));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, winningResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(resourcePersistService).insert(any(AiResource.class));
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertDuplicatePreservesVersionReferencedByWinner()
        throws NacosException {
        stubPrepare();
        AiResource winningResource = equivalentStoredResource();
        winningResource.setDesc("different metadata");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, winningResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertDuplicatePreservesVersionMovedToReviewing()
        throws NacosException {
        stubPrepare();
        AiResource winningResource = equivalentStoredResource();
        ResourceVersionInfo lifecycle = initialVersionInfo("2.0.0");
        lifecycle.setReviewingVersion(VERSION);
        winningResource.setVersionInfo(JacksonUtils.toJson(lifecycle));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, winningResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertDuplicateWithEquivalentWinnerCompletesIdempotently()
        throws NacosException {
        stubPrepare();
        AiResource winningResource = equivalentStoredResource();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, winningResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null, storedVersion());
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertUnknownOutcomeWithEquivalentReadbackCompletesIdempotently()
        throws NacosException {
        stubPrepare();
        AiResource committedResource = equivalentStoredResource();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, committedResource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null, storedVersion());
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new IllegalStateException("unknown insert outcome"));
        
        AgentVersionDetail result = service.createInitialDraft(agent, initialDraft);
        
        assertCreatedDetail(result);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertUnknownFailurePreservesDependencies() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new IllegalStateException("unknown insert outcome"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        verify(storageService).save(prepared);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertDuplicateWithoutVisibleReadbackPreservesDependencies()
        throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertInvalidIdMapsToServerError() throws NacosException {
        stubUntilVersionInsert();
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(0L);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        verify(storageService).save(prepared);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceDuplicateInsertAndReadbackFailureMapsToConflict() throws NacosException {
        DuplicateKeyException insertFailure = new DuplicateKeyException("duplicate");
        IllegalStateException readFailure = new IllegalStateException("read failed");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null).thenThrow(readFailure);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class))).thenThrow(insertFailure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertConflict(exception);
        assertSame(insertFailure, exception.getCause());
        assertSame(readFailure, insertFailure.getSuppressed()[0]);
        verify(storageService).save(prepared);
    }
    
    @Test
    void testResourceUnknownInsertAndReadbackFailureMapsToServerError()
        throws NacosException {
        IllegalStateException insertFailure =
            new IllegalStateException("unknown insert outcome");
        IllegalStateException readFailure = new IllegalStateException("read failed");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null).thenThrow(readFailure);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class))).thenThrow(insertFailure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        assertSame(insertFailure, exception.getCause());
        assertSame(readFailure, insertFailure.getSuppressed()[0]);
        verify(storageService).save(prepared);
    }
    
    @Test
    void testPostCommitReadFailureDoesNotRollbackCommittedState() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(RESOURCE_ID);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testPostCommitMissingVersionMapsToServerError() throws NacosException {
        stubSuccessfulWritesForPostCommit(storedResource(), null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testPostCommitInvalidVersionSummaryMapsToServerError() throws NacosException {
        AiResourceVersion invalidVersion = storedVersion();
        invalidVersion.setStorage("{}");
        stubSuccessfulWritesForPostCommit(storedResource(), invalidVersion);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testUnexpectedPersistenceFailureIsMappedToNacosServerError() throws NacosException {
        IllegalStateException persistenceFailure =
            new IllegalStateException("resource lookup failed");
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenThrow(persistenceFailure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createInitialDraft(agent, initialDraft));
        
        assertServerError(exception);
        assertSame(persistenceFailure, exception.getCause());
        verifyNoInteractions(versionPersistService);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
    }
    
    @Test
    void testUpdateDraftOverwritesStableStorageThenUpdatesVersionRowAndReturnsDetail()
        throws NacosException {
        AgentVersionContent replacement = replacementContent();
        PreparedAgentVersionWrite replacementWrite = replacementWrite(replacement);
        AiResourceVersion updated = updatedVersion(replacementWrite, "Updated draft");
        stubDraftUpdatePreparation(replacementWrite);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion(), updated);
        when(versionPersistService.updateStorageAndDesc(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, updated.getStorage(),
            "Updated draft")).thenReturn(1);
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(replacement);
        
        AgentVersionDetail result = service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
            replacement.getCallInterfaces(), "Updated draft");
        
        assertEquals("0.4", result.getCallInterfaces().get(0).getProtocolVersion());
        assertEquals("Updated draft", result.getChangeDescription());
        assertEquals(replacementWrite.getDescriptor().getContentDigest(),
            result.getContentDigest());
        InOrder order = inOrder(versionPersistService, storageService);
        order.verify(storageService).prepare(any(AgentVersionStorageDescriptor.class),
            any(AgentVersionContent.class));
        order.verify(storageService).save(replacementWrite);
        order.verify(versionPersistService).updateStorageAndDesc(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, updated.getStorage(),
            "Updated draft");
        order.verify(storageService).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testUpdateDraftRejectsNonDraftBeforePreparingOrWritingStorage() throws NacosException {
        AiResourceVersion reviewing = storedVersion();
        reviewing.setStatus(AiConstants.Agent.VERSION_STATUS_REVIEWING);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(reviewing);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacementContent().getCallInterfaces(), "Updated draft"));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), exception.getDetailErrCode());
        verifyNoInteractions(storageService);
        verify(versionPersistService, never()).updateStorageAndDesc(anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testUpdateDraftRejectsVersionThatIsNotCurrentDraft() throws NacosException {
        AiResource resource = storedResource();
        resource.setVersionInfo(serializeVersionInfo("2.0.0"));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacementContent().getCallInterfaces(), "Updated draft"));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), exception.getDetailErrCode());
        verifyNoInteractions(storageService);
        verify(versionPersistService, never()).updateStorageAndDesc(anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testUpdateDraftRejectsMissingVersion() throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacementContent().getCallInterfaces(), "Updated draft"));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testUpdateDraftDoesNotUpdateRowWhenStorageSaveFails() throws NacosException {
        NacosException storageFailure =
            new NacosException(NacosException.SERVER_ERROR, "save failed");
        AgentVersionContent replacement = replacementContent();
        PreparedAgentVersionWrite replacementWrite = replacementWrite(replacement);
        stubDraftUpdatePreparation(replacementWrite);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        doThrow(storageFailure).when(storageService).save(replacementWrite);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacement.getCallInterfaces(), "Updated draft"));
        
        assertSame(storageFailure, exception);
        verify(versionPersistService, never()).updateStorageAndDesc(anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testUpdateDraftFailsWhenVersionRowIsNotUpdated() throws NacosException {
        AgentVersionContent replacement = replacementContent();
        PreparedAgentVersionWrite replacementWrite = replacementWrite(replacement);
        AiResourceVersion updated = updatedVersion(replacementWrite, "Updated draft");
        stubDraftUpdatePreparation(replacementWrite);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        when(versionPersistService.updateStorageAndDesc(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, updated.getStorage(),
            "Updated draft")).thenReturn(0);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacement.getCallInterfaces(), "Updated draft"));
        
        assertServerError(exception);
        verify(storageService).save(replacementWrite);
        verify(storageService, never()).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testUpdateDraftRetriesAfterVersionRowUpdateFailure() throws NacosException {
        AgentVersionContent replacement = replacementContent();
        PreparedAgentVersionWrite replacementWrite = replacementWrite(replacement);
        AiResourceVersion updated = updatedVersion(replacementWrite, "Updated draft");
        stubDraftUpdatePreparation(replacementWrite);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion(),
                storedVersion(), updated);
        when(versionPersistService.updateStorageAndDesc(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, updated.getStorage(),
            "Updated draft")).thenReturn(0, 1);
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(replacement);
        
        assertThrows(NacosApiException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacement.getCallInterfaces(), "Updated draft"));
        
        AgentVersionDetail result = service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
            replacement.getCallInterfaces(), "Updated draft");
        
        assertEquals("Updated draft", result.getChangeDescription());
        assertEquals(replacementWrite.getDescriptor().getContentDigest(),
            result.getContentDigest());
        verify(storageService, times(2)).save(replacementWrite);
        verify(versionPersistService, times(2)).updateStorageAndDesc(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, updated.getStorage(),
            "Updated draft");
    }
    
    @Test
    void testUpdateDraftValidatesCommandBeforePersistence() {
        assertThrows(IllegalArgumentException.class,
            () -> service.updateDraft("", AGENT_NAME, VERSION,
                replacementContent().getCallInterfaces(), "Updated draft"));
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testUpdateDraftRejectsEmptyCallInterfacesBeforePersistence() {
        assertThrows(IllegalArgumentException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                Collections.<AgentCallInterface>emptyList(), "Updated draft"));
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testUpdateDraftRejectsLongDescriptionBeforePersistence() {
        AgentVersionContent replacement = replacementContent();
        
        assertThrows(IllegalArgumentException.class,
            () -> service.updateDraft(NAMESPACE_ID, AGENT_NAME, VERSION,
                replacement.getCallInterfaces(), repeat('x', 2049)));
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testGetAgentUsesStrictStoredProjection() throws NacosException {
        AiResource row = storedResource();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(row);
        
        Agent result = service.getAgent(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(AGENT_NAME, result.getAgentName());
        assertEquals("Nacos Agent Display", result.getDisplayName());
        assertEquals(Arrays.asList("assistant", "demo"), result.getTags());
        assertEquals(VERSION, result.getVersionInfo().getEditingVersion());
        assertEquals(0, result.getVersionCatalog().getOnlineVersions().size());
        assertEquals(3L, result.getMetaVersion());
        assertEquals(1000L, result.getCreateTime());
        assertEquals(2000L, result.getUpdateTime());
    }
    
    @Test
    void testGetAgentRejectsCaseInsensitiveFalseMatchAndCorruptMetadata() {
        AiResource caseMismatch = storedResource();
        caseMismatch.setName("nacos agent");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(caseMismatch);
        
        NacosApiException notFound = assertThrows(NacosApiException.class,
            () -> service.getAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertEquals(NacosException.NOT_FOUND, notFound.getErrCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), notFound.getDetailErrCode());
        
        AiResource corrupt = storedResource();
        corrupt.setExt("{}");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(corrupt);
        
        NacosApiException serverError = assertThrows(NacosApiException.class,
            () -> service.getAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertEquals(NacosException.SERVER_ERROR, serverError.getErrCode());
    }
    
    @Test
    void testGetAgentReturnsUserDefinedTagPrefixes() throws NacosException {
        AiResource row = storedResource();
        row.setBizTags(JacksonUtils.toJson(
            Arrays.asList("assistant", "__nacos.agent.internal")));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(row);
        
        Agent result = service.getAgent(NAMESPACE_ID, AGENT_NAME);
        
        assertEquals(Arrays.asList("assistant", "__nacos.agent.internal"), result.getTags());
    }
    
    @Test
    void testGetAgentNormalizesNullAndBlankPersistedTags() throws NacosException {
        AiResource nullTags = storedResource();
        nullTags.setBizTags(null);
        AiResource blankTags = storedResource();
        blankTags.setBizTags("  ");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(nullTags, blankTags);
        
        assertTrue(service.getAgent(NAMESPACE_ID, AGENT_NAME).getTags().isEmpty());
        assertTrue(service.getAgent(NAMESPACE_ID, AGENT_NAME).getTags().isEmpty());
    }
    
    @Test
    void testGetAgentOverviewUsesBoundedVersionSummaryPage() throws NacosException {
        Page<AiResourceVersion> versions =
            versionPage(Collections.singletonList(storedVersion()), 1, 1);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 8)).thenReturn(versions);
        
        AgentOverview result = service.getAgentOverview(NAMESPACE_ID, AGENT_NAME, 8);
        
        assertEquals(AGENT_NAME, result.getAgent().getAgentName());
        assertEquals(1, result.getVersionPage().getTotalCount());
        assertEquals(VERSION, result.getVersionPage().getPageItems().get(0).getVersion());
        verify(resourcePersistService, times(2)).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testTryUpdateAgentUsesAuthorizedRowAndMergesLatestVersionFacts()
        throws NacosException {
        Agent replacement = newAgent();
        replacement.setDisplayName("Updated Agent");
        replacement.setDescription("Updated description");
        replacement.setTags(Collections.singletonList("updated"));
        replacement.setOwner("bob");
        replacement.setScope("PUBLIC");
        AiResource concurrentRow = storedResource();
        ResourceVersionInfo concurrentVersionInfo = JacksonUtils.toObj(
            concurrentRow.getVersionInfo(), ResourceVersionInfo.class);
        concurrentVersionInfo.setEditingVersion("1.2.0");
        concurrentVersionInfo.setOnlineCnt(1);
        concurrentVersionInfo.setLabels(
            Collections.singletonMap(AiResourceConstants.LABEL_LATEST, "1.1.0"));
        concurrentRow.setVersionInfo(JacksonUtils.toJson(concurrentVersionInfo));
        concurrentRow.setMetaVersion(4L);
        AgentResourceExt concurrentExt =
            AgentResourceExtSerializer.deserialize(concurrentRow.getExt());
        concurrentExt.setVersionCatalog(AgentVersionCatalogBuilder.build(
            Collections.singletonMap("1.1.0", Collections.singletonList("a2a")),
            Collections.singletonMap(AiResourceConstants.LABEL_LATEST, "1.1.0"))
            .getVersionCatalog());
        concurrentRow.setExt(AgentResourceExtSerializer.serialize(concurrentExt));
        AiResource updatedRow = storedResource();
        updatedRow.setDesc(replacement.getDescription());
        updatedRow.setBizTags(JacksonUtils.toJson(replacement.getTags()));
        updatedRow.setOwner(concurrentRow.getOwner());
        updatedRow.setScope(concurrentRow.getScope());
        updatedRow.setVersionInfo(concurrentRow.getVersionInfo());
        updatedRow.setMetaVersion(5L);
        AgentResourceExt updatedExt =
            AgentResourceExtSerializer.deserialize(concurrentRow.getExt());
        updatedExt.setDisplayName(replacement.getDisplayName());
        updatedRow.setExt(AgentResourceExtSerializer.serialize(updatedExt));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(updatedRow);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(4L),
            any(AiResource.class))).thenReturn(true);
        
        Agent result = service.tryUpdateAgent(replacement, concurrentRow);
        
        assertEquals("Updated Agent", result.getDisplayName());
        assertEquals("Updated description", result.getDescription());
        assertEquals(Collections.singletonList("updated"), result.getTags());
        assertEquals("1.2.0", result.getVersionInfo().getEditingVersion());
        assertEquals("1.1.0", result.getVersionCatalog().getLatestVersion());
        assertEquals(5L, result.getMetaVersion());
        ArgumentCaptor<AiResource> updateCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID),
            eq(AGENT_NAME), eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(4L),
            updateCaptor.capture());
        AiResource update = updateCaptor.getValue();
        assertEquals("1.2.0", JacksonUtils.toObj(update.getVersionInfo(),
            ResourceVersionInfo.class).getEditingVersion());
        assertEquals("1.1.0", AgentResourceExtSerializer.deserialize(
            update.getExt()).getVersionCatalog().getLatestVersion());
        assertEquals("Updated description", update.getDesc());
        assertEquals(concurrentRow.getOwner(), update.getOwner());
        assertEquals(concurrentRow.getScope(), update.getScope());
        assertEquals(concurrentRow.getOwner(), result.getOwner());
        assertEquals(concurrentRow.getScope(), result.getScope());
    }
    
    @Test
    void testTryUpdateAgentReturnsNullWhenCasDoesNotMatch() throws NacosException {
        Agent replacement = newAgent();
        AiResource current = storedResource();
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L),
            any(AiResource.class))).thenReturn(false);
        
        assertNull(service.tryUpdateAgent(replacement, current));
        
        verify(resourcePersistService, never()).find(anyString(), anyString(), anyString());
    }
    
    @Test
    void testListAgentsMapsSummariesAndRetainsPageMetadata() throws NacosException {
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId(NAMESPACE_ID);
        Page<AiResource> source = new Page<AiResource>();
        source.setPageNumber(2);
        source.setTotalCount(21);
        source.setPagesAvailable(3);
        source.setPageItems(Collections.singletonList(storedResource()));
        when(resourcePersistService.list(condition, 2, 10)).thenReturn(source);
        
        Page<AgentSummary> result = service.listAgents(condition, 2, 10);
        
        assertEquals(2, result.getPageNumber());
        assertEquals(21, result.getTotalCount());
        assertEquals(3, result.getPagesAvailable());
        assertEquals(AGENT_NAME, result.getPageItems().get(0).getAgentName());
        assertEquals(VERSION,
            result.getPageItems().get(0).getVersionInfo().getEditingVersion());
        verifyNoInteractions(versionPersistService, storageService);
    }
    
    @Test
    void testGetAgentRejectsMalformedAndNullJsonPersistedTags() {
        AiResource malformedTags = storedResource();
        malformedTags.setBizTags("[");
        AiResource nullJsonTags = storedResource();
        nullJsonTags.setBizTags("null");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(malformedTags, nullJsonTags);
        
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.getAgent(NAMESPACE_ID, AGENT_NAME)));
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.getAgent(NAMESPACE_ID, AGENT_NAME)));
    }
    
    @Test
    void testGetAgentRejectsNonStringPersistedTag() {
        AiResource row = storedResource();
        row.setBizTags("[1]");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(row);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.getAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testReadProjectionsRejectNullDatabaseTimestamps() throws NacosException {
        AiResource invalidResource = storedResource();
        invalidResource.setGmtCreate(null);
        AiResourceVersion invalidVersion = storedVersion();
        invalidVersion.setGmtModified(null);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(invalidResource, storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(invalidVersion);
        when(storageService.load(any(AgentVersionStorageDescriptor.class))).thenReturn(content);
        
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.getAgent(NAMESPACE_ID, AGENT_NAME)));
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION)));
    }
    
    @Test
    void testGetExactVersionLoadsAndVerifiesContentOnce() throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        when(storageService.load(any(AgentVersionStorageDescriptor.class))).thenReturn(content);
        
        AgentVersionDetail result = service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        assertEquals(AGENT_NAME, result.getAgentName());
        assertEquals(VERSION, result.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_DRAFT, result.getStatus());
        assertEquals("a2a", result.getCallInterfaces().get(0).getProtocol());
        assertEquals(prepared.getDescriptor().getContentDigest(), result.getContentDigest());
        assertEquals(3000L, result.getCreateTime());
        assertEquals(4000L, result.getUpdateTime());
        verify(storageService).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testGetExactVersionDoesNotExposeOrphanVersion() throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        verifyNoInteractions(versionPersistService);
        verify(storageService, never()).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testGetExactVersionRejectsCaseMismatchAndInvalidDescriptor() throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        AiResourceVersion mismatch = storedVersion();
        mismatch.setVersion("1.0.0-rc1");
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(mismatch);
        
        NacosApiException notFound = assertThrows(NacosApiException.class,
            () -> service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertEquals(NacosException.NOT_FOUND, notFound.getErrCode());
        
        AiResourceVersion invalid = storedVersion();
        invalid.setStorage("{}");
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(invalid);
        
        NacosApiException serverError = assertThrows(NacosApiException.class,
            () -> service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertEquals(NacosException.SERVER_ERROR, serverError.getErrCode());
        verify(storageService, never()).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testGetExactVersionRejectsInvalidLoadedVersionAndContent() throws NacosException {
        AiResourceVersion invalidStatus = storedVersion();
        invalidStatus.setStatus("invalid");
        AgentVersionContent invalidContent =
            new AgentVersionContent(Collections.<AgentCallInterface>emptyList());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(invalidStatus,
                storedVersion());
        when(storageService.load(any(AgentVersionStorageDescriptor.class))).thenReturn(content,
            invalidContent);
        
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION)));
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.getAgentVersion(NAMESPACE_ID, AGENT_NAME, VERSION)));
    }
    
    @Test
    void testCreateDraftPersistsDirectContentAndClaimsEditingVersion() throws NacosException {
        String draftVersion = "1.1.0";
        AgentVersionDetail draft = newDraft(draftVersion, "json-rpc");
        AgentVersionContent draftContent = new AgentVersionContent(draft.getCallInterfaces());
        PreparedAgentVersionWrite draftWrite = prepareWrite(draftVersion, draftContent);
        AiResourceVersion persistedDraft =
            storedVersion(draftVersion, AiConstants.Agent.VERSION_STATUS_DRAFT, draftWrite);
        AtomicReference<AiResource> currentResource =
            new AtomicReference<AiResource>(resourceWithoutWorkingVersions());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(
                invocation -> currentResource.get());
        when(storageService.prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(draftVersion),
            any(AgentVersionContent.class))).thenReturn(draftWrite);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, draftVersion)).thenReturn(null, persistedDraft);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID + 1);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class)))
            .thenAnswer(invocation -> {
                currentResource.set(applyMetaUpdate(currentResource.get(),
                    invocation.<AiResource>getArgument(4), 4L));
                return true;
            });
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(draftContent);
        
        AgentVersionDetail result =
            service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null);
        
        assertEquals(draftVersion, result.getVersion());
        assertEquals("json-rpc", result.getCallInterfaces().get(0).getProtocol());
        assertEquals(draftWrite.getDescriptor().getContentDigest(), result.getContentDigest());
        ResourceVersionInfo versionInfo = JacksonUtils.toObj(
            currentResource.get().getVersionInfo(), ResourceVersionInfo.class);
        assertEquals(draftVersion, versionInfo.getEditingVersion());
        InOrder order = inOrder(versionPersistService, storageService, resourcePersistService);
        order.verify(versionPersistService).insert(any(AiResourceVersion.class));
        order.verify(storageService).save(draftWrite);
        order.verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
    }
    
    @Test
    void testCreateDraftCopiesExactBasedOnVersion() throws NacosException {
        String draftVersion = "1.1.0";
        AgentVersionDetail draft = newDraft(draftVersion, null);
        AgentVersionContent sourceContent = new AgentVersionContent(
            newInitialDraft().getCallInterfaces());
        PreparedAgentVersionWrite draftWrite = prepareWrite(draftVersion, sourceContent);
        AiResourceVersion persistedDraft =
            storedVersion(draftVersion, AiConstants.Agent.VERSION_STATUS_DRAFT, draftWrite);
        AtomicReference<AiResource> currentResource =
            new AtomicReference<AiResource>(resourceWithoutWorkingVersions());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(
                invocation -> currentResource.get());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, draftVersion)).thenReturn(null, persistedDraft);
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(sourceContent);
        when(storageService.prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(draftVersion),
            any(AgentVersionContent.class))).thenReturn(draftWrite);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID + 1);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class)))
            .thenAnswer(invocation -> {
                currentResource.set(applyMetaUpdate(currentResource.get(),
                    invocation.<AiResource>getArgument(4), 4L));
                return true;
            });
        
        AgentVersionDetail result =
            service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, VERSION);
        
        assertEquals(draftVersion, result.getVersion());
        assertEquals(sourceContent.getCallInterfaces(), result.getCallInterfaces());
        ArgumentCaptor<AgentVersionContent> contentCaptor =
            ArgumentCaptor.forClass(AgentVersionContent.class);
        verify(storageService).prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(draftVersion),
            contentCaptor.capture());
        assertEquals(sourceContent.getCallInterfaces(),
            contentCaptor.getValue().getCallInterfaces());
        verify(storageService, times(2)).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testCreateDraftRejectsAnotherWorkingDraftBeforeContentPersistence()
        throws NacosException {
        AgentVersionDetail draft = newDraft("1.1.0", "json-rpc");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null));
        
        assertConflict(exception);
        verifyNoInteractions(versionPersistService, storageService);
        verify(resourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any(AiResource.class));
    }
    
    @Test
    void testCreateDraftValidatesIdentityStatusAndReadOnlyFieldsBeforePersistence() {
        assertInvalidCreateDraft(null, null);
        
        AgentVersionDetail namespaceMismatch = newDraft("1.1.0", "a2a");
        namespaceMismatch.setNamespaceId("other");
        assertInvalidCreateDraft(namespaceMismatch, null);
        
        AgentVersionDetail nameMismatch = newDraft("1.1.0", "a2a");
        nameMismatch.setAgentName("Other Agent");
        assertInvalidCreateDraft(nameMismatch, null);
        
        AgentVersionDetail invalidStatus = newDraft("1.1.0", "a2a");
        invalidStatus.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        assertInvalidCreateDraft(invalidStatus, null);
        
        AgentVersionDetail contentDigest = newDraft("1.1.0", "a2a");
        contentDigest.setContentDigest("sha256:" + repeat('a', 64));
        assertInvalidCreateDraft(contentDigest, null);
        
        AgentVersionDetail createTime = newDraft("1.1.0", "a2a");
        createTime.setCreateTime(1L);
        assertInvalidCreateDraft(createTime, null);
        
        AgentVersionDetail updateTime = newDraft("1.1.0", "a2a");
        updateTime.setUpdateTime(1L);
        assertInvalidCreateDraft(updateTime, null);
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testCreateDraftRequiresExactlyOneDistinctContentSource() {
        assertInvalidCreateDraft(newDraft("1.1.0", "a2a"), VERSION);
        assertInvalidCreateDraft(newDraft("1.1.0", null), null);
        assertInvalidCreateDraft(newDraft(VERSION, null), VERSION);
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testCreateDraftAcceptsIdempotentEditingPointer() throws NacosException {
        String draftVersion = "1.1.0";
        AgentVersionDetail draft = newDraft(draftVersion, "a2a");
        AgentVersionContent draftContent = new AgentVersionContent(draft.getCallInterfaces());
        PreparedAgentVersionWrite draftWrite = prepareWrite(draftVersion, draftContent);
        AiResourceVersion persistedDraft =
            storedVersion(draftVersion, AiConstants.Agent.VERSION_STATUS_DRAFT, draftWrite);
        AiResource resource = lifecycleResource(draftVersion, null,
            Collections.<String, String>emptyMap(), 3L);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resource);
        when(storageService.prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(draftVersion),
            any(AgentVersionContent.class))).thenReturn(draftWrite);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, draftVersion)).thenReturn(null, persistedDraft);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID + 1);
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(draftContent);
        
        AgentVersionDetail result =
            service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null);
        
        assertEquals(draftVersion, result.getVersion());
        verify(resourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any(AiResource.class));
    }
    
    @Test
    void testCreateDraftRejectsEditingPointerClaimedAfterContentWrite() throws NacosException {
        String draftVersion = "1.1.0";
        AgentVersionDetail draft = newDraft(draftVersion, "a2a");
        PreparedAgentVersionWrite draftWrite =
            prepareWrite(draftVersion, new AgentVersionContent(draft.getCallInterfaces()));
        AiResource unclaimed = resourceWithoutWorkingVersions();
        AiResource claimed = lifecycleResource("2.0.0", null,
            Collections.<String, String>emptyMap(), 4L);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(unclaimed, claimed);
        when(storageService.prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(draftVersion),
            any(AgentVersionContent.class))).thenReturn(draftWrite);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, draftVersion)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID + 1);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null));
        
        assertConflict(exception);
        verify(storageService).save(draftWrite);
        verify(resourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any(AiResource.class));
    }
    
    @Test
    void testCreateDraftFailsAfterEditingPointerCasRetryExhaustion()
        throws NacosException {
        String draftVersion = "1.1.0";
        AgentVersionDetail draft = newDraft(draftVersion, "a2a");
        PreparedAgentVersionWrite draftWrite =
            prepareWrite(draftVersion, new AgentVersionContent(draft.getCallInterfaces()));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resourceWithoutWorkingVersions());
        when(storageService.prepare(eq(NAMESPACE_ID), eq(AGENT_NAME), eq(draftVersion),
            any(AgentVersionContent.class))).thenReturn(draftWrite);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, draftVersion)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID + 1);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, null));
        
        assertConflict(exception);
        verify(resourcePersistService, times(AiResourceConstants.MAX_WORKING_VERSION_RETRY))
            .updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
                eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
    }
    
    @Test
    void testDeleteDraftClearsEditingPointerBeforeDeletingRowAndContent()
        throws NacosException {
        AiResource resource = storedResource();
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resource);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class)))
            .thenReturn(true);
        when(versionPersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(1);
        
        service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        ArgumentCaptor<AiResource> updateCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), updateCaptor.capture());
        ResourceVersionInfo versionInfo = JacksonUtils.toObj(
            updateCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertNull(versionInfo.getEditingVersion());
        InOrder order = inOrder(versionPersistService, resourcePersistService, storageService);
        order.verify(versionPersistService).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION);
        order.verify(resourcePersistService).find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        order.verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
        order.verify(versionPersistService).delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION);
        order.verify(storageService).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testDeleteDraftRejectsNonDraftWithoutChangingMetadataOrStorage()
        throws NacosException {
        AiResourceVersion online = storedVersion();
        online.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(online);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), exception.getDetailErrCode());
        verifyNoInteractions(resourcePersistService, storageService);
        verify(versionPersistService, never()).delete(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void testDeleteDraftRestoresEditingPointerWhenVersionRowDeleteMisses()
        throws NacosException {
        AiResource clearedPointer = lifecycleResource(null, null,
            Collections.<String, String>emptyMap(), 4L);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion(),
                storedVersion());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource(), clearedPointer);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), anyLong(), any(AiResource.class)))
            .thenReturn(true);
        when(versionPersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(0);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertServerError(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
        ArgumentCaptor<AiResource> updates = ArgumentCaptor.forClass(AiResource.class);
        verify(resourcePersistService, times(2)).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), anyLong(), updates.capture());
        assertNull(JacksonUtils.toObj(updates.getAllValues().get(0).getVersionInfo(),
            ResourceVersionInfo.class).getEditingVersion());
        assertEquals(VERSION,
            JacksonUtils.toObj(updates.getAllValues().get(1).getVersionInfo(),
                ResourceVersionInfo.class).getEditingVersion());
    }
    
    @Test
    void testDeleteDraftDoesNotRestorePointerWhenVersionWasDeletedConcurrently()
        throws NacosException {
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion(),
                (AiResourceVersion) null);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class)))
            .thenReturn(true);
        when(versionPersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(0);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertServerError(exception);
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testDeleteDraftRejectsVersionThatIsNotEditingPointer() throws NacosException {
        AiResource anotherDraft = lifecycleResource("2.0.0", null,
            Collections.<String, String>emptyMap(), 3L);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(anotherDraft);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), exception.getDetailErrCode());
        verify(versionPersistService, never()).delete(anyString(), anyString(), anyString(),
            anyString());
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testDeleteDraftFailsAfterEditingPointerCasRetryExhaustion()
        throws NacosException {
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteDraft(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertConflict(exception);
        verify(resourcePersistService, times(AiResourceConstants.MAX_WORKING_VERSION_RETRY))
            .updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
                eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
        verify(versionPersistService, never()).delete(anyString(), anyString(), anyString(),
            anyString());
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testDeleteAgentScansEveryVersionPageAndCleansEveryContentObject()
        throws NacosException {
        List<AiResourceVersion> firstPageRows = new ArrayList<AiResourceVersion>();
        for (int i = 0; i < 100; i++) {
            String version = "1.0." + i;
            AgentVersionContent versionContent = contentWithProtocol("a2a");
            firstPageRows.add(storedVersion(version, AiConstants.Agent.VERSION_STATUS_ONLINE,
                prepareWrite(version, versionContent)));
        }
        String lastVersion = "1.0.100";
        AiResourceVersion lastRow = storedVersion(lastVersion,
            AiConstants.Agent.VERSION_STATUS_OFFLINE,
            prepareWrite(lastVersion, contentWithProtocol("json-rpc")));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 100))
            .thenReturn(versionPage(firstPageRows, 101, 2));
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 2, 100))
            .thenReturn(versionPage(Collections.singletonList(lastRow), 101, 2));
        when(resourcePersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(1);
        when(versionPersistService.deleteByNameAndType(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(101);
        
        service.deleteAgent(NAMESPACE_ID, AGENT_NAME);
        
        verify(versionPersistService).list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 100);
        verify(versionPersistService).list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 2, 100);
        verify(resourcePersistService).delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        verify(versionPersistService).deleteByNameAndType(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        InOrder order = inOrder(resourcePersistService, versionPersistService, storageService);
        order.verify(resourcePersistService).delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        order.verify(versionPersistService).deleteByNameAndType(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        order.verify(storageService, times(101))
            .delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testDeleteAgentValidatesEveryDescriptorBeforeDeletingAnyState()
        throws NacosException {
        AiResourceVersion valid = storedVersion();
        AiResourceVersion invalid = storedVersion();
        invalid.setVersion("1.1.0");
        invalid.setStorage("{}");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 100))
            .thenReturn(versionPage(Arrays.asList(valid, invalid), 2, 1));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertServerError(exception);
        verify(resourcePersistService, never()).delete(anyString(), anyString(), anyString());
        verify(versionPersistService, never()).deleteByNameAndType(anyString(), anyString(),
            anyString());
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testDeleteAgentStopsBeforeVersionAndStorageCleanupWhenResourceDeleteMisses()
        throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 100))
            .thenReturn(versionPage(Collections.singletonList(storedVersion()), 1, 1));
        when(resourcePersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(0);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertServerError(exception);
        verify(versionPersistService, never()).deleteByNameAndType(anyString(), anyString(),
            anyString());
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testDeleteAgentDoesNotDeleteStorageWhenVersionRowsAreNotDeleted()
        throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 100))
            .thenReturn(versionPage(Collections.singletonList(storedVersion()), 1, 1));
        when(resourcePersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(1);
        when(versionPersistService.deleteByNameAndType(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(0);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertServerError(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testDeleteAgentReportsSingleStorageCleanupFailure() throws NacosException {
        NacosException storageFailure =
            new NacosException(NacosException.SERVER_ERROR, "storage delete failed");
        stubDeleteAgentRows(Collections.singletonList(storedVersion()));
        doThrow(storageFailure).when(storageService)
            .delete(any(AgentVersionStorageDescriptor.class));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertServerError(exception);
        assertSame(storageFailure, exception.getCause());
        assertEquals(0, storageFailure.getSuppressed().length);
    }
    
    @Test
    void testDeleteAgentSuppressesAdditionalStorageCleanupFailures() throws NacosException {
        AiResourceVersion secondVersion = storedVersion("1.1.0",
            AiConstants.Agent.VERSION_STATUS_OFFLINE,
            prepareWrite("1.1.0", contentWithProtocol("json-rpc")));
        NacosException firstFailure =
            new NacosException(NacosException.SERVER_ERROR, "first delete failed");
        NacosException secondFailure =
            new NacosException(NacosException.SERVER_ERROR, "second delete failed");
        stubDeleteAgentRows(Arrays.asList(storedVersion(), secondVersion));
        doThrow(firstFailure, secondFailure).when(storageService)
            .delete(any(AgentVersionStorageDescriptor.class));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.deleteAgent(NAMESPACE_ID, AGENT_NAME));
        
        assertServerError(exception);
        assertSame(firstFailure, exception.getCause());
        assertEquals(1, firstFailure.getSuppressed().length);
        assertSame(secondFailure, firstFailure.getSuppressed()[0]);
    }
    
    @Test
    void testVersionSummaryReadsNeverLoadVersionContent() throws NacosException {
        AiResourceVersion online = storedVersion();
        online.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        Page<AiResourceVersion> sourcePage =
            versionPage(Collections.singletonList(online), 1, 1);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 2, 20)).thenReturn(sourcePage);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(online);
        
        Page<AgentVersionSummary> result = service.listAgentVersions(NAMESPACE_ID, AGENT_NAME,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 2, 20);
        AgentVersionSummary exact =
            service.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION);
        
        assertEquals(1, result.getTotalCount());
        assertEquals(VERSION, result.getPageItems().get(0).getVersion());
        assertEquals(prepared.getDescriptor().getContentDigest(),
            result.getPageItems().get(0).getContentDigest());
        assertEquals(VERSION, exact.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, exact.getStatus());
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testListVersionSummariesHandlesNullPageWithoutStorageReads()
        throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 3, 20)).thenReturn(null);
        
        Page<AgentVersionSummary> result =
            service.listAgentVersions(NAMESPACE_ID, AGENT_NAME, null, 3, 20);
        
        assertEquals(3, result.getPageNumber());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getPagesAvailable());
        assertTrue(result.getPageItems().isEmpty());
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testListVersionSummariesRejectsInvalidStoredDescriptor() throws NacosException {
        AiResourceVersion invalid = storedVersion();
        invalid.setStorage("{}");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 20))
            .thenReturn(versionPage(Collections.singletonList(invalid), 1, 1));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.listAgentVersions(NAMESPACE_ID, AGENT_NAME, null, 1, 20));
        
        assertServerError(exception);
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testGetVersionSummaryRejectsInvalidStoredDescriptor() throws NacosException {
        AiResourceVersion invalid = storedVersion();
        invalid.setStorage("{}");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(invalid);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.getAgentVersionSummary(NAMESPACE_ID, AGENT_NAME, VERSION));
        
        assertServerError(exception);
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testLifecycleRowUpdatesRequireOneAffectedVersion() throws NacosException {
        String pipelineInfo = "{\"executionId\":\"pipeline-1\"}";
        when(versionPersistService.updateStatus(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION,
            AiConstants.Agent.VERSION_STATUS_REVIEWING)).thenReturn(1);
        when(versionPersistService.updatePublishPipelineInfo(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, pipelineInfo)).thenReturn(1);
        
        service.updateVersionStatus(NAMESPACE_ID, AGENT_NAME, VERSION,
            AiConstants.Agent.VERSION_STATUS_REVIEWING);
        service.updatePublishPipelineInfo(NAMESPACE_ID, AGENT_NAME, VERSION, pipelineInfo);
        
        verify(versionPersistService).updateStatus(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION,
            AiConstants.Agent.VERSION_STATUS_REVIEWING);
        verify(versionPersistService).updatePublishPipelineInfo(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, pipelineInfo);
    }
    
    @Test
    void testLifecycleRowUpdatesFailWhenVersionWasNotUpdated() {
        String pipelineInfo = "{\"executionId\":\"pipeline-1\"}";
        when(versionPersistService.updateStatus(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION,
            AiConstants.Agent.VERSION_STATUS_REVIEWING)).thenReturn(0);
        when(versionPersistService.updatePublishPipelineInfo(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION, pipelineInfo)).thenReturn(0);
        
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.updateVersionStatus(NAMESPACE_ID, AGENT_NAME, VERSION,
                AiConstants.Agent.VERSION_STATUS_REVIEWING)));
        assertServerError(assertThrows(NacosApiException.class,
            () -> service.updatePublishPipelineInfo(NAMESPACE_ID, AGENT_NAME, VERSION,
                pipelineInfo)));
    }
    
    @Test
    void testSynchronizeDerivedStateWritesLifecycleAndCatalogInOneCas()
        throws NacosException {
        String reviewedVersion = "1.0.1";
        String latestVersion = "1.1.0";
        AiResource resource = lifecycleResource(VERSION, reviewedVersion,
            Collections.singletonMap(AiResourceConstants.LABEL_LATEST, "0.9.0"), 3L);
        AgentVersionContent reviewedContent = contentWithProtocol("json-rpc");
        AgentVersionContent latestContent = contentWithProtocol("a2a");
        AiResourceVersion reviewed = storedVersion(reviewedVersion,
            AiConstants.Agent.VERSION_STATUS_ONLINE,
            prepareWrite(reviewedVersion, reviewedContent));
        AiResourceVersion latest = storedVersion(latestVersion,
            AiConstants.Agent.VERSION_STATUS_ONLINE,
            prepareWrite(latestVersion, latestContent));
        AtomicReference<AiResource> currentResource =
            new AtomicReference<AiResource>(resource);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenAnswer(
                invocation -> currentResource.get());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, reviewedVersion)).thenReturn(reviewed);
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100))
            .thenReturn(versionPage(Arrays.asList(reviewed, latest), 2, 1));
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(reviewedContent, latestContent);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class)))
            .thenAnswer(invocation -> {
                currentResource.set(applyMetaUpdate(resource,
                    invocation.<AiResource>getArgument(4), 4L));
                return true;
            });
        Map<String, String> labels =
            Collections.singletonMap("stable", reviewedVersion);
        
        Agent result = service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME,
            latestVersion, labels, VERSION, reviewedVersion);
        assertEquals(2, result.getVersionInfo().getOnlineCnt());
        assertEquals(latestVersion, result.getVersionCatalog().getLatestVersion());
        
        ArgumentCaptor<AiResource> updateCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), updateCaptor.capture());
        ResourceVersionInfo versionInfo = JacksonUtils.toObj(
            updateCaptor.getValue().getVersionInfo(), ResourceVersionInfo.class);
        assertNull(versionInfo.getEditingVersion());
        assertNull(versionInfo.getReviewingVersion());
        assertEquals(2, versionInfo.getOnlineCnt());
        assertEquals(latestVersion,
            versionInfo.getLabels().get(AiResourceConstants.LABEL_LATEST));
        assertEquals(reviewedVersion, versionInfo.getLabels().get("stable"));
        AgentResourceExt resourceExt =
            AgentResourceExtSerializer.deserialize(updateCaptor.getValue().getExt());
        assertEquals(latestVersion, resourceExt.getVersionCatalog().getLatestVersion());
        assertEquals(2, resourceExt.getVersionCatalog().getOnlineVersions().size());
        assertEquals(latestVersion,
            resourceExt.getVersionCatalog().getOnlineVersions().get(0).getVersion());
        assertEquals(Collections.singletonList("a2a"),
            resourceExt.getVersionCatalog().getOnlineVersions().get(0).getProtocols());
        assertEquals(reviewedVersion,
            resourceExt.getVersionCatalog().getOnlineVersions().get(1).getVersion());
        assertEquals(Collections.singletonList("stable"),
            resourceExt.getVersionCatalog().getOnlineVersions().get(1).getLabels());
    }
    
    @Test
    void testSynchronizeDerivedStateRebuildsFactsAfterCasRetry() throws NacosException {
        String latestVersion = "1.1.0";
        AiResource first = lifecycleResource(null, null,
            Collections.<String, String>emptyMap(), 3L);
        AiResource second = lifecycleResource(null, null,
            Collections.<String, String>emptyMap(), 4L);
        AiResource finalResource = lifecycleResource(null, null,
            Collections.singletonMap(AiResourceConstants.LABEL_LATEST, latestVersion), 5L);
        AgentVersionContent onlineContent = contentWithProtocol("a2a");
        AgentVersionCatalogBuilder.Result derived = AgentVersionCatalogBuilder.build(
            Collections.singletonMap(latestVersion, Collections.singletonList("a2a")),
            Collections.singletonMap(AiResourceConstants.LABEL_LATEST, latestVersion));
        ResourceVersionInfo finalVersionInfo =
            AiResourceManager.requireVersionInfo(finalResource);
        finalVersionInfo.setOnlineCnt(1);
        finalVersionInfo.setLabels(
            new LinkedHashMap<String, String>(derived.getLabels()));
        finalResource.setVersionInfo(JacksonUtils.toJson(finalVersionInfo));
        AgentResourceExt finalResourceExt =
            AgentResourceExtSerializer.deserialize(finalResource.getExt());
        finalResourceExt.setVersionCatalog(derived.getVersionCatalog());
        finalResource.setExt(AgentResourceExtSerializer.serialize(finalResourceExt));
        AiResourceVersion online = storedVersion(latestVersion,
            AiConstants.Agent.VERSION_STATUS_ONLINE,
            prepareWrite(latestVersion, onlineContent));
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(first, second, finalResource);
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100))
            .thenReturn(versionPage(Collections.singletonList(online), 1, 1));
        when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(onlineContent);
        when(resourcePersistService.updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), anyLong(), any(AiResource.class)))
            .thenReturn(false, true);
        
        service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, latestVersion, null, null,
            null);
        
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
        verify(resourcePersistService).updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
            eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(4L), any(AiResource.class));
        verify(versionPersistService, times(2)).list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100);
        verify(storageService, times(2)).load(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testSynchronizeDerivedStateRejectsLabelTargetingWorkingVersion()
        throws NacosException {
        AiResource resource = lifecycleResource(null, null,
            Collections.<String, String>emptyMap(), 3L);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(storedVersion());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null,
                Collections.singletonMap("stable", VERSION), null, null));
        
        assertEquals(ErrorCode.ILLEGAL_STATE.getCode(), exception.getDetailErrCode());
        verify(versionPersistService, never()).list(anyString(), anyString(), anyString(),
            any(), anyInt(), anyInt());
        verify(resourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any(AiResource.class));
        verifyNoInteractions(storageService);
    }
    
    @Test
    void testSynchronizeDerivedStateRejectsMissingResourceAndMetaVersion()
        throws NacosException {
        AiResource missingMetaVersion = resourceWithoutWorkingVersions();
        missingMetaVersion.setMetaVersion(null);
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, missingMetaVersion);
        
        NacosApiException notFound = assertThrows(NacosApiException.class,
            () -> service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null, null, null,
                null));
        NacosApiException serverError = assertThrows(NacosApiException.class,
            () -> service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null, null, null,
                null));
        
        assertEquals(NacosException.NOT_FOUND, notFound.getErrCode());
        assertServerError(serverError);
        verify(resourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any(AiResource.class));
    }
    
    @Test
    void testSynchronizeDerivedStateFailsAfterCasRetryExhaustion() throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resourceWithoutWorkingVersions());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null, null, null,
                null));
        
        assertConflict(exception);
        verify(resourcePersistService, times(AiResourceConstants.MAX_WORKING_VERSION_RETRY))
            .updateMetaCas(eq(NAMESPACE_ID), eq(AGENT_NAME),
                eq(Constants.Agent.RESOURCE_TYPE_AGENT), eq(3L), any(AiResource.class));
        verify(versionPersistService, times(AiResourceConstants.MAX_WORKING_VERSION_RETRY))
            .list(NAMESPACE_ID, AGENT_NAME, Constants.Agent.RESOURCE_TYPE_AGENT,
                AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 100);
    }
    
    @Test
    void testSynchronizeDerivedStateRejectsMissingCustomLabelTarget()
        throws NacosException {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(resourceWithoutWorkingVersions());
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.synchronizeDerivedState(NAMESPACE_ID, AGENT_NAME, null,
                Collections.singletonMap("stable", VERSION), null, null));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        verify(resourcePersistService, never()).updateMetaCas(anyString(), anyString(),
            anyString(), anyLong(), any(AiResource.class));
        verifyNoInteractions(storageService);
    }
    
    private AgentVersionDetail newDraft(String version, String protocol) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setVersion(version);
        if (protocol != null) {
            result.setCallInterfaces(contentWithProtocol(protocol).getCallInterfaces());
        }
        result.setAuthor("alice");
        result.setChangeDescription("Create " + version);
        return result;
    }
    
    private void assertInvalidCreateDraft(AgentVersionDetail draft, String basedOnVersion) {
        assertThrows(IllegalArgumentException.class,
            () -> service.createDraft(NAMESPACE_ID, AGENT_NAME, draft, basedOnVersion));
    }
    
    private AgentVersionContent contentWithProtocol(String protocol) {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol(protocol);
        callInterface.setProtocolVersion("1.0");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(
            Collections.<String, Object>singletonMap("protocol", protocol));
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        return new AgentVersionContent(Collections.singletonList(callInterface));
    }
    
    private PreparedAgentVersionWrite prepareWrite(String version,
        AgentVersionContent versionContent) {
        return new AgentVersionStorageService().prepare(NAMESPACE_ID, AGENT_NAME, version,
            versionContent);
    }
    
    private AiResource resourceWithoutWorkingVersions() {
        return lifecycleResource(null, null, Collections.<String, String>emptyMap(), 3L);
    }
    
    private AiResource lifecycleResource(String editingVersion, String reviewingVersion,
        Map<String, String> labels, long metaVersion) {
        AiResource result = storedResource();
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setEditingVersion(editingVersion);
        versionInfo.setReviewingVersion(reviewingVersion);
        versionInfo.setOnlineCnt(0);
        versionInfo.setLabels(new LinkedHashMap<String, String>(labels));
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        result.setMetaVersion(metaVersion);
        return result;
    }
    
    private AiResource applyMetaUpdate(AiResource source, AiResource update, long metaVersion) {
        AiResource result = lifecycleResource(null, null,
            Collections.<String, String>emptyMap(), metaVersion);
        result.setId(source.getId());
        result.setGmtCreate(source.getGmtCreate());
        result.setGmtModified(source.getGmtModified());
        result.setNamespaceId(source.getNamespaceId());
        result.setName(source.getName());
        result.setType(source.getType());
        result.setFrom(source.getFrom());
        result.setOwner(source.getOwner());
        result.setScope(source.getScope());
        result.setStatus(update.getStatus());
        result.setDesc(update.getDesc());
        result.setBizTags(update.getBizTags());
        result.setVersionInfo(update.getVersionInfo());
        result.setExt(update.getExt());
        return result;
    }
    
    private Page<AiResourceVersion> versionPage(List<AiResourceVersion> rows, int totalCount,
        int pagesAvailable) {
        Page<AiResourceVersion> result = new Page<AiResourceVersion>();
        result.setPageNumber(1);
        result.setTotalCount(totalCount);
        result.setPagesAvailable(pagesAvailable);
        result.setPageItems(rows);
        return result;
    }
    
    private void stubDeleteAgentRows(List<AiResourceVersion> rows) {
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(storedResource());
        when(versionPersistService.list(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, null, 1, 100))
            .thenReturn(versionPage(rows, rows.size(), 1));
        when(resourcePersistService.delete(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(1);
        when(versionPersistService.deleteByNameAndType(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(rows.size());
    }
    
    private void stubPrepare() throws NacosException {
        when(storageService.prepare(anyString(), anyString(), anyString(),
            any(AgentVersionContent.class))).thenReturn(prepared);
        org.mockito.Mockito.lenient()
            .when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenReturn(content);
    }
    
    private void stubDraftUpdatePreparation(PreparedAgentVersionWrite replacementWrite) {
        when(storageService.prepare(any(AgentVersionStorageDescriptor.class),
            any(AgentVersionContent.class))).thenReturn(replacementWrite);
    }
    
    private AgentVersionContent replacementContent() {
        AgentVersionDetail detail = newInitialDraft();
        detail.getCallInterfaces().get(0).setProtocolVersion("0.4");
        return new AgentVersionContent(detail.getCallInterfaces());
    }
    
    private PreparedAgentVersionWrite replacementWrite(AgentVersionContent replacement) {
        return new AgentVersionStorageService().prepare(prepared.getDescriptor(), replacement);
    }
    
    private AiResourceVersion updatedVersion(PreparedAgentVersionWrite replacementWrite,
        String description) {
        AiResourceVersion result = storedVersion();
        AgentVersionStorageDescriptor descriptor = replacementWrite.getDescriptor();
        result.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        result.setDesc(description);
        result.setGmtModified(new Timestamp(5000L));
        return result;
    }
    
    private void stubUntilVersionInsert() throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
    }
    
    private void stubSuccessfulWritesForPostCommit(AiResource resource,
        AiResourceVersion version) throws NacosException {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null, resource);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null, version);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(RESOURCE_ID);
    }
    
    private List<AiResource> createConflictingResources() {
        List<AiResource> result = new ArrayList<AiResource>();
        result.add(mutatedResource(resource -> resource.setNamespaceId("other")));
        result.add(mutatedResource(resource -> resource.setName("Other Agent")));
        result.add(mutatedResource(resource -> resource.setType("prompt")));
        result.add(mutatedResource(
            resource -> resource.setStatus(AiConstants.Agent.RESOURCE_STATUS_DISABLE)));
        result.add(mutatedResource(resource -> resource.setFrom("remote")));
        result.add(mutatedResource(resource -> resource.setScope("PUBLIC")));
        result.add(mutatedResource(resource -> resource.setOwner("bob")));
        result.add(mutatedResource(resource -> resource.setBizTags("[\"other\"]")));
        result.add(mutatedResourceExt(ext -> ext.setDisplayName("Other Display")));
        result.add(mutatedResourceExt(ext -> ext.setIconUrl("https://example.com/other.png")));
        result.add(mutatedResourceExt(ext -> ext.setProvider(null)));
        result.add(mutatedResourceExt(ext -> ext.getProvider().setName("Other Provider")));
        result.add(mutatedResourceExt(ext -> ext.getProvider().setUrl("https://example.com")));
        result.add(mutatedResourceExt(
            ext -> ext.setExtensions(Collections.<String, Object>singletonMap("x-team", "other"))));
        
        ResourceVersionInfo onlineCount = initialVersionInfo(VERSION);
        onlineCount.setOnlineCnt(1);
        result.add(mutatedResource(
            resource -> resource.setVersionInfo(JacksonUtils.toJson(onlineCount))));
        ResourceVersionInfo labels = initialVersionInfo(VERSION);
        labels.setLabels(Collections.singletonMap("stable", VERSION));
        result.add(mutatedResource(
            resource -> resource.setVersionInfo(JacksonUtils.toJson(labels))));
        
        AgentVersionCatalog onlineCatalog = AgentVersionCatalogBuilder.build(
            Collections.singletonMap("1.0.0", Collections.singletonList("a2a")),
            Collections.<String, String>emptyMap()).getVersionCatalog();
        result.add(mutatedResourceExt(ext -> ext.setVersionCatalog(onlineCatalog)));
        return result;
    }
    
    private List<AiResourceVersion> createConflictingVersions() {
        List<AiResourceVersion> result = new ArrayList<AiResourceVersion>();
        result.add(mutatedVersion(version -> version.setNamespaceId("other")));
        result.add(mutatedVersion(version -> version.setName("Other Agent")));
        result.add(mutatedVersion(version -> version.setType("prompt")));
        result.add(mutatedVersion(version -> version.setVersion("1.0.0")));
        result.add(mutatedVersion(
            version -> version.setStatus(AiConstants.Agent.VERSION_STATUS_REVIEWING)));
        result.add(mutatedVersion(version -> version.setAuthor("bob")));
        result.add(mutatedVersion(version -> version.setDesc("Other change")));
        result.add(mutatedVersionDescriptor(descriptor -> descriptor.setProvider("custom")));
        result.add(mutatedVersionDescriptor(descriptor -> descriptor.setKey("other-key")));
        result.add(mutatedVersionDescriptor(descriptor -> descriptor.setContentDigest(
            "sha256:" + repeat('b', 64))));
        result.add(mutatedVersionDescriptor(
            descriptor -> descriptor.setSize(descriptor.getSize() + 1)));
        result.add(mutatedVersion(
            version -> version.setPublishPipelineInfo("{\"executionId\":\"other\"}")));
        return result;
    }
    
    private AiResource mutatedResource(Consumer<AiResource> mutation) {
        AiResource result = equivalentStoredResource();
        mutation.accept(result);
        return result;
    }
    
    private AiResource mutatedResourceExt(Consumer<AgentResourceExt> mutation) {
        AiResource result = equivalentStoredResource();
        AgentResourceExt ext = AgentResourceExtSerializer.deserialize(result.getExt());
        mutation.accept(ext);
        result.setExt(AgentResourceExtSerializer.serialize(ext));
        return result;
    }
    
    private AiResourceVersion mutatedVersion(Consumer<AiResourceVersion> mutation) {
        AiResourceVersion result = storedVersion();
        mutation.accept(result);
        return result;
    }
    
    private AiResourceVersion mutatedVersionDescriptor(
        Consumer<AgentVersionStorageDescriptor> mutation) {
        AiResourceVersion result = storedVersion();
        AgentVersionStorageDescriptor descriptor = prepared.getDescriptor();
        mutation.accept(descriptor);
        result.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        return result;
    }
    
    private Agent newAgent() {
        Agent result = new Agent();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setDisplayName("Nacos Agent Display");
        result.setDescription("Agent description");
        result.setIconUrl("https://example.com/agent.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        result.setProvider(provider);
        result.setTags(Arrays.asList("assistant", "demo"));
        result.setExtensions(Collections.<String, Object>singletonMap("x-team", "ai"));
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        result.setOwner("alice");
        result.setScope("PRIVATE");
        return result;
    }
    
    private String newTag(int index, int length) {
        StringBuilder result = new StringBuilder("tag-").append(index).append('-');
        while (result.length() < length) {
            result.append('x');
        }
        return result.toString();
    }
    
    private AgentVersionDetail newInitialDraft() {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("0.3");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(
            Collections.<String, Object>singletonMap("name", "native"));
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        AgentVersionDetail result = new AgentVersionDetail();
        result.setVersion(VERSION);
        result.setCallInterfaces(Collections.singletonList(callInterface));
        result.setAuthor("alice");
        result.setChangeDescription("Initial draft");
        return result;
    }
    
    private ResourceVersionInfo initialVersionInfo(String version) {
        ResourceVersionInfo result = new ResourceVersionInfo();
        result.setEditingVersion(version);
        result.setOnlineCnt(0);
        result.setLabels(new LinkedHashMap<String, String>());
        return result;
    }
    
    private String serializeVersionInfo(String version) {
        return JacksonUtils.toJson(initialVersionInfo(version));
    }
    
    private AiResource storedResource() {
        AgentVersionCatalog catalog = emptyCatalog();
        AgentResourceExt ext = new AgentResourceExt();
        ext.setSchemaVersion(AgentResourceExt.SCHEMA_VERSION);
        ext.setDisplayName(agent.getDisplayName());
        ext.setIconUrl(agent.getIconUrl());
        ext.setProvider(agent.getProvider());
        ext.setExtensions(agent.getExtensions());
        ext.setVersionCatalog(catalog);
        
        AiResource result = new AiResource();
        result.setId(RESOURCE_ID);
        result.setGmtCreate(new Timestamp(1000L));
        result.setGmtModified(new Timestamp(2000L));
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(AGENT_NAME);
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setDesc(agent.getDescription());
        result.setStatus(agent.getStatus());
        result.setOwner(agent.getOwner());
        result.setScope(agent.getScope());
        result.setBizTags(JacksonUtils.toJson(agent.getTags()));
        result.setExt(AgentResourceExtSerializer.serialize(ext));
        result.setFrom("local");
        result.setVersionInfo(serializeVersionInfo(VERSION));
        result.setMetaVersion(3L);
        return result;
    }
    
    private AiResource equivalentStoredResource() {
        AiResource result = storedResource();
        result.setMetaVersion(1L);
        return result;
    }
    
    private AiResourceVersion storedVersion() {
        AiResourceVersion result = new AiResourceVersion();
        result.setId(VERSION_ID);
        result.setGmtCreate(new Timestamp(3000L));
        result.setGmtModified(new Timestamp(4000L));
        result.setNamespaceId(NAMESPACE_ID);
        result.setName(AGENT_NAME);
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setVersion(VERSION);
        result.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        result.setAuthor("alice");
        result.setDesc("Initial draft");
        result.setStorage(
            AgentVersionStorageDescriptorSerializer.serialize(prepared.getDescriptor()));
        return result;
    }
    
    private AiResourceVersion storedVersion(String version, String status,
        PreparedAgentVersionWrite versionWrite) {
        AiResourceVersion result = storedVersion();
        result.setId(VERSION_ID + Math.abs(version.hashCode()));
        result.setVersion(version);
        result.setStatus(status);
        result.setStorage(AgentVersionStorageDescriptorSerializer.serialize(
            versionWrite.getDescriptor()));
        return result;
    }
    
    private AiResourceVersion reorderedStoredVersion() {
        AgentVersionStorageDescriptor descriptor = prepared.getDescriptor();
        Map<String, Object> projection = new LinkedHashMap<String, Object>();
        projection.put("size", descriptor.getSize());
        projection.put("schemaVersion", descriptor.getSchemaVersion());
        projection.put("mediaType", descriptor.getMediaType());
        projection.put("contentDigest", descriptor.getContentDigest());
        projection.put("agentNameCodec", descriptor.getAgentNameCodec());
        projection.put("keyFormat", descriptor.getKeyFormat());
        projection.put("key", descriptor.getKey());
        projection.put("provider", descriptor.getProvider());
        AiResourceVersion result = storedVersion();
        result.setStorage(JacksonUtils.toJson(projection));
        return result;
    }
    
    private AiResource asPersisted(AiResource source, long id) {
        source.setId(id);
        source.setGmtCreate(new Timestamp(1000L));
        source.setGmtModified(new Timestamp(2000L));
        return source;
    }
    
    private AiResourceVersion asPersisted(AiResourceVersion source, long id) {
        source.setId(id);
        source.setGmtCreate(new Timestamp(3000L));
        source.setGmtModified(new Timestamp(4000L));
        return source;
    }
    
    private AgentVersionCatalog emptyCatalog() {
        return AgentVersionCatalogBuilder.build(
            Collections.<String, java.util.List<String>>emptyMap(),
            Collections.<String, String>emptyMap()).getVersionCatalog();
    }
    
    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
    
    private void assertCreatedDetail(AgentVersionDetail result) {
        assertNotNull(result);
        assertEquals(NAMESPACE_ID, result.getNamespaceId());
        assertEquals(AGENT_NAME, result.getAgentName());
        assertEquals(VERSION, result.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_DRAFT, result.getStatus());
        assertEquals(initialDraft.getCallInterfaces(), result.getCallInterfaces());
        assertEquals(initialDraft.getAuthor(), result.getAuthor());
        assertEquals(initialDraft.getChangeDescription(), result.getChangeDescription());
        assertEquals(prepared.getDescriptor().getContentDigest(),
            result.getContentDigest());
    }
    
    private void assertPersistedResource(AiResource resource) {
        assertEquals(Constants.Agent.RESOURCE_TYPE_AGENT, resource.getType());
        assertEquals("local", resource.getFrom());
        assertEquals(1L, resource.getMetaVersion());
        ResourceVersionInfo versionInfo =
            JacksonUtils.toObj(resource.getVersionInfo(), ResourceVersionInfo.class);
        assertEquals(VERSION, versionInfo.getEditingVersion());
        assertEquals(0, versionInfo.getOnlineCnt());
        assertTrue(versionInfo.getLabels().isEmpty());
        AgentResourceExt ext = AgentResourceExtSerializer.deserialize(resource.getExt());
        assertTrue(ext.getVersionCatalog().getOnlineVersions().isEmpty());
        List<String> bizTags = JacksonUtils.toObj(resource.getBizTags(),
            new TypeReference<List<String>>() {
            });
        assertEquals(agent.getTags(), bizTags);
    }
    
    private void assertPersistedVersion(AiResourceVersion version) {
        assertEquals(Constants.Agent.RESOURCE_TYPE_AGENT, version.getType());
        assertEquals(AiConstants.Agent.VERSION_STATUS_DRAFT, version.getStatus());
        assertEquals(VERSION, version.getVersion());
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(version.getStorage());
        assertEquals(prepared.getDescriptor().getContentDigest(), descriptor.getContentDigest());
    }
    
    private void assertConflict(NacosApiException exception) {
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), exception.getDetailErrCode());
    }
    
    private void assertServerError(NacosApiException exception) {
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), exception.getDetailErrCode());
    }
}
