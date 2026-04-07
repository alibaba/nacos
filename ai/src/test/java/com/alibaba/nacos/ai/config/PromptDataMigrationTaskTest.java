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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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
        nacosReader = new NacosPromptLegacyDataReader(configInfoPersistService, configQueryChainService);
        List<PromptLegacyDataReader> readers = Collections.singletonList(nacosReader);
        task = new PromptDataMigrationTask(aiResourcePersistService, aiResourceVersionPersistService,
                promptOperationService, configQueryChainService, configOperationService, readers);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
        System.clearProperty("nacos.ai.prompt.migration.enabled");
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
        List<PromptLegacyDataReader> readers = Collections.singletonList(nacosReader);
        task = new PromptDataMigrationTask(aiResourcePersistService, aiResourceVersionPersistService,
                promptOperationService, configQueryChainService, configOperationService, readers);
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(configInfoPersistService, after(500).never())
                .findConfigInfo4Page(anyInt(), anyInt(), any(), any(), any(), any());
    }
    
    // ========== scan / filter / migration flow ==========
    
    @Test
    void testShouldSkipWhenNoLegacyData() {
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
                .thenReturn(null);
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never()).insert(any(AiResource.class));
    }
    
    @Test
    void testShouldSkipWhenAllAlreadyMigrated() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
                .thenReturn(scanPage);
        // ai_resource record exists
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT))
                .thenReturn(new AiResource());
        // All versions also exist in DB
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Collections.singletonList("0.0.1");
        ConfigQueryChainResponse mappingResp = new ConfigQueryChainResponse();
        mappingResp.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        mappingResp.setContent(JacksonUtils.toJson(mapping));
        when(configQueryChainService.handle(any())).thenReturn(mappingResp);
        AiResourceVersion existingVersion = new AiResourceVersion();
        existingVersion.setVersion("0.0.1");
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.1"))
                .thenReturn(existingVersion);
        
        task.onApplicationEvent(createRootContextEvent());
        
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never()).insert(any(AiResource.class));
    }
    
    @Test
    void testShouldAcquireMarkerAndMigratePromptSuccessfully() throws Exception {
        // 1. Scan returns one descriptor dataId
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
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
        when(aiResourceVersionPersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT, "0.0.1")).thenReturn(null);
        
        // 6. readVersionContent uses configInfoPersistService.findConfigAllInfo, not configQueryChainService
        ConfigAllInfo versionConfigAllInfo = new ConfigAllInfo();
        versionConfigAllInfo.setContent(JacksonUtils.toJson(versionContent));
        when(configInfoPersistService.findConfigAllInfo(any(), eq(PROMPT_GROUP), eq(NS)))
                .thenReturn(versionConfigAllInfo);
        
        task.onApplicationEvent(createRootContextEvent());
        
        // Verify: meta record inserted
        verify(aiResourcePersistService, timeout(ASYNC_TIMEOUT)).insert(any(AiResource.class));
        // Verify: version record inserted
        verify(aiResourceVersionPersistService, timeout(ASYNC_TIMEOUT)).insert(any(AiResourceVersion.class));
        // Verify: content written to typed storage
        verify(storage, timeout(ASYNC_TIMEOUT)).save(any(StorageKey.class), any(byte[].class));
        // Verify: legacy mirror refreshed
        verify(promptOperationService, timeout(ASYNC_TIMEOUT)).refreshLatestMirror(NS, PROMPT_KEY);
        // Verify: marker released
        verify(configOperationService, timeout(ASYNC_TIMEOUT))
                .deleteConfig(eq("nacos.ai.prompt.migration"), eq("nacos_internal"), eq(NS), any(), any(),
                        eq("nacos"), any());
    }
    
    @Test
    void testShouldSkipWhenMarkerAcquireFails() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
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
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never()).insert(any(AiResource.class));
    }
    
    @Test
    void testMigrateOneVersionShouldSkipDbInsertWhenAlreadyExists() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
                .thenReturn(scanPage);
        // First call: ai_resource not found (needs migration); after insert: found
        when(aiResourcePersistService.find(NS, PROMPT_KEY, RESOURCE_TYPE_PROMPT))
                .thenReturn(null);
        
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = Collections.singletonList("0.0.1");
        mapping.latestVersion = "0.0.1";
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req = invocation.getArgument(0);
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
        verify(aiResourceVersionPersistService, after(ASYNC_TIMEOUT).never()).insert(any(AiResourceVersion.class));
    }
    
    @Test
    void testShouldReleaseMigrationMarkerEvenOnFailure() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
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
                .deleteConfig(eq("nacos.ai.prompt.migration"), eq("nacos_internal"), eq(NS), any(), any(),
                        eq("nacos"), any());
    }
    
    @Test
    void testShouldSkipPromptWhenMappingHasNoVersions() throws Exception {
        Page<ConfigInfo> scanPage = buildScanPage(PROMPT_KEY);
        when(configInfoPersistService.findConfigInfo4Page(eq(1), eq(100), any(), eq(PROMPT_GROUP), eq(NS), any()))
                .thenReturn(scanPage);
        
        // Mapping with no versions — buildLegacyPromptData returns null, so scan yields empty list
        LegacyLabelVersionMapping mapping = new LegacyLabelVersionMapping();
        mapping.promptKey = PROMPT_KEY;
        mapping.versions = new ArrayList<>();
        
        when(configQueryChainService.handle(any())).thenAnswer(invocation -> {
            com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest req = invocation.getArgument(0);
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
        verify(aiResourcePersistService, after(ASYNC_TIMEOUT).never()).insert(any(AiResource.class));
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
