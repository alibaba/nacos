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
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
        assertPersistedResource(persistedResource.get());
        assertPersistedVersion(persistedVersion.get());
        verify(storageService, never()).load(any(AgentVersionStorageDescriptor.class));
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
    }
    
    @Test
    void testCreateRejectsReadOnlyAndMismatchedInputsBeforePersistence() {
        agent.setMetaVersion(1L);
        assertThrows(IllegalArgumentException.class, () -> service.create(agent, initialDraft));
        
        agent.setMetaVersion(null);
        initialDraft.setNamespaceId("other");
        assertThrows(IllegalArgumentException.class, () -> service.create(agent, initialDraft));
        
        initialDraft.setNamespaceId(null);
        initialDraft.setAgentName("other");
        assertThrows(IllegalArgumentException.class, () -> service.create(agent, initialDraft));
        
        initialDraft.setAgentName(null);
        initialDraft.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        assertThrows(IllegalArgumentException.class, () -> service.create(agent, initialDraft));
        
        initialDraft.setStatus(null);
        initialDraft.setContentDigest(prepared.getDescriptor().getContentDigest());
        assertThrows(IllegalArgumentException.class, () -> service.create(agent, initialDraft));
        
        verifyNoInteractions(resourcePersistService, versionPersistService, storageService);
    }
    
    @Test
    void testCreateRejectsNullInputsBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.create(null, initialDraft));
        assertThrows(IllegalArgumentException.class, () -> service.create(agent, null));
        
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
            () -> service.create(agent, initialDraft));
        
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertEquals(tags, result.getAgent().getTags());
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertTrue(result.getAgent().getTags().isEmpty());
    }
    
    @Test
    void testCreateRejectsExistingResourceBeforeAnyWrite() throws NacosException {
        stubPrepare();
        AiResource conflictingResource = storedResource();
        conflictingResource.setDesc("different metadata");
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(conflictingResource);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
                () -> service.create(agent, initialDraft)));
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
                () -> service.create(agent, initialDraft)));
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
            () -> service.create(agent, initialDraft));
        
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
        
        AgentOverview result = service.create(agent, initialDraft);
        
        assertCreatedOverview(result);
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
        assertConflict(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testResourceInsertInvalidIdMapsToServerError() throws NacosException {
        stubUntilVersionInsert();
        when(resourcePersistService.insert(any(AiResource.class))).thenReturn(0L);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testPostCommitMissingVersionMapsToServerError() throws NacosException {
        stubSuccessfulWritesForPostCommit(storedResource(), null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.create(agent, initialDraft));
        
        assertServerError(exception);
        verify(storageService, never()).delete(any(AgentVersionStorageDescriptor.class));
    }
    
    @Test
    void testPostCommitInvalidVersionSummaryMapsToServerError() throws NacosException {
        AiResourceVersion invalidVersion = storedVersion();
        invalidVersion.setStorage("{}");
        stubSuccessfulWritesForPostCommit(storedResource(), invalidVersion);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.create(agent, initialDraft));
        
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
            () -> service.create(agent, initialDraft));
        
        assertServerError(exception);
        assertSame(persistenceFailure, exception.getCause());
        verifyNoInteractions(versionPersistService);
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
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
    
    private void stubPrepare() {
        when(storageService.prepare(anyString(), anyString(), anyString(),
            any(AgentVersionContent.class))).thenReturn(prepared);
    }
    
    private void stubUntilVersionInsert() {
        stubPrepare();
        when(resourcePersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT)).thenReturn(null);
        when(versionPersistService.find(NAMESPACE_ID, AGENT_NAME,
            Constants.Agent.RESOURCE_TYPE_AGENT, VERSION)).thenReturn(null);
        when(versionPersistService.insert(any(AiResourceVersion.class))).thenReturn(VERSION_ID);
    }
    
    private void stubSuccessfulWritesForPostCommit(AiResource resource,
        AiResourceVersion version) {
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
    
    private void assertCreatedOverview(AgentOverview result) {
        assertNotNull(result);
        assertEquals(AGENT_NAME, result.getAgent().getAgentName());
        assertEquals(VERSION, result.getAgent().getVersionInfo().getEditingVersion());
        assertEquals(0, result.getAgent().getVersionCatalog().getOnlineVersions().size());
        assertNull(result.getAgent().getVersionCatalog().getLatestVersion());
        assertEquals(1, result.getVersionPage().getTotalCount());
        assertEquals(1, result.getVersionPage().getPageItems().size());
        assertEquals(VERSION, result.getVersionPage().getPageItems().get(0).getVersion());
        assertEquals(prepared.getDescriptor().getContentDigest(),
            result.getVersionPage().getPageItems().get(0).getContentDigest());
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
