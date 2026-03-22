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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSpecOperationServiceImplTest {

    @Mock
    private AiResourceStorage storage;

    @Mock
    private AiResourcePersistService aiResourcePersistService;

    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;

    @Mock
    private PipelineConfigProvider pipelineConfigProvider;

    @Mock
    private PipelineExecutionRepository pipelineExecutionRepository;

    private AgentSpecOperationServiceImpl service;

    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();

    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        AiResourceStorageRouter.reset();
        lenient().when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        PipelineConfig disabledConfig = new PipelineConfig();
        disabledConfig.setEnabled(false);
        lenient().when(pipelineConfigProvider.getConfig()).thenReturn(disabledConfig);
        PublishPipelineExecutor publishPipelineExecutor = new PublishPipelineExecutor(
                new PublishPipelineManager(), pipelineConfigProvider, pipelineExecutionRepository,
                Executors.newSingleThreadExecutor());
        service = new AgentSpecOperationServiceImpl(aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, pipelineExecutionRepository);
    }

    @AfterEach
    void tearDown() {
        AiResourceStorageRouter.reset();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }

    @Test
    void createDraftShouldCreateV1ForBrandNewAgentSpec() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "brand-new-agentspec";

        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString())).thenReturn(null);

        String version = service.createDraft(namespaceId, agentSpecName, null);

        assertEquals("v1", version);

        ArgumentCaptor<AiResourceVersion> versionCaptor = ArgumentCaptor.forClass(AiResourceVersion.class);
        verify(aiResourceVersionPersistService).insert(versionCaptor.capture());
        AiResourceVersion insertedVersion = versionCaptor.getValue();
        assertEquals(agentSpecName, insertedVersion.getName());
        assertEquals("v1", insertedVersion.getVersion());
        assertEquals("draft", insertedVersion.getStatus());

        ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
        verify(aiResourcePersistService).insert(metaCaptor.capture());
        AiResource insertedMeta = metaCaptor.getValue();
        assertEquals(agentSpecName, insertedMeta.getName());
        assertEquals("enable", insertedMeta.getStatus());
        Map<?, ?> versionInfo = JacksonUtils.toObj(insertedMeta.getVersionInfo(), Map.class);
        assertEquals("v1", versionInfo.get("editingVersion"));
        assertEquals(0, ((Number) versionInfo.get("onlineCnt")).intValue());

        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
    }

    @Test
    void createDraftShouldRejectBasedOnVersionForBrandNewAgentSpec() {
        String namespaceId = "public";
        String agentSpecName = "brand-new-agentspec";

        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString())).thenReturn(null);

        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> service.createDraft(namespaceId, agentSpecName, "v7"));

        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertTrue(exception.getErrMsg().contains("cannot use basedOnVersion for a brand-new agentspec"));
    }

    @Test
    void createDraftShouldCreateEmptyDraftWhenMetaExistsWithoutAnyBaseVersion() throws NacosException {
        String namespaceId = "public";
        String agentSpecName = "existing-agentspec";
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(agentSpecName);
        meta.setType("agentspec");
        meta.setStatus("enable");
        meta.setVersionInfo("{\"labels\":{},\"onlineCnt\":0}");
        meta.setMetaVersion(1L);

        com.alibaba.nacos.api.model.Page<AiResourceVersion> emptyPage = new com.alibaba.nacos.api.model.Page<>();
        when(aiResourcePersistService.find(eq(namespaceId), eq(agentSpecName), anyString())).thenReturn(meta);
        when(aiResourceVersionPersistService.listAll(eq(namespaceId), eq(agentSpecName), anyInt(), anyInt()))
                .thenReturn(emptyPage);
        when(aiResourcePersistService.updateMetaCas(eq(namespaceId), eq(agentSpecName), anyString(), eq(1L), any()))
                .thenReturn(true);

        String version = service.createDraft(namespaceId, agentSpecName, null);

        assertEquals("v1", version);
        verify(aiResourceVersionPersistService).insert(any(AiResourceVersion.class));
        verify(storage, times(1)).save(any(StorageKey.class), any(byte[].class));
    }
}