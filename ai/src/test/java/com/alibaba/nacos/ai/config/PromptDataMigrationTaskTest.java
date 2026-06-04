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

import com.alibaba.nacos.ai.config.NacosPromptLegacyDataReader.LegacyDescriptor;
import com.alibaba.nacos.ai.config.NacosPromptLegacyDataReader.LegacyLabelVersionMapping;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.prompt.PromptOperationService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.utils.PromptDataIdUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for PromptDataMigrationTask.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class PromptDataMigrationTaskTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String PROMPT_GROUP = "nacos-ai-prompt";
    
    private static final String RESOURCE_TYPE_PROMPT = "prompt";
    
    private static final long ASYNC_TIMEOUT = 2000L;
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    @Mock
    private PromptOperationService promptOperationService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private AiResourceStorage storage;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    private PromptDataMigrationTask task;
    
    private NacosPromptLegacyDataReader nacosReader;
    
    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT =
        EnvUtil.getEnvironment();
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        AiResourceStorageRouter.reset();
        lenient().when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        initTaskWithDefaultNamespace();
    }
    
    /**
     * Initialize nacosReader and task with default single-namespace setup.
     * Tests that need a different namespace list should call {@link #initTaskWithNamespaces} instead.
     */
    private void initTaskWithDefaultNamespace() {
        Namespace defaultNs = new Namespace(NS, "public");
        lenient().when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(defaultNs));
        initTaskWithCurrentStubs();
    }
    
    /**
     * Re-initialize nacosReader and task using whatever stubs are currently set on namespaceOperationService.
     */
    private void initTaskWithCurrentStubs() {
        nacosReader =
            new NacosPromptLegacyDataReader(configInfoPersistService, configQueryChainService,
                configOperationService, namespaceOperationService);
        List<PromptLegacyDataReader> readers = Collections.singletonList(nacosReader);
        task =
            new PromptDataMigrationTask(aiResourcePersistService, aiResourceVersionPersistService,
                promptOperationService, configQueryChainService, configOperationService, readers);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
        System.clearProperty("nacos.ai.prompt.migration.enabled");
        System.clearProperty("nacos.ai.prompt.migration.provider");
    }
    
    // ========== onApplicationEvent guard conditions ==========
    
    @Test
    void testShouldSkipWhenNotRootContext() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(event.getApplicationContext()).thenReturn(ctx);
        when(ctx.getParent()).thenReturn(mock(ConfigurableApplicationContext.class));
        
        task.onApplicationEvent(event);
        
        verify(configInfoPersistService, after(500).never())
            .findConfigInfo4Page(anyInt(), anyInt(), any(), any(), any(), any());
    }
    
    @Test
    void testShouldSkipWhenDisabled() {
        System.setProperty("nacos.ai.prompt.migration.enabled", "false");
        EnvUtil.setEnvironment(new StandardEnvironment());
        initTaskWithCurrentStubs();
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(configInfoPersistService, after(500).never())
            .findConfigInfo4Page(anyInt(), anyInt(), any(), any(), any(), any());
    }
    
    // ========== scan / filter / migration flow ==========
    
    @Test
    void testShouldSkipWhenNoLegacyData() {
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(null);
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never())
            .insert(any(AiResource.class));
    }
    
    @Test
    void testShouldSkipWhenAllAlreadyMigrated() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        // ai_resource record exists
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT))
            .thenReturn(new AiResource());
        // All versions also exist in DB — mock list() which is used by hasUnmigratedVersions
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Collections.singletonList("0.0.1");
        ConfigQueryChainResponse mappingResp = new ConfigQueryChainResponse();
        mappingResp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        mappingResp.setContent(JacksonUtils.toJson(mapping));
        when(configQueryChainService.handle(any())).thenReturn(mappingResp);
        Page<AiResourceVersion> versionPage = new Page<>();
        AiResourceVersion existingVersion = new AiResourceVersion();
        existingVersion.setVersion("0.0.1");
        versionPage.setPageItems(Collections.singletonList(existingVersion));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(RESOURCE_TYPE_PROMPT),
            any(), anyInt(), anyInt())).thenReturn(versionPage);
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never())
            .insert(any(AiResource.class));
    }
    
    @Test
    void testShouldAcquireMarkerAndMigratePromptSuccessfully() throws Exception {
        // 1. Scan returns one descriptor dataId
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        
        // 2. Not yet migrated
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT)).thenReturn(null);
        
        // 3. Marker creation succeeds (no exception from publishConfig)
        
        // 4. Config reads: descriptor + mapping + version content
        LegacyDescriptor descriptor = new LegacyDescriptor();
        descriptor.promptKey = PROMPT_KEY;
        descriptor.description = "test desc";
        descriptor.bizTags = Arrays.asList("tag1");
        
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Collections.singletonList("0.0.1");
        mapping.latestVersion = "0.0.1";
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "0.0.1");
        mapping.labels = labels;
        
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setPromptKey(PROMPT_KEY);
        versionContent.setVersion("0.0.1");
        versionContent.setTemplate("Hello {{name}}");
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest) {
                com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                    (com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest) arg;
                String dataId = req.getDataId();
                ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
                resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
                if (dataId != null && dataId.endsWith(".descriptor.json")) {
                    resp.setContent(JacksonUtils.toJson(descriptor));
                } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                    resp.setContent(JacksonUtils.toJson(mapping));
                } else {
                    resp.setContent(JacksonUtils.toJson(versionContent));
                }
                return resp;
            }
            return new ConfigQueryChainResponse();
        });
        
        // 5. Version not yet in DB
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.1"))
            .thenReturn(null);
        
        // 6. readVersionContent uses configInfoPersistService.findConfigAllInfo, not configQueryChainService
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Verify: meta record inserted
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT)).insert(any(AiResource.class));
        // Verify: version record inserted
        verify(aiResourceVersionPersistService, timeout(ASYNC_TIMEOUT))
            .insert(any(AiResourceVersion.class));
        // Verify: content written to typed storage
        verify(storage, timeout(ASYNC_TIMEOUT)).save(any(StorageKey.class), any(byte[].class));
        // Verify: legacy mirror refreshed
        verify(promptOperationService, timeout(ASYNC_TIMEOUT)).refreshLatestMirror(NS, PROMPT_KEY);
        // Verify: marker released
        verify(configOperationService, timeout(ASYNC_TIMEOUT))
            .deleteConfig(eq("nacos.ai.prompt.migration"), eq("nacos_internal"), eq(NS), any(),
                any(),
                eq("nacos"), any());
    }
    
    @Test
    void testShouldSkipWhenMarkerAcquireFails() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT)).thenReturn(null);
        
        // Marker creation fails: another node holds it
        when(configOperationService.publishConfig(any(), any(), any()))
            .thenThrow(new ConfigAlreadyExistsException("marker exists"));
        
        // Answer-based mock: return proper data for scan reads, timestamp for marker staleness check
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.promptKey = PROMPT_KEY;
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
                mapping.promptKey = PROMPT_KEY;
                mapping.versions = Collections.singletonList("0.0.1");
                resp.setContent(JacksonUtils.toJson(mapping));
            } else {
                // Marker staleness check: return current timestamp (not stale)
                resp.setContent(String.valueOf(System.currentTimeMillis()));
            }
            return resp;
        });
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Should NOT proceed with migration
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never())
            .insert(any(AiResource.class));
    }
    
    @Test
    void testMigrateOneVersionShouldSkipDbInsertWhenAlreadyExists() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        // First call: ai_resource not found (needs migration); after insert: found
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT))
            .thenReturn(null);
        
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Collections.singletonList("0.0.1");
        mapping.latestVersion = "0.0.1";
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                resp.setContent(JacksonUtils.toJson(mapping));
            } else if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.promptKey = PROMPT_KEY;
                resp.setContent(JacksonUtils.toJson(desc));
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        // Version already exists in DB — DB insert should be skipped, but storage write still happens
        AiResourceVersion existingVersion = new AiResourceVersion();
        existingVersion.setVersion("0.0.1");
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.1"))
            .thenReturn(existingVersion);
        
        // readVersionContent uses configInfoPersistService.findConfigAllInfo
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setPromptKey(PROMPT_KEY);
        versionContent.setVersion("0.0.1");
        versionContent.setTemplate("Hello");
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Meta should still be inserted
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT)).insert(any(AiResource.class));
        // Storage write still happens (idempotent overwrite)
        verify(storage, timeout(ASYNC_TIMEOUT)).save(any(StorageKey.class), any(byte[].class));
        // But version DB insert should be skipped
        verify(aiResourceVersionPersistService, after(ASYNC_TIMEOUT).never())
            .insert(any(AiResourceVersion.class));
    }
    
    @Test
    void testShouldReleaseMigrationMarkerEvenOnFailure() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT)).thenReturn(null);
        
        // Proper scan mock for descriptor + mapping reads
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.promptKey = PROMPT_KEY;
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
                mapping.promptKey = PROMPT_KEY;
                mapping.versions = Collections.singletonList("0.0.1");
                resp.setContent(JacksonUtils.toJson(mapping));
            }
            return resp;
        });
        
        // Migration fails during ai_resource insert
        when(aiResourcePersistService.insert(any(AiResource.class)))
            .thenThrow(new RuntimeException("DB failure"));
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Marker should still be released in finally block
        verify(configOperationService, timeout(ASYNC_TIMEOUT))
            .deleteConfig(eq("nacos.ai.prompt.migration"), eq("nacos_internal"), eq(NS), any(),
                any(),
                eq("nacos"), any());
    }
    
    @Test
    void testShouldSkipPromptWhenMappingHasNoVersions() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        
        // Mapping with no versions — buildLegacyPromptData returns null, so scan yields empty list
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = new ArrayList<>();
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                resp.setContent(JacksonUtils.toJson(mapping));
            } else if (dataId != null && dataId.endsWith(".descriptor.json")) {
                resp.setContent("{}");
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Should not insert anything when mapping has no versions
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never())
            .insert(any(AiResource.class));
    }
    
    // ========== Multi-namespace tests ==========
    
    @Test
    void testShouldMigratePromptsFromMultipleNamespaces() throws Exception {
        String ns2 = "dev-namespace";
        
        // Two namespaces
        Namespace defaultNs = new Namespace(NS, "public");
        Namespace devNs = new Namespace(ns2, "dev");
        when(namespaceOperationService.getNamespaceList())
            .thenReturn(Arrays.asList(defaultNs, devNs));
        initTaskWithCurrentStubs();
        
        // Scan: one prompt in each namespace
        String prompt2 = "dev-prompt";
        Page<ConfigInfo> scanPageNs1 = buildScanPage(PROMPT_KEY);
        Page<ConfigInfo> scanPageNs2 = buildScanPage(prompt2);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPageNs1);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(ns2), any()))
            .thenReturn(scanPageNs2);
        
        // Neither migrated yet
        when(aiResourcePersistService.find(eq(NS), eq(PROMPT_KEY), eq(RESOURCE_TYPE_PROMPT)))
            .thenReturn(null);
        when(aiResourcePersistService.find(eq(ns2), eq(prompt2), eq(RESOURCE_TYPE_PROMPT)))
            .thenReturn(null);
        
        // Version not in DB
        when(aiResourceVersionPersistService.find(eq(NS), eq(PROMPT_KEY), eq(RESOURCE_TYPE_PROMPT),
            eq("0.0.1")))
            .thenReturn(null);
        when(aiResourceVersionPersistService.find(eq(ns2), eq(prompt2), eq(RESOURCE_TYPE_PROMPT),
            eq("0.0.1")))
            .thenReturn(null);
        
        // Config reads
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.description = "desc";
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
                mapping.versions = Collections.singletonList("0.0.1");
                mapping.latestVersion = "0.0.1";
                resp.setContent(JacksonUtils.toJson(mapping));
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        // readVersionContent
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setTemplate("Hello");
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), any()))
            .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Both prompts should be migrated: 2 meta inserts
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT).times(2))
            .insert(any(AiResource.class));
        // 2 version inserts
        verify(aiResourceVersionPersistService, timeout(ASYNC_TIMEOUT).times(2))
            .insert(any(AiResourceVersion.class));
    }
    
    @Test
    void testShouldFallbackToDefaultNamespaceWhenNamespaceListFails() throws Exception {
        // Namespace service throws exception
        when(namespaceOperationService.getNamespaceList())
            .thenThrow(new RuntimeException("connection refused"));
        initTaskWithCurrentStubs();
        
        // Default namespace has a prompt
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID), any()))
            .thenReturn(scanPage);
        
        // Not yet migrated
        when(aiResourcePersistService.find(
            eq(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID),
            eq(PROMPT_KEY), eq(RESOURCE_TYPE_PROMPT))).thenReturn(null);
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.promptKey = PROMPT_KEY;
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
                mapping.versions = Collections.singletonList("0.0.1");
                mapping.latestVersion = "0.0.1";
                resp.setContent(JacksonUtils.toJson(mapping));
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setTemplate("Hello");
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP),
            eq(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID)))
            .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Should still migrate the default namespace prompt
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT)).insert(any(AiResource.class));
    }
    
    // ========== cleanupLegacyConfig tests ==========
    
    @Test
    void testCleanupLegacyConfigShouldDeleteDescriptorMappingAndVersionConfigs() throws Exception {
        List<String> versions = Arrays.asList("0.0.1", "0.0.2");
        
        nacosReader.cleanupLegacyData(NS, PROMPT_KEY, versions);
        
        // descriptor
        verify(configOperationService).deleteConfig(
            eq(PromptDataIdUtils.buildDescriptorDataId(PROMPT_KEY)),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any());
        // label-version-mapping
        verify(configOperationService).deleteConfig(
            eq(PromptDataIdUtils.buildLabelVersionMappingDataId(PROMPT_KEY)),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any());
        // version configs
        verify(configOperationService).deleteConfig(
            eq(PromptDataIdUtils.buildVersionDataId(PROMPT_KEY, "0.0.1")),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any());
        verify(configOperationService).deleteConfig(
            eq(PromptDataIdUtils.buildVersionDataId(PROMPT_KEY, "0.0.2")),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any());
        // Total: 2 (descriptor + mapping) + 2 (versions) = 4
        verify(configOperationService, times(4)).deleteConfig(any(), any(), any(), any(), any(),
            any(), any());
    }
    
    @Test
    void testCleanupLegacyConfigShouldHandleNullVersions() throws Exception {
        nacosReader.cleanupLegacyData(NS, PROMPT_KEY, null);
        
        // Only descriptor + mapping deleted, no version deletes
        verify(configOperationService, times(2)).deleteConfig(any(), any(), any(), any(), any(),
            any(), any());
    }
    
    @Test
    void testCleanupLegacyConfigShouldSuppressDeleteExceptions() throws Exception {
        // First delete throws, second should still proceed
        when(configOperationService.deleteConfig(
            eq(PromptDataIdUtils.buildDescriptorDataId(PROMPT_KEY)),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any()))
            .thenThrow(new RuntimeException("delete failed"));
        
        nacosReader.cleanupLegacyData(NS, PROMPT_KEY, Collections.singletonList("0.0.1"));
        
        // mapping delete should still be called despite descriptor delete failure
        verify(configOperationService).deleteConfig(
            eq(PromptDataIdUtils.buildLabelVersionMappingDataId(PROMPT_KEY)),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any());
        // version delete should still be called
        verify(configOperationService).deleteConfig(
            eq(PromptDataIdUtils.buildVersionDataId(PROMPT_KEY, "0.0.1")),
            eq(PROMPT_GROUP), eq(NS), any(), any(), eq("nacos"), any());
    }
    
    @Test
    void testCleanupLegacyConfigViaTaskShouldDelegateToReader() {
        task.cleanupLegacyConfig(NS, PROMPT_KEY, Arrays.asList("0.0.1"));
        
        // Should delegate to nacosReader which calls deleteConfig
        verify(configOperationService, atLeastOnce()).deleteConfig(any(), any(), any(), any(),
            any(), any(), any());
    }
    
    @Test
    void testCleanupLegacyConfigViaTaskShouldNoopWhenNoReaderFound() {
        // Use a provider type that doesn't match any reader
        System.setProperty("nacos.ai.prompt.migration.provider", "nonexistent");
        EnvUtil.setEnvironment(new StandardEnvironment());
        task =
            new PromptDataMigrationTask(aiResourcePersistService, aiResourceVersionPersistService,
                promptOperationService, configQueryChainService, configOperationService,
                Collections.singletonList(nacosReader));
        
        task.cleanupLegacyConfig(NS, PROMPT_KEY, Arrays.asList("0.0.1"));
        
        // No deleteConfig calls since reader not found
        verify(configOperationService, never()).deleteConfig(any(), any(), any(), any(), any(),
            any(), any());
    }
    
    // ========== hasUnmigratedVersions / filterNeedsMigration edge cases ==========
    
    @Test
    void testShouldDetectPartiallyMigratedPrompt() throws Exception {
        // Prompt exists in DB but only 1 of 2 versions migrated
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT))
            .thenReturn(new AiResource());
        
        // Legacy has 2 versions
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Arrays.asList("0.0.1", "0.0.2");
        mapping.latestVersion = "0.0.2";
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.promptKey = PROMPT_KEY;
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                resp.setContent(JacksonUtils.toJson(mapping));
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        // Only version 0.0.1 is in DB; 0.0.2 is not
        Page<AiResourceVersion> versionPage = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("0.0.1");
        versionPage.setPageItems(Collections.singletonList(v1));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(RESOURCE_TYPE_PROMPT),
            any(), anyInt(), anyInt())).thenReturn(versionPage);
        
        // Version 0.0.1 already exists, 0.0.2 does not
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.1"))
            .thenReturn(v1);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.2"))
            .thenReturn(null);
        
        // readVersionContent
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setTemplate("Hello");
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Meta insert will be attempted (may throw duplicate, that's fine — existing check handles it)
        // At least one version insert should happen (for 0.0.2)
        verify(aiResourceVersionPersistService, timeout(ASYNC_TIMEOUT).atLeastOnce())
            .insert(any(AiResourceVersion.class));
    }
    
    @Test
    void testShouldSkipWhenAllVersionsAlreadyMigratedViaListCheck() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT))
            .thenReturn(new AiResource());
        
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Arrays.asList("0.0.1", "0.0.2");
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                resp.setContent(JacksonUtils.toJson(mapping));
            }
            return resp;
        });
        
        // Both versions already in DB
        Page<AiResourceVersion> versionPage = new Page<>();
        AiResourceVersion v1 = new AiResourceVersion();
        v1.setVersion("0.0.1");
        AiResourceVersion v2 = new AiResourceVersion();
        v2.setVersion("0.0.2");
        versionPage.setPageItems(Arrays.asList(v1, v2));
        when(aiResourceVersionPersistService.list(eq(NS), eq(PROMPT_KEY), eq(RESOURCE_TYPE_PROMPT),
            any(), anyInt(), anyInt())).thenReturn(versionPage);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // All migrated — no inserts
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never())
            .insert(any(AiResource.class));
    }
    
    // ========== latestVersion label auto-fill test ==========
    
    @Test
    void testShouldAutoFillLatestLabelWhenMissing() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT)).thenReturn(null);
        
        // Mapping has latestVersion but labels does NOT contain "latest" key
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Collections.singletonList("0.0.1");
        mapping.latestVersion = "0.0.1";
        mapping.labels = new HashMap<>(); // no "latest" key
        
        LegacyDescriptor descriptor = new LegacyDescriptor();
        descriptor.promptKey = PROMPT_KEY;
        descriptor.description = "test";
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if (dataId != null && dataId.endsWith(".descriptor.json")) {
                resp.setContent(JacksonUtils.toJson(descriptor));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                resp.setContent(JacksonUtils.toJson(mapping));
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setTemplate("Hello");
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(versionConfigAllInfo);
        
        // Capture the inserted AiResource to verify versionInfo contains "latest" label
        org.mockito.ArgumentCaptor<AiResource> resourceCaptor =
            org.mockito.ArgumentCaptor.forClass(AiResource.class);
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT)).insert(resourceCaptor.capture());
        AiResource inserted = resourceCaptor.getValue();
        assertNotNull(inserted.getVersionInfo());
        assertTrue(inserted.getVersionInfo().contains("\"latest\""));
        assertTrue(inserted.getVersionInfo().contains("0.0.1"));
    }
    
    // ========== NacosPromptLegacyDataReader scan edge cases ==========
    
    @Test
    void testScanShouldPaginateCorrectly() {
        // First page: full (100 items), second page: partial (1 item), third page: null
        List<ConfigInfo> page1Items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ConfigInfo info = new ConfigInfo();
            // Only some are descriptor dataIds
            if (i < 3) {
                info.setDataId(PromptDataIdUtils.buildDescriptorDataId("prompt-" + i));
            } else {
                info.setDataId("some-other-config-" + i);
            }
            info.setGroup(PROMPT_GROUP);
            page1Items.add(info);
        }
        Page<ConfigInfo> page1 = new Page<>();
        page1.setPageItems(page1Items);
        
        List<ConfigInfo> page2Items = new ArrayList<>();
        ConfigInfo lastItem = new ConfigInfo();
        lastItem.setDataId(PromptDataIdUtils.buildDescriptorDataId("prompt-last"));
        lastItem.setGroup(PROMPT_GROUP);
        page2Items.add(lastItem);
        Page<ConfigInfo> page2 = new Page<>();
        page2.setPageItems(page2Items);
        
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(page1);
        when(configInfoPersistService.findConfigInfo4Page(eq(2), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(page2);
        
        // Mapping returns empty versions so buildLegacyPromptData returns null — that's fine,
        // we just want to verify pagination works
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
            mapping.versions = new ArrayList<>();
            resp.setContent(JacksonUtils.toJson(mapping));
            return resp;
        });
        
        List<LegacyPromptData> result = nacosReader.scanLegacyPrompts();
        
        // All 4 descriptor dataIds found, but all skipped because no versions
        assertTrue(result.isEmpty());
        // Verify both pages were queried
        verify(configInfoPersistService).findConfigInfo4Page(eq(1), eq(100), any(),
            eq(PROMPT_GROUP), eq(NS), any());
        verify(configInfoPersistService).findConfigInfo4Page(eq(2), eq(100), any(),
            eq(PROMPT_GROUP), eq(NS), any());
    }
    
    @Test
    void testReadVersionContentShouldFallbackToRawTemplateOnParseFailure() {
        String versionDataId = PromptDataIdUtils.buildVersionDataId(PROMPT_KEY, "0.0.1");
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setContent("This is plain text, not JSON");
        configAllInfo.setMd5("abc123");
        configAllInfo.setCreateUser("testUser");
        when(
            configInfoPersistService.findConfigAllInfo(eq(versionDataId), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(configAllInfo);
        
        PromptVersionInfo result = nacosReader.readVersionContent(NS, PROMPT_KEY, "0.0.1");
        
        assertNotNull(result);
        assertEquals("This is plain text, not JSON", result.getTemplate());
        assertEquals(PROMPT_KEY, result.getPromptKey());
        assertEquals("0.0.1", result.getVersion());
        assertEquals("abc123", result.getMd5());
        assertEquals("testUser", result.getSrcUser());
    }
    
    @Test
    void testReadVersionContentShouldReturnNullWhenConfigNotFound() {
        String versionDataId = PromptDataIdUtils.buildVersionDataId(PROMPT_KEY, "0.0.1");
        when(
            configInfoPersistService.findConfigAllInfo(eq(versionDataId), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(null);
        
        PromptVersionInfo result = nacosReader.readVersionContent(NS, PROMPT_KEY, "0.0.1");
        
        assertNull(result);
    }
    
    @Test
    void testReadVersionContentShouldReturnNullWhenContentIsBlank() {
        String versionDataId = PromptDataIdUtils.buildVersionDataId(PROMPT_KEY, "0.0.1");
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setContent("   ");
        when(
            configInfoPersistService.findConfigAllInfo(eq(versionDataId), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(configAllInfo);
        
        PromptVersionInfo result = nacosReader.readVersionContent(NS, PROMPT_KEY, "0.0.1");
        
        assertNull(result);
    }
    
    // ========== Stale marker recovery test ==========
    
    @Test
    void testShouldRecoverFromStaleMarkerAndMigrate() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP),
            eq(NS), any()))
            .thenReturn(scanPage);
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT)).thenReturn(null);
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.1"))
            .thenReturn(null);
        
        // First publishConfig: marker already exists (stale); after delete+retry: succeeds
        when(configOperationService.publishConfig(any(), any(), any()))
            .thenThrow(new ConfigAlreadyExistsException("marker exists"))
            .thenReturn(true);
        
        // configQueryChainService: return stale timestamp for marker check, proper data for scan
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req =
                invocation.getArgument(0);
            String dataId = req.getDataId();
            ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
            resp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
            if ("nacos.ai.prompt.migration".equals(dataId)) {
                // Return a timestamp from 15 minutes ago (stale)
                resp.setContent(String.valueOf(System.currentTimeMillis() - 15 * 60 * 1000L));
            } else if (dataId != null && dataId.endsWith(".descriptor.json")) {
                LegacyDescriptor desc = new LegacyDescriptor();
                desc.promptKey = PROMPT_KEY;
                resp.setContent(JacksonUtils.toJson(desc));
            } else if (dataId != null && dataId.endsWith(".label-version-mapping.json")) {
                LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
                mapping.versions = Collections.singletonList("0.0.1");
                mapping.latestVersion = "0.0.1";
                resp.setContent(JacksonUtils.toJson(mapping));
            } else {
                resp.setContent("{}");
            }
            return resp;
        });
        
        PromptVersionInfo versionContent = new PromptVersionInfo();
        versionContent.setTemplate("Hello");
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), eq(NS)))
            .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Should recover from stale marker and proceed with migration
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT)).insert(any(AiResource.class));
    }
    
    // ========== Helper methods ==========
    
    private ApplicationReadyEvent createRootContextEvent() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(event.getApplicationContext()).thenReturn(ctx);
        when(ctx.getParent()).thenReturn(null);
        return event;
    }
    
    private Page<ConfigInfo> buildScanPage(String... promptKeys) {
        Page<ConfigInfo> page = new Page<>();
        List<ConfigInfo> items = new ArrayList<>();
        for (String key : promptKeys) {
            ConfigInfo info = new ConfigInfo();
            info.setDataId(PromptDataIdUtils.buildDescriptorDataId(key));
            info.setGroup(PROMPT_GROUP);
            items.add(info);
        }
        page.setPageItems(items);
        return page;
    }
}
