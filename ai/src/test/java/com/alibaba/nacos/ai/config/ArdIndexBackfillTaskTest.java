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
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.ard.ArdEntryBuilder;
import com.alibaba.nacos.ai.service.ard.ArdIndexBuildService;
import com.alibaba.nacos.ai.service.ard.ArdIndexRepository;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdIndexBackfillTask}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdIndexBackfillTaskTest {
    
    private static final long ASYNC_TIMEOUT = 2000L;
    
    private static final String PUBLIC_NAMESPACE = "public";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Mock
    private ArdIndexRepository repository;
    
    @Mock
    private ArdIndexBuildService indexBuildService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    private ArdIndexBackfillTask task;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        lenient().when(namespaceOperationService.getNamespaceList())
            .thenReturn(List.of(new Namespace(PUBLIC_NAMESPACE, "public")));
        lenient().when(resourceManager.listMetaByType(anyString(), anyString(), isNull(), isNull(),
            anyInt(), eq(100))).thenReturn(emptyPage());
        lenient().when(mcpServerOperationService.listMcpServerWithPage(anyString(), isNull(),
            eq(Constants.MCP_LIST_SEARCH_ACCURATE), anyInt(), eq(100))).thenReturn(emptyPage());
        task = new ArdIndexBackfillTask(resourceManager, mcpServerOperationService, repository,
            indexBuildService, namespaceOperationService, configQueryChainService,
            configOperationService);
    }
    
    @AfterEach
    void tearDown() {
        shutdownBackfillExecutor();
        System.clearProperty(ArdIndexBackfillTask.BACKFILL_ENABLED_KEY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void shouldSkipWhenDisabled() throws Exception {
        System.setProperty(ArdIndexBackfillTask.BACKFILL_ENABLED_KEY, "false");
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
        ArdEntry currentEntry = new ArdEntryBuilder().fromAiResource(currentSkill, currentVersion);
        ArdEntry staleEntry = new ArdEntryBuilder().fromAiResource(staleSkill, staleVersion);
        staleEntry.setSourceDigest("stale");
        when(repository.findEntry(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "current-skill")).thenReturn(currentEntry);
        when(repository.findEntry(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
            "stale-skill")).thenReturn(staleEntry);
        when(repository.findEntry(teamNamespace,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "missing-prompt")).thenReturn(null);
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexBuildService, timeout(ASYNC_TIMEOUT)).rebuildLatestAiResource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "stale-skill");
        verify(indexBuildService, timeout(ASYNC_TIMEOUT)).rebuildLatestAiResource(teamNamespace,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "missing-prompt");
        verify(indexBuildService, after(ASYNC_TIMEOUT).never()).rebuildLatestAiResource(
            PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL, "current-skill");
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
        verify(indexBuildService, timeout(ASYNC_TIMEOUT)).rebuildLatestAiResource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "page-two");
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
        doThrow(new IllegalStateException("index failure")).when(indexBuildService)
            .rebuildLatestAiResource(PUBLIC_NAMESPACE, Constants.Skills.RESOURCE_TYPE_SKILL,
                "first");
        
        task.onApplicationEvent(rootContextEvent());
        
        verify(indexBuildService, timeout(ASYNC_TIMEOUT)).rebuildLatestAiResource(PUBLIC_NAMESPACE,
            Constants.Skills.RESOURCE_TYPE_SKILL, "second");
        verifyMarkerReleased();
    }
    
    @Test
    void shouldLoadMcpDetailBeforeRebuild() throws Exception {
        McpServerBasicInfo basic = mcpServer("avatar-mcp", "1.0.0");
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setId(basic.getId());
        detail.setName(basic.getName());
        detail.setVersion(basic.getVersion());
        detail.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        when(mcpServerOperationService.listMcpServerWithPage(PUBLIC_NAMESPACE, null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 100)).thenReturn(page(List.of(basic)));
        when(mcpServerOperationService.getMcpServerDetail(PUBLIC_NAMESPACE, "avatar-mcp", null,
            "1.0.0")).thenReturn(detail);
        
        task.onApplicationEvent(rootContextEvent());
        
        ArgumentCaptor<McpServerBasicInfo> captor = ArgumentCaptor.forClass(
            McpServerBasicInfo.class);
        verify(indexBuildService, timeout(ASYNC_TIMEOUT)).rebuildMcpServer(eq(PUBLIC_NAMESPACE),
            captor.capture());
        assertSame(detail, captor.getValue());
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
        verify(indexBuildService, never()).rebuildMcpServer(anyString(), any());
    }
    
    private void verifyMarkerReleased() throws Exception {
        verify(configOperationService, timeout(ASYNC_TIMEOUT)).deleteConfig(
            eq("nacos.ai.ard.index.backfill"), eq("nacos_internal"), eq(PUBLIC_NAMESPACE), isNull(),
            isNull(), eq("nacos"), isNull());
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
    
    private void shutdownBackfillExecutor() {
        if (task == null) {
            return;
        }
        try {
            Field executorField = ArdIndexBackfillTask.class.getDeclaredField("backfillExecutor");
            executorField.setAccessible(true);
            ExecutorService executor = (ExecutorService) executorField.get(task);
            executor.shutdown();
            if (!executor.awaitTermination(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for backfill executor", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access backfill executor", e);
        }
    }
}
