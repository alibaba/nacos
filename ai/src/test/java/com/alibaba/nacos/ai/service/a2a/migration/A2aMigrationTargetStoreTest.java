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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageTestUtils;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceChangeNotifier;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationTargetStoreTest {
    
    private static final String NAMESPACE_ID = "tenant-a";
    
    private static final String AGENT_NAME = "research-agent";
    
    private final List<String> events = new ArrayList<String>();
    
    private final Map<String, AgentVersionContent> storage =
        new LinkedHashMap<String, AgentVersionContent>();
    
    private final InMemoryResourcePersistService resourcePersistService =
        new InMemoryResourcePersistService();
    
    private final InMemoryVersionPersistService versionPersistService =
        new InMemoryVersionPersistService();
    
    @Mock
    private AgentVersionStorageService storageService;
    
    @Mock
    private A2aMigrationStorageVerifier storageVerifier;
    
    @Mock
    private AiResourceIndexMaintenanceService indexMaintenanceService;
    
    @Mock
    private AiResourceChangeNotifier resourceChangeNotifier;
    
    private A2aMigrationTargetStore targetStore;
    
    @BeforeEach
    void setUp() throws NacosException {
        when(storageService.prepare(anyString(), anyString(), anyString(),
            any(AgentVersionContent.class))).thenAnswer(
                invocation -> AgentVersionStorageTestUtils.prepare(invocation.getArgument(0),
                    invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3)));
        lenient().when(storageService.load(any(AgentVersionStorageDescriptor.class)))
            .thenAnswer(invocation -> {
                AgentVersionStorageDescriptor descriptor = invocation.getArgument(0);
                AgentVersionContent result = storage.get(descriptor.getKey());
                if (result == null) {
                    throw new NacosException(NacosException.SERVER_ERROR, "storage missing");
                }
                return result;
            });
        lenient().doAnswer(invocation -> {
            PreparedAgentVersionWrite prepared = invocation.getArgument(0);
            storage.put(prepared.getDescriptor().getKey(),
                AgentVersionContentSerializer.deserialize(prepared.getBytes()));
            events.add("storage-save:" + versionFromKey(prepared.getDescriptor().getKey()));
            return null;
        }).when(storageVerifier).saveAndVerify(any());
        lenient().doAnswer(invocation -> {
            AgentVersionStorageDescriptor descriptor = invocation.getArgument(0);
            storage.remove(descriptor.getKey());
            events.add("storage-delete:" + versionFromKey(descriptor.getKey()));
            return null;
        }).when(storageService).delete(any());
        targetStore = new A2aMigrationTargetStore(resourcePersistService, versionPersistService,
            storageService, storageVerifier);
        targetStore.setAiResourceIndexMaintenanceService(indexMaintenanceService);
        targetStore.setAiResourceChangeNotifier(resourceChangeNotifier);
    }
    
    @Test
    void shouldWriteAllVersionsBeforePublishingResourceAndRemainIdempotent()
        throws NacosException {
        AtomicInteger fenceChecks = new AtomicInteger();
        A2aMigrationDefinition definition = definition("2.0.0", "1.0.0", "2.0.0");
        
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition, () -> {
                fenceChecks.incrementAndGet();
                return true;
            }));
        
        assertEquals(Arrays.asList("storage-save:1.0.0", "version-insert:1.0.0",
            "storage-save:2.0.0", "version-insert:2.0.0", "resource-insert"), events);
        assertEquals(3, fenceChecks.get());
        AiResource resource = resourcePersistService.current();
        assertEquals(A2aMigrationTargetStore.MIGRATION_RESOURCE_SOURCE, resource.getFrom());
        assertEquals("nacos", resource.getOwner());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, resource.getScope());
        Map<?, ?> versionInfo = JacksonUtils.toObj(resource.getVersionInfo(), Map.class);
        assertEquals("2.0.0", ((Map<?, ?>) versionInfo.get("labels"))
            .get(AiResourceConstants.LABEL_LATEST));
        assertEquals(2, versionPersistService.rows.size());
        verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME);
        verify(resourceChangeNotifier).notifyChanged(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME, AiResourceChangeOperation.CREATE,
            true);
        
        events.clear();
        assertEquals(A2aMigrationTargetStore.Result.EQUIVALENT,
            targetStore.reconcile(definition, () -> true));
        assertTrue(events.isEmpty());
        verify(indexMaintenanceService, times(1)).schedule(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME);
        verify(resourceChangeNotifier, times(1)).notifyChanged(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME, AiResourceChangeOperation.CREATE,
            true);
    }
    
    @Test
    void sourceFenceShouldPreventInitialWriteAndRecoverVersionOnlyPartialWrite()
        throws NacosException {
        A2aMigrationDefinition definition = definition("2.0.0", "1.0.0", "2.0.0");
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition, () -> false));
        assertTrue(events.isEmpty());
        assertTrue(versionPersistService.rows.isEmpty());
        
        AtomicInteger checks = new AtomicInteger();
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition,
            () -> checks.incrementAndGet() == 1));
        assertNull(resourcePersistService.current());
        assertEquals(2, versionPersistService.rows.size());
        assertEquals(Arrays.asList("storage-save:1.0.0", "version-insert:1.0.0",
            "storage-save:2.0.0", "version-insert:2.0.0"), events);
        
        events.clear();
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition, () -> true));
        assertEquals(Collections.singletonList("resource-insert"), events);
    }
    
    @Test
    void shouldRepairOwnedRowsThenDeleteExtraVersionAfterCatalogUpdate()
        throws NacosException {
        targetStore.reconcile(definition("2.0.0", "1.0.0", "2.0.0"), () -> true);
        AiResource resource = resourcePersistService.current();
        resource.setDesc("corrupted");
        AiResourceVersion first = versionPersistService.rows.get("1.0.0");
        first.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(first.getStorage());
        storage.put(descriptor.getKey(), content("1.0.0", "0.9"));
        events.clear();
        
        assertEquals(A2aMigrationTargetStore.Result.REPAIRED,
            targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true));
        
        assertEquals(Arrays.asList("storage-save:1.0.0", "version-update:1.0.0",
            "version-status:1.0.0", "resource-update", "version-delete:2.0.0",
            "storage-delete:2.0.0"), events);
        assertEquals("Research", resourcePersistService.current().getDesc());
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE,
            versionPersistService.rows.get("1.0.0").getStatus());
        assertFalse(versionPersistService.rows.containsKey("2.0.0"));
        verify(indexMaintenanceService, times(2)).schedule(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME);
        verify(resourceChangeNotifier).notifyChanged(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME, AiResourceChangeOperation.UPDATE,
            true);
    }
    
    @Test
    void shouldAcceptStrictExternalEquivalenceWithoutChangingOwnership()
        throws NacosException {
        A2aMigrationDefinition definition = definition("1.0.0", "1.0.0");
        targetStore.reconcile(definition, () -> true);
        AiResource external = resourcePersistService.current();
        external.setFrom("external-import");
        external.setOwner("alice");
        external.setScope(VisibilityConstants.SCOPE_PRIVATE);
        versionPersistService.rows.get("1.0.0").setAuthor("alice");
        events.clear();
        
        assertEquals(A2aMigrationTargetStore.Result.EXTERNAL_EQUIVALENT,
            targetStore.reconcile(definition, () -> true));
        assertTrue(events.isEmpty());
        assertEquals("alice", external.getOwner());
        
        external.setDesc("different");
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition, () -> true));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
        assertEquals("different", external.getDesc());
    }
    
    @Test
    void shouldRejectUnownedOrCorruptVersionRowsAndOwnedAuthorConflict()
        throws NacosException {
        A2aMigrationDefinition definition = definition("1.0.0", "1.0.0");
        AtomicInteger checks = new AtomicInteger();
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition,
            () -> checks.incrementAndGet() == 1));
        AiResourceVersion orphan = versionPersistService.rows.get("1.0.0");
        orphan.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition, () -> true));
        
        orphan.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        targetStore.reconcile(definition, () -> true);
        orphan.setAuthor("other");
        storage.clear();
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition, () -> true));
    }
    
    @Test
    void insertRacesShouldRecoverOnlyExactRows() throws NacosException {
        versionPersistService.throwAfterInsert = true;
        resourcePersistService.throwAfterInsert = true;
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true));
        assertTrue(resourcePersistService.current() != null);
        assertEquals(1, versionPersistService.rows.size());
        
        resourcePersistService.clear();
        versionPersistService.rows.clear();
        storage.clear();
        events.clear();
        resourcePersistService.throwBeforeInsert = true;
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true));
    }
    
    @Test
    void shouldDeleteOnlyConfirmedMigrationOwnedOrphans() throws NacosException {
        assertFalse(targetStore.deleteConfirmedOrphan(NAMESPACE_ID, AGENT_NAME));
        targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true);
        resourcePersistService.current().setFrom("external");
        assertFalse(targetStore.deleteConfirmedOrphan(NAMESPACE_ID, AGENT_NAME));
        resourcePersistService.current().setFrom(
            A2aMigrationTargetStore.MIGRATION_RESOURCE_SOURCE);
        events.clear();
        
        assertTrue(targetStore.deleteConfirmedOrphan(NAMESPACE_ID, AGENT_NAME));
        assertEquals(Arrays.asList("resource-delete", "version-delete:1.0.0",
            "storage-delete:1.0.0"), events);
        assertTrue(storage.isEmpty());
        verify(indexMaintenanceService, times(2)).schedule(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME);
        verify(resourceChangeNotifier).notifyChanged(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME, AiResourceChangeOperation.DELETE,
            true);
        
        targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true);
        resourcePersistService.deleteResult = 0;
        assertThrows(NacosException.class,
            () -> targetStore.deleteConfirmedOrphan(NAMESPACE_ID, AGENT_NAME));
        assertEquals(1, versionPersistService.rows.size());
    }
    
    @Test
    void projectionMaintenanceFailureShouldNotChangeCommittedMigrationResult()
        throws NacosException {
        doThrow(new IllegalStateException("index unavailable")).when(indexMaintenanceService)
            .schedule(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME);
        doThrow(new IllegalStateException("notifier unavailable")).when(resourceChangeNotifier)
            .notifyChanged(NAMESPACE_ID, Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME,
                AiResourceChangeOperation.CREATE, true);
        
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true));
        assertTrue(resourcePersistService.current() != null);
        assertEquals(1, versionPersistService.rows.size());
        verify(indexMaintenanceService).schedule(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME);
        verify(resourceChangeNotifier).notifyChanged(NAMESPACE_ID,
            Constants.Agent.RESOURCE_TYPE_AGENT, AGENT_NAME,
            AiResourceChangeOperation.CREATE, true);
        verifyNoMoreInteractions(indexMaintenanceService, resourceChangeNotifier);
    }
    
    @Test
    void shouldListOnlyMigrationOwnedAgentsAcrossSafePageContract() throws NacosException {
        targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true);
        AiResource external = copy(resourcePersistService.current());
        external.setName("external-agent");
        external.setFrom("external");
        resourcePersistService.rows.put(external.getName(), external);
        
        assertEquals(Collections.singleton(AGENT_NAME),
            targetStore.listMigratedAgentNames(NAMESPACE_ID));
        
        resourcePersistService.listUnavailable = true;
        assertThrows(IllegalStateException.class,
            () -> targetStore.listMigratedAgentNames(NAMESPACE_ID));
    }
    
    @Test
    void shouldRejectIncompleteDefinitionsAndPersistenceFailures() throws NacosException {
        assertThrows(IllegalArgumentException.class,
            () -> targetStore.reconcile(null, () -> true));
        Agent incomplete = agent();
        assertThrows(IllegalArgumentException.class, () -> targetStore.reconcile(
            new A2aMigrationDefinition(incomplete, Collections.emptyList(), "1.0.0"),
            () -> true));
        A2aMigrationDefinition missingLatest = definition("2.0.0", "1.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> targetStore.reconcile(missingLatest, () -> true));
        
        targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true);
        resourcePersistService.current().setDesc("broken");
        resourcePersistService.updateResult = false;
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true));
    }
    
    @Test
    void shouldRejectDuplicateVersionsAndMissingVersionContent() {
        assertThrows(IllegalArgumentException.class, () -> targetStore.reconcile(
            definition("1.0.0", "1.0.0", "1.0.0"), () -> true));
        A2aMigrationDefinition missingContent = definition("1.0.0", "1.0.0");
        missingContent.getVersions().get(0).setCallInterfaces(null);
        assertThrows(IllegalArgumentException.class,
            () -> targetStore.reconcile(missingContent, () -> true));
    }
    
    @Test
    void shouldRejectUnownedVersionSetAndPointerRepair() throws NacosException {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(NacosException.class, () -> targetStore.reconcile(
            definition("1.0.0", "1.0.0"), () -> checks.incrementAndGet() == 1));
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition("2.0.0", "2.0.0"), () -> true));
        
        resetState();
        checks.set(0);
        assertThrows(NacosException.class, () -> targetStore.reconcile(
            definition("1.0.0", "1.0.0"), () -> checks.incrementAndGet() == 1));
        AiResourceVersion unowned = versionPersistService.rows.get("1.0.0");
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(unowned.getStorage());
        AgentVersionContent stored = storage.remove(descriptor.getKey());
        descriptor.setProvider("object-store");
        descriptor.setKey("alternative-key");
        descriptor.setKeyFormat(null);
        descriptor.setAgentNameCodec(null);
        unowned.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        storage.put(descriptor.getKey(), stored);
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition("1.0.0", "1.0.0"), () -> true));
    }
    
    @Test
    void shouldResumeExactUnownedVersionSubsetAfterSourceFenceChange()
        throws NacosException {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(NacosException.class, () -> targetStore.reconcile(
            definition("1.0.0", "1.0.0"), () -> checks.incrementAndGet() == 1));
        assertNull(resourcePersistService.current());
        assertEquals(Collections.singleton("1.0.0"), versionPersistService.rows.keySet());
        
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition("2.0.0", "1.0.0", "2.0.0"), () -> true));
        Map<?, ?> versionInfo = JacksonUtils.toObj(
            resourcePersistService.current().getVersionInfo(), Map.class);
        assertEquals("2.0.0", ((Map<?, ?>) versionInfo.get("labels"))
            .get(AiResourceConstants.LABEL_LATEST));
        assertEquals(new LinkedHashSet<String>(Arrays.asList("1.0.0", "2.0.0")),
            versionPersistService.rows.keySet());
    }
    
    @Test
    void resourceInsertAndUpdateUncertaintyShouldRecoverOnlyEquivalentState()
        throws NacosException {
        A2aMigrationDefinition definition = definition("1.0.0", "1.0.0");
        targetStore.reconcile(definition, () -> true);
        AiResource equivalentExternal = copy(resourcePersistService.current());
        equivalentExternal.setFrom("external");
        equivalentExternal.setOwner("alice");
        equivalentExternal.setScope(VisibilityConstants.SCOPE_PRIVATE);
        resetState();
        resourcePersistService.insertRaceResource = equivalentExternal;
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition, () -> true));
        
        resetState();
        resourcePersistService.insertResult = 0L;
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition, () -> true));
        
        resetState();
        resourcePersistService.insertFailure = new IllegalStateException("database unavailable");
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition, () -> true));
        
        resetState();
        targetStore.reconcile(definition, () -> true);
        resourcePersistService.current().setDesc("stale");
        resourcePersistService.updateRaceToExpected = true;
        assertEquals(A2aMigrationTargetStore.Result.EQUIVALENT,
            targetStore.reconcile(definition, () -> true));
        resourcePersistService.current().setExt("{");
        resourcePersistService.updateRaceToExpected = false;
        assertEquals(A2aMigrationTargetStore.Result.REPAIRED,
            targetStore.reconcile(definition, () -> true));
        resourcePersistService.current().setBizTags(null);
        assertEquals(A2aMigrationTargetStore.Result.EQUIVALENT,
            targetStore.reconcile(definition, () -> true));
    }
    
    @Test
    void versionInsertUncertaintyShouldRecoverOnlyEquivalentState() throws NacosException {
        A2aMigrationDefinition definition = definition("1.0.0", "1.0.0");
        versionPersistService.insertResult = 0L;
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition, () -> true));
        
        resetState();
        versionPersistService.insertRaceAuthor = "other";
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition, () -> true));
        
        resetState();
        versionPersistService.insertFailure = new IllegalStateException("database unavailable");
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition, () -> true));
    }
    
    @Test
    void ownedVersionRepairFailuresShouldRemainBlocking() throws NacosException {
        A2aMigrationDefinition definition = definition("1.0.0", "1.0.0");
        targetStore.reconcile(definition, () -> true);
        AiResourceVersion version = versionPersistService.rows.get("1.0.0");
        version.setDesc("stale");
        assertEquals(A2aMigrationTargetStore.Result.REPAIRED,
            targetStore.reconcile(definition, () -> true));
        
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(version.getStorage());
        storage.put(descriptor.getKey(), content("1.0.0", "0.4"));
        versionPersistService.updateStorageResult = 0;
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition, () -> true));
        
        versionPersistService.updateStorageResult = 1;
        versionPersistService.updateStatusResult = 0;
        version.setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        assertThrows(NacosException.class, () -> targetStore.reconcile(definition, () -> true));
    }
    
    @Test
    void externalVersionDifferenceAndUnsafeVersionScansShouldFailClosed()
        throws NacosException {
        A2aMigrationDefinition definition = definition("1.0.0", "1.0.0");
        targetStore.reconcile(definition, () -> true);
        resourcePersistService.current().setFrom("external");
        versionPersistService.rows.get("1.0.0")
            .setStatus(AiConstants.Agent.VERSION_STATUS_DRAFT);
        assertThrows(NacosApiException.class,
            () -> targetStore.reconcile(definition, () -> true));
        
        resetState();
        versionPersistService.listUnavailable = true;
        assertThrows(IllegalStateException.class,
            () -> targetStore.reconcile(definition, () -> true));
        versionPersistService.listUnavailable = false;
        versionPersistService.listWithNull = true;
        assertThrows(IllegalStateException.class,
            () -> targetStore.reconcile(definition, () -> true));
        versionPersistService.listWithNull = false;
        versionPersistService.pagesUnavailable = true;
        assertEquals(A2aMigrationTargetStore.Result.CREATED,
            targetStore.reconcile(definition, () -> true));
    }
    
    private A2aMigrationDefinition definition(String latest, String... versions) {
        List<AgentVersionDetail> details = new ArrayList<AgentVersionDetail>();
        for (String version : versions) {
            AgentVersionDetail detail = new AgentVersionDetail();
            detail.setNamespaceId(NAMESPACE_ID);
            detail.setAgentName(AGENT_NAME);
            detail.setVersion(version);
            detail.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
            detail.setAuthor("nacos");
            detail.setChangeDescription("");
            detail.setCallInterfaces(content(version, "0.3").getCallInterfaces());
            details.add(detail);
        }
        return new A2aMigrationDefinition(agent(), details, latest);
    }
    
    private Agent agent() {
        Agent result = new Agent();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setDescription("Research");
        result.setIconUrl("https://example.com/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Example");
        provider.setUrl("https://example.com");
        result.setProvider(provider);
        result.setTags(Collections.singletonList("research"));
        result.setExtensions(Collections.<String, Object>singletonMap("tier", "stable"));
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        result.setOwner("nacos");
        result.setScope(VisibilityConstants.SCOPE_PUBLIC);
        return result;
    }
    
    private AgentVersionContent content(String version, String protocolVersion) {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion(protocolVersion);
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("version", version));
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://example.com/" + version);
        endpoint.setTransport("HTTP+JSON");
        callInterface.setDeclaredEndpoints(Collections.singletonList(endpoint));
        return new AgentVersionContent(Collections.singletonList(callInterface));
    }
    
    private String versionFromKey(String key) {
        int slash = key.lastIndexOf('_');
        return slash < 0 ? key : key.substring(slash + 1, key.length() - ".json".length());
    }
    
    private AiResource copy(AiResource source) {
        return JacksonUtils.toObj(JacksonUtils.toJson(source), AiResource.class);
    }
    
    private void resetState() {
        resourcePersistService.clear();
        versionPersistService.clear();
        storage.clear();
        events.clear();
    }
    
    private final class InMemoryResourcePersistService implements AiResourcePersistService {
        
        private final Map<String, AiResource> rows = new LinkedHashMap<String, AiResource>();
        
        private boolean throwAfterInsert;
        
        private boolean throwBeforeInsert;
        
        private long insertResult = 1L;
        
        private RuntimeException insertFailure;
        
        private AiResource insertRaceResource;
        
        private boolean updateResult = true;
        
        private boolean updateRaceToExpected;
        
        private boolean listUnavailable;
        
        private int deleteResult = 1;
        
        @Override
        public long insert(AiResource resource) {
            if (insertRaceResource != null) {
                rows.put(insertRaceResource.getName(), insertRaceResource);
                throw new DuplicateKeyException("resource race");
            }
            if (insertFailure != null) {
                throw insertFailure;
            }
            if (throwBeforeInsert) {
                throw new DuplicateKeyException("race");
            }
            rows.put(resource.getName(), resource);
            events.add("resource-insert");
            if (throwAfterInsert) {
                throwAfterInsert = false;
                throw new DuplicateKeyException("uncertain insert");
            }
            return insertResult;
        }
        
        @Override
        public AiResource find(String namespaceId, String name, String type) {
            AiResource result = rows.get(name);
            return result != null && namespaceId.equals(result.getNamespaceId())
                && type.equals(result.getType()) ? result : null;
        }
        
        @Override
        public Page<AiResource> list(QueryCondition queryCondition, int pageNo, int pageSize) {
            if (listUnavailable) {
                return null;
            }
            List<AiResource> matched = new ArrayList<AiResource>();
            for (AiResource row : rows.values()) {
                if (queryCondition.getNamespaceId().equals(row.getNamespaceId())
                    && queryCondition.getType().equals(row.getType())) {
                    matched.add(row);
                }
            }
            return page(matched);
        }
        
        @Override
        public boolean updateMetaCas(String namespaceId, String name, String type,
            long expectedMetaVersion, AiResource newValue) {
            if (updateRaceToExpected) {
                rows.put(name, newValue);
                return false;
            }
            AiResource current = find(namespaceId, name, type);
            if (!updateResult || current == null || current.getMetaVersion() == null
                || current.getMetaVersion() != expectedMetaVersion) {
                return false;
            }
            newValue.setMetaVersion(expectedMetaVersion + 1);
            rows.put(name, newValue);
            events.add("resource-update");
            return true;
        }
        
        @Override
        public boolean updateSourceCas(String namespaceId, String name, String type,
            long expectedMetaVersion, String source) {
            return false;
        }
        
        @Override
        public int delete(String namespaceId, String name, String type) {
            if (deleteResult == 1) {
                rows.remove(name);
                events.add("resource-delete");
            }
            return deleteResult;
        }
        
        @Override
        public boolean updateScope(String namespaceId, String name, String type, String scope) {
            return false;
        }
        
        @Override
        public boolean incrementDownloadCount(String namespaceId, String name, String type,
            long increment) {
            return false;
        }
        
        private AiResource current() {
            return rows.get(AGENT_NAME);
        }
        
        private void clear() {
            rows.clear();
            throwAfterInsert = false;
            throwBeforeInsert = false;
            insertResult = 1L;
            insertFailure = null;
            insertRaceResource = null;
            updateResult = true;
            updateRaceToExpected = false;
            deleteResult = 1;
        }
    }
    
    private final class InMemoryVersionPersistService
        implements AiResourceVersionPersistService {
        
        private final Map<String, AiResourceVersion> rows =
            new LinkedHashMap<String, AiResourceVersion>();
        
        private boolean throwAfterInsert;
        
        private long insertResult = 1L;
        
        private RuntimeException insertFailure;
        
        private String insertRaceAuthor;
        
        private int updateStorageResult = 1;
        
        private int updateStatusResult = 1;
        
        private boolean listUnavailable;
        
        private boolean listWithNull;
        
        private boolean pagesUnavailable;
        
        @Override
        public long insert(AiResourceVersion version) {
            if (insertRaceAuthor != null) {
                AiResourceVersion raced = JacksonUtils.toObj(JacksonUtils.toJson(version),
                    AiResourceVersion.class);
                raced.setAuthor(insertRaceAuthor);
                rows.put(raced.getVersion(), raced);
                throw new DuplicateKeyException("version race");
            }
            if (insertFailure != null) {
                throw insertFailure;
            }
            rows.put(version.getVersion(), version);
            events.add("version-insert:" + version.getVersion());
            if (throwAfterInsert) {
                throwAfterInsert = false;
                throw new DuplicateKeyException("uncertain version insert");
            }
            return insertResult;
        }
        
        @Override
        public AiResourceVersion find(String namespaceId, String name, String type,
            String version) {
            return rows.get(version);
        }
        
        @Override
        public Page<AiResourceVersion> list(String namespaceId, String name, String type,
            String status, int pageNo, int pageSize) {
            if (listUnavailable) {
                return null;
            }
            List<AiResourceVersion> items = new ArrayList<AiResourceVersion>(rows.values());
            if (listWithNull) {
                items.add(null);
            }
            Page<AiResourceVersion> result = page(items);
            if (pagesUnavailable) {
                result.setPagesAvailable(0);
            }
            return result;
        }
        
        @Override
        public int delete(String namespaceId, String name, String type, String version) {
            AiResourceVersion removed = rows.remove(version);
            if (removed != null) {
                events.add("version-delete:" + version);
                return 1;
            }
            return 0;
        }
        
        @Override
        public int deleteByName(String namespaceId, String name) {
            return 0;
        }
        
        @Override
        public int deleteByNameAndType(String namespaceId, String name, String type) {
            return 0;
        }
        
        @Override
        public int updateStatus(String namespaceId, String name, String type, String version,
            String status) {
            if (updateStatusResult != 1) {
                return updateStatusResult;
            }
            AiResourceVersion row = rows.get(version);
            if (row == null) {
                return 0;
            }
            row.setStatus(status);
            events.add("version-status:" + version);
            return 1;
        }
        
        @Override
        public int updateStorage(String namespaceId, String name, String type, String version,
            String storageValue) {
            return updateStorageAndDesc(namespaceId, name, type, version, storageValue, null);
        }
        
        @Override
        public int updateStorageAndDesc(String namespaceId, String name, String type,
            String version, String storageValue, String desc) {
            if (updateStorageResult != 1) {
                return updateStorageResult;
            }
            AiResourceVersion row = rows.get(version);
            if (row == null) {
                return 0;
            }
            row.setStorage(storageValue);
            row.setDesc(desc);
            events.add("version-update:" + version);
            return 1;
        }
        
        @Override
        public int updateStorageMd5(String namespaceId, String name, String type, String version,
            String contentMd5) {
            return 0;
        }
        
        @Override
        public int updatePublishPipelineInfo(String namespaceId, String name, String type,
            String version, String publishPipelineInfo) {
            return 0;
        }
        
        @Override
        public int incrementDownloadCount(String namespaceId, String name, String type,
            String version, long increment) {
            return 0;
        }
        
        private void clear() {
            rows.clear();
            throwAfterInsert = false;
            insertResult = 1L;
            insertFailure = null;
            insertRaceAuthor = null;
            updateStorageResult = 1;
            updateStatusResult = 1;
            listUnavailable = false;
            listWithNull = false;
            pagesUnavailable = false;
        }
    }
    
    private <T> Page<T> page(List<T> items) {
        Page<T> result = new Page<T>();
        result.setPageItems(items);
        result.setTotalCount(items.size());
        result.setPagesAvailable(1);
        return result;
    }
}
