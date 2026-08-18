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
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceEmbeddingService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchDocumentBuilder;
import com.alibaba.nacos.ai.service.search.AiResourceSearchRepository;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiResourceIndexBackfillTask}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiResourceIndexBackfillTaskTest {
    
    private static final long ASYNC_TIMEOUT = 2000L;
    
    private static final String PUBLIC_NAMESPACE = "public";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private AiResourceSearchRepository repository;
    
    @Mock
    private AiResourceIndexMaintenanceService indexMaintenanceService;
    
    @Mock
    private AiResourceEmbeddingService embeddingService;
    
    @Mock
    private AiResourceVectorIndex vectorIndex;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    private AiResourceIndexBackfillTask task;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        lenient().when(namespaceOperationService.getNamespaceList())
            .thenReturn(List.of(new Namespace(PUBLIC_NAMESPACE, "public")));
        lenient().when(resourceManager.listMetaByType(anyString(), anyString(), isNull(), isNull(),
            anyInt(), eq(100))).thenReturn(emptyPage());
        lenient().when(mcpServerOperationService.listMcpServerWithPage(anyString(), isNull(),
            eq(Constants.MCP_LIST_SEARCH_ACCURATE), anyInt(), eq(100))).thenReturn(emptyPage());
        lenient().when(
            indexMaintenanceService.scheduleReconciliation(anyString(), anyString(), anyString()))
            .thenReturn(true);
        task =
            new AiResourceIndexBackfillTask(resourceManager, mcpServerOperationService, repository,
                indexMaintenanceService, embeddingService, vectorIndex,
                namespaceOperationService, configQueryChainService, configOperationService);
    }
    
    @AfterEach
    void tearDown() {
        if (task != null) {
            task.destroy();
        }
        System.clearProperty(AiResourceIndexBackfillTask.BACKFILL_ENABLED_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void shouldSkipWhenDisabled() throws Exception {
        System.setProperty(AiResourceIndexBackfillTask.BACKFILL_ENABLED_KEY, "false");
        EnvUtil.setEnvironment(new StandardEnvironment());
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(configOperationService, after(500).never()).publishConfig(any(), any(), any());
    }
    
    @Test
    void shouldRebuildMissingAndStaleEntriesAcrossNamespaces() throws Exception {
        String teamNamespace = "team-a";
        when(namespaceOperationService.getNamespaceList()).thenReturn(
            List.of(new Namespace(PUBLIC_NAMESPACE, "public"),
                new Namespace(teamNamespace, "team-a")));
        AiResource currentSkill = resource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "current-skill", "1.0.0");
        AiResource staleSkill = resource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "stale-skill", "1.0.0");
        AiResource missingPrompt = resource(teamNamespace,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "missing-prompt", "2.0.0");
        AiResourceVersion currentVersion = version(currentSkill, "1.0.0");
        AiResourceVersion staleVersion = version(staleSkill, "1.0.0");
        AiResourceVersion promptVersion = version(missingPrompt, "2.0.0");
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 1, 100))
            .thenReturn(page(List.of(currentSkill, staleSkill)));
        when(resourceManager.listMetaByType(teamNamespace,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, null, null, 1, 100))
            .thenReturn(page(List.of(missingPrompt)));
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "current-skill",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(currentVersion);
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "stale-skill",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(staleVersion);
        when(resourceManager.findVersion(teamNamespace, "missing-prompt",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "2.0.0"))
            .thenReturn(promptVersion);
        AiResourceSearchDocument currentEntry =
            new AiResourceSearchDocumentBuilder().fromAiResource(currentSkill, currentVersion);
        AiResourceSearchDocument staleEntry =
            new AiResourceSearchDocumentBuilder().fromAiResource(staleSkill, staleVersion);
        staleEntry.setSourceDigest("stale");
        when(repository.findEntry(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "current-skill")).thenReturn(currentEntry);
        when(repository.findEntry(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "stale-skill")).thenReturn(staleEntry);
        when(repository.findEntry(teamNamespace,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "missing-prompt")).thenReturn(null);
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "stale-skill");
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            teamNamespace, NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "missing-prompt");
        verify(indexMaintenanceService, after(ASYNC_TIMEOUT).never()).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "current-skill");
        verify(indexMaintenanceService, never()).schedule(anyString(), anyString(), anyString());
        verifyMarkerReleased();
    }
    
    @Test
    void shouldScanNextPage() throws Exception {
        List<AiResource> firstPage = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            AiResource resource = resource(PUBLIC_NAMESPACE,
                Constants.Skills.RESOURCE_TYPE_SKILL, "offline-" + i, null);
            firstPage.add(resource);
        }
        AiResource lastResource = resource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "page-two", "1.0.0");
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 1, 100))
            .thenReturn(page(firstPage));
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 2, 100))
            .thenReturn(page(List.of(lastResource)));
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "page-two",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(lastResource, "1.0.0"));
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(resourceManager, timeout(ASYNC_TIMEOUT)).listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 2, 100);
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "page-two");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldContinueAfterOneResourceFails() throws Exception {
        AiResource first = resource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "first", "1.0.0");
        AiResource second = resource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "second", "1.0.0");
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 1, 100))
            .thenReturn(page(List.of(first, second)));
        when(resourceManager.findVersion(eq(PUBLIC_NAMESPACE), anyString(),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq("1.0.0")))
            .thenAnswer(invocation -> version(
                "first".equals(invocation.getArgument(1)) ? first : second, "1.0.0"));
        when(indexMaintenanceService.scheduleReconciliation(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "first"))
            .thenThrow(new IllegalStateException("index failure"));
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "second");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldContinueAfterOneResourceProjectionFails() throws Exception {
        AiResource first = resource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "first", "1.0.0");
        AiResource second = resource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "second", "1.0.0");
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 1, 100))
            .thenReturn(page(List.of(first, second)));
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "first",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenThrow(new IllegalStateException("projection failure"));
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "second",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(second, "1.0.0"));
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "second");
        verify(indexMaintenanceService, never()).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "first");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldScheduleWhenVectorDocumentsAreIncomplete() throws Exception {
        AiResource skill = resource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "vector-stale", "1.0.0");
        AiResourceVersion version = version(skill, "1.0.0");
        AiResourceSearchDocument current =
            new AiResourceSearchDocumentBuilder().fromAiResource(skill, version);
        current.setId(10L);
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 1, 100))
            .thenReturn(page(List.of(skill)));
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "vector-stale",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(version);
        when(repository.findEntry(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "vector-stale")).thenReturn(current);
        when(vectorIndex.available()).thenReturn(true);
        when(embeddingService.model()).thenReturn("model-v2");
        when(repository.countChunks(10L)).thenReturn(3);
        when(vectorIndex.isResourceVersionReady(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "vector-stale", "1.0.0", "model-v2", 10L, 3))
            .thenReturn(false);
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "vector-stale");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldScheduleOrphanIndexDeletion() throws Exception {
        AiResourceSearchDocument orphan = new AiResourceSearchDocument();
        orphan.setId(1L);
        orphan.setNamespaceId(PUBLIC_NAMESPACE);
        orphan.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        orphan.setResourceName("deleted-skill");
        when(repository.scanEntries(PUBLIC_NAMESPACE,
            List.of(Constants.Skills.RESOURCE_TYPE_SKILL), 0L, 100))
            .thenReturn(List.of(orphan));
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "deleted-skill");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldKeepCanonicalEntryAndSkipTransientExistenceFailure() throws Exception {
        AiResourceSearchDocument stored = new AiResourceSearchDocument();
        stored.setId(1L);
        stored.setNamespaceId(PUBLIC_NAMESPACE);
        stored.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        stored.setResourceName("stored-skill");
        AiResourceSearchDocument uncertain = new AiResourceSearchDocument();
        uncertain.setId(2L);
        uncertain.setNamespaceId(PUBLIC_NAMESPACE);
        uncertain.setResourceType(AiResourceConstants.RESOURCE_TYPE_MCP);
        uncertain.setResourceName("uncertain-mcp");
        when(repository.scanEntries(eq(PUBLIC_NAMESPACE), anyList(), eq(0L), eq(100)))
            .thenAnswer(invocation -> {
                List<?> resourceTypes = invocation.getArgument(1);
                if (resourceTypes.contains(Constants.Skills.RESOURCE_TYPE_SKILL)) {
                    return List.of(stored);
                }
                if (resourceTypes.contains(AiResourceConstants.RESOURCE_TYPE_MCP)) {
                    return List.of(uncertain);
                }
                return Collections.emptyList();
            });
        when(resourceManager.findMeta(PUBLIC_NAMESPACE, "stored-skill",
            Constants.Skills.RESOURCE_TYPE_SKILL))
            .thenReturn(resource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
                "stored-skill", "1.0.0"));
        when(mcpServerOperationService.getMcpServerDetail(PUBLIC_NAMESPACE, "uncertain-mcp",
            null, null)).thenThrow(new NacosException(NacosException.SERVER_ERROR,
                "temporary failure"));
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, after(ASYNC_TIMEOUT).never()).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "stored-skill");
        verify(indexMaintenanceService, never()).scheduleReconciliation(
            PUBLIC_NAMESPACE, AiResourceConstants.RESOURCE_TYPE_MCP, "uncertain-mcp");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldRecordRejectedReconciliationWithoutStoppingBackfill() throws Exception {
        AiResource rejected = resource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "rejected", "1.0.0");
        when(resourceManager.listMetaByType(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, null, null, 1, 100))
            .thenReturn(page(List.of(rejected)));
        when(resourceManager.findVersion(PUBLIC_NAMESPACE, "rejected",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(version(rejected, "1.0.0"));
        when(indexMaintenanceService.scheduleReconciliation(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "rejected")).thenReturn(false);
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "rejected");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldScheduleMcpReconciliation() throws Exception {
        McpServerBasicInfo basic = mcpServer("avatar-mcp", "1.0.0");
        when(mcpServerOperationService.listMcpServerWithPage(PUBLIC_NAMESPACE, null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100)).thenReturn(page(List.of(basic)));
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexMaintenanceService, timeout(ASYNC_TIMEOUT)).scheduleReconciliation(
            PUBLIC_NAMESPACE, "mcp", "avatar-mcp");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldSkipWhenAnotherNodeOwnsMarker() throws Exception {
        when(configOperationService.publishConfig(any(), any(), any()))
            .thenThrow(new ConfigAlreadyExistsException("marker exists"));
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(String.valueOf(System.currentTimeMillis()));
        when(configQueryChainService.handle(any())).thenReturn(response);
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(namespaceOperationService, after(ASYNC_TIMEOUT).never()).getNamespaceList();
        verify(indexMaintenanceService, never()).scheduleReconciliation(anyString(), anyString(),
            anyString());
    }
    
    @Test
    void shouldTakeOverExpiredMarkerWithCas() throws Exception {
        when(configOperationService.publishConfig(any(), any(), any()))
            .thenThrow(new ConfigAlreadyExistsException("marker exists"))
            .thenReturn(true);
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent("0");
        response.setMd5("stale-marker-md5");
        when(configQueryChainService.handle(any())).thenReturn(response);
        
        task.onApplicationEvent(rootContextEvent());
        
        ArgumentCaptor<ConfigRequestInfo> requestInfo =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService, timeout(ASYNC_TIMEOUT).times(2))
            .publishConfig(any(), requestInfo.capture(), isNull());
        assertEquals("stale-marker-md5", requestInfo.getAllValues().get(1).getCasMd5());
    }
    
    private void verifyMarkerReleased() throws Exception {
        verify(configOperationService, timeout(ASYNC_TIMEOUT)).publishConfig(any(), any(),
            isNull());
    }
    
    private ApplicationReadyEvent rootContextEvent() {
        ApplicationReadyEvent event = org.mockito.Mockito.mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext context =
            org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        when(event.getApplicationContext()).thenReturn(context);
        when(context.getParent()).thenReturn(null);
        return event;
    }
    
    private AiResource resource(String namespaceId, String type, String name,
        String latestVersion) {
        AiResource resource = new AiResource();
        resource.setNamespaceId(namespaceId);
        resource.setType(type);
        resource.setName(name);
        resource.setDesc("description for " + name);
        resource.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        if (latestVersion != null) {
            resource.setVersionInfo(
                JacksonUtils.toJson(Map.of("labels", Map.of("latest", latestVersion))));
        }
        return resource;
    }
    
    private AiResourceVersion version(AiResource resource, String version) {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(resource.getNamespaceId());
        result.setType(resource.getType());
        result.setName(resource.getName());
        result.setVersion(version);
        result.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        result.setDesc(resource.getDesc());
        return result;
    }
    
    private McpServerBasicInfo mcpServer(String id, String version) {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setId(id);
        server.setName("Avatar MCP");
        server.setVersion(version);
        server.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        return server;
    }
    
    private <T> Page<T> page(List<T> items) {
        Page<T> page = new Page<>();
        page.setPageItems(items);
        return page;
    }
    
    private <T> Page<T> emptyPage() {
        return page(Collections.emptyList());
    }
    
}
