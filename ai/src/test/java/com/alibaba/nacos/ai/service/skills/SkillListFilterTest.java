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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityPluginManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for owner/scope filter parameters in {@link SkillOperationServiceImpl#listSkills}.
 */
@ExtendWith(MockitoExtension.class)
class SkillListFilterTest {
    
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
    
    @Mock
    private SkillIndexManifestService manifestService;
    
    private SkillOperationServiceImpl skillOperationService;
    
    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT =
        EnvUtil.getEnvironment();
    
    private MockedStatic<VisibilityPluginManager> visibilityManagerStatic;
    
    private VisibilityPluginManager mockVisibilityManager;
    
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
        skillOperationService = new SkillOperationServiceImpl(aiResourcePersistService,
            aiResourceVersionPersistService, publishPipelineExecutor,
            manifestService,
            new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                pipelineExecutionRepository));
        mockVisibilityManager = mock(VisibilityPluginManager.class);
        lenient().when(mockVisibilityManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        visibilityManagerStatic = org.mockito.Mockito.mockStatic(VisibilityPluginManager.class);
        visibilityManagerStatic.when(VisibilityPluginManager::getInstance)
            .thenReturn(mockVisibilityManager);
    }
    
    @AfterEach
    void tearDown() {
        if (visibilityManagerStatic != null) {
            visibilityManagerStatic.close();
        }
        AiResourceStorageRouter.reset();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    private Page<AiResource> emptyPage() {
        Page<AiResource> page = new Page<>();
        page.setPageItems(List.of());
        page.setTotalCount(0);
        page.setPagesAvailable(0);
        return page;
    }
    
    @Test
    void listSkillsWithOwnerFilterShouldSetOwnerOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, "alice", null, 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertEquals("alice", captor.getValue().getOwner());
    }
    
    @Test
    void listSkillsWithScopePublicShouldSetScopeOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, null, "PUBLIC", 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertEquals("PUBLIC", captor.getValue().getScope());
    }
    
    @Test
    void listSkillsWithScopePrivateShouldSetScopeOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, null, "PRIVATE", 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertEquals("PRIVATE", captor.getValue().getScope());
    }
    
    @Test
    void listSkillsWithOwnerAndScopeShouldSetBothOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, "download_count", "bob", "PRIVATE",
            1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        QueryCondition condition = captor.getValue();
        assertEquals("bob", condition.getOwner());
        assertEquals("PRIVATE", condition.getScope());
        assertEquals("download_count", condition.getOrderBy());
    }
    
    @Test
    void listSkillsWithNullOwnerShouldNotSetOwnerOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, null, null, 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertNull(captor.getValue().getOwner());
    }
    
    @Test
    void listSkillsWithNullScopeShouldNotSetScopeOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, null, null, 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertNull(captor.getValue().getScope());
    }
    
    @Test
    void listSkillsWithEmptyOwnerShouldNotSetOwnerOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, "", "PUBLIC", 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertNull(captor.getValue().getOwner());
    }
    
    @Test
    void listSkillsWithEmptyScopeShouldNotSetScopeOnQueryCondition() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        skillOperationService.listSkills("public", null, null, null, "alice", "", 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertNull(captor.getValue().getScope());
    }
    
    @Test
    void listSkillsViaLegacy5ParamDelegatesWithNullFilters() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        Page<SkillSummary> result = skillOperationService.listSkills("public", null, null, 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertNotNull(result);
        assertNull(captor.getValue().getOwner());
        assertNull(captor.getValue().getScope());
        assertNull(captor.getValue().getOrderBy());
    }
    
    @Test
    void listSkillsViaLegacy6ParamDelegatesWithNullFilters() throws NacosException {
        when(aiResourcePersistService.list(any(QueryCondition.class), anyInt(), anyInt()))
            .thenReturn(emptyPage());
        
        Page<SkillSummary> result =
            skillOperationService.listSkills("public", null, null, "download_count", 1, 10);
        
        ArgumentCaptor<QueryCondition> captor = ArgumentCaptor.forClass(QueryCondition.class);
        verify(aiResourcePersistService).list(captor.capture(), anyInt(), anyInt());
        assertNotNull(result);
        assertNull(captor.getValue().getOwner());
        assertNull(captor.getValue().getScope());
        assertEquals("download_count", captor.getValue().getOrderBy());
    }
}
