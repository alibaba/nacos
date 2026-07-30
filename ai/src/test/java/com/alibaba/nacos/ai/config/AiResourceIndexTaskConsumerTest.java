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

import com.alibaba.nacos.ai.model.search.AiResourceIndexTask;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexTaskRepository;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiResourceIndexTaskConsumer}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiResourceIndexTaskConsumerTest {
    
    @Mock
    private AiResourceIndexTaskRepository taskRepository;
    
    @Mock
    private AiResourceIndexService indexBuildService;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    private AiResourceIndexTaskConsumer consumer;
    
    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.destroy();
        }
    }
    
    @Test
    void shouldAdvanceBaseTaskWhenEnhancementIsRequired() throws Exception {
        AiResourceIndexTask task = task();
        task.setEnhancementRequested(true);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(indexBuildService.rebuildLatestAiResource("public", "skill", "avatar"))
            .thenReturn(true);
        when(indexBuildService.isEnhancementRequested()).thenReturn(true);
        when(taskRepository.advanceToEnhancement(task)).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(indexBuildService).rebuildLatestAiResource("public", "skill", "avatar");
        verify(taskRepository).advanceToEnhancement(task);
        verify(taskRepository, never()).complete(eq(task), any());
        verify(taskRepository, never()).retry(eq(task), anyLong(), any());
    }
    
    @Test
    void shouldCompleteBaseCheckpointWhenEnhancementIsDisabled() throws Exception {
        AiResourceIndexTask task = task();
        task.setEnhancementRequested(true);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(indexBuildService.rebuildLatestAiResource("public", "skill", "avatar"))
            .thenReturn(true);
        when(taskRepository.complete(task, null)).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).complete(task, null);
        verify(taskRepository, never()).advanceToEnhancement(task);
    }
    
    @Test
    void shouldNotEnhanceReconciledHistoricalResource() throws Exception {
        AiResourceIndexTask task = task();
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(indexBuildService.rebuildLatestAiResource("public", "skill", "avatar"))
            .thenReturn(true);
        lenient().when(indexBuildService.isEnhancementRequested()).thenReturn(true);
        when(taskRepository.complete(task, null)).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).complete(task, null);
        verify(taskRepository, never()).advanceToEnhancement(task);
        verify(indexBuildService, never()).isEnhancementRequested();
    }
    
    @Test
    void shouldCompleteEnhancementCheckpointWithFingerprint() throws Exception {
        AiResourceIndexTask task = task();
        task.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(taskRepository.renewLease(eq(task), anyLong())).thenReturn(true);
        when(indexBuildService.isEnhancementRequested()).thenReturn(true);
        when(indexBuildService.enhanceLatestAiResource(eq("public"), eq("skill"), eq("avatar"),
            any(BooleanSupplier.class))).thenReturn("fingerprint-v1");
        when(taskRepository.complete(task, "fingerprint-v1")).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).complete(task, "fingerprint-v1");
        verify(taskRepository, never()).advanceToEnhancement(task);
    }
    
    @Test
    void shouldRetainTaskWithBackoffAfterIndexFailure() throws Exception {
        AiResourceIndexTask task = task();
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "vector unavailable"))
            .when(indexBuildService).rebuildLatestAiResource("public", "skill", "avatar");
        when(taskRepository.retry(task, 5_000L, "vector unavailable")).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).retry(task, 5_000L, "vector unavailable");
        verify(taskRepository, never()).complete(eq(task), any());
    }
    
    @Test
    void shouldRetainEnhancementTaskAfterLlmFailure() throws Exception {
        AiResourceIndexTask task = task();
        task.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        task.setRetryCount(13);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(taskRepository.renewLease(eq(task), anyLong())).thenReturn(true);
        when(indexBuildService.isEnhancementRequested()).thenReturn(true);
        doThrow(new IllegalStateException("llm unavailable")).when(indexBuildService)
            .enhanceLatestAiResource(eq("public"), eq("skill"), eq("avatar"),
                any(BooleanSupplier.class));
        when(taskRepository.retry(task, 1_800_000L, "llm unavailable")).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).retry(task, 1_800_000L, "llm unavailable");
        verify(taskRepository, never()).complete(eq(task), any());
    }
    
    @Test
    void shouldRequeueBaseStageWhenEnhancementIsDisabled() throws Exception {
        AiResourceIndexTask task = task();
        task.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        task.setRetryCount(1);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(taskRepository.renewLease(eq(task), anyLong())).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).restartFromBase(task, false);
        verify(indexBuildService, never()).enhanceLatestAiResource(eq("public"), eq("skill"),
            eq("avatar"), any(BooleanSupplier.class));
        verify(taskRepository, never()).complete(eq(task), any());
        verify(taskRepository, never()).retry(eq(task), anyLong(), any());
    }
    
    @Test
    void shouldRequeueBaseStageWhenEnhancementEntryIsStale() throws Exception {
        AiResourceIndexTask task = task();
        task.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(taskRepository.renewLease(eq(task), anyLong())).thenReturn(true);
        when(indexBuildService.isEnhancementRequested()).thenReturn(true);
        when(indexBuildService.enhanceLatestAiResource(eq("public"), eq("skill"), eq("avatar"),
            any(BooleanSupplier.class))).thenReturn(null);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(taskRepository).restartFromBase(task, true);
        verify(taskRepository, never()).remove(task);
        verify(taskRepository, never()).complete(eq(task), any());
    }
    
    @Test
    void shouldNotClaimMoreEnhancementTasksThanWorkerConcurrency() {
        AiResourceIndexTask first = task();
        first.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        AiResourceIndexTask second = task();
        second.setTaskKey("second-task");
        second.setResourceName("second");
        second.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        List<Runnable> queued = new ArrayList<>();
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(first, second));
        when(taskRepository.claim(eq(first), anyLong())).thenReturn(true);
        when(taskRepository.renewLease(eq(first), anyLong())).thenReturn(true);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, queued::add, 1);
        
        consumer.consume();
        
        verify(taskRepository).claim(eq(first), anyLong());
        verify(taskRepository, never()).claim(eq(second), anyLong());
        queued.get(0).run();
    }
    
    @Test
    void shouldStopEnhancementWhenClaimedRevisionLosesItsLease() throws Exception {
        AiResourceIndexTask task = task();
        task.setTaskStage(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT);
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        when(taskRepository.renewLease(eq(task), anyLong())).thenReturn(false);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(indexBuildService, never()).enhanceLatestAiResource(any(), any(), any(),
            any(BooleanSupplier.class));
        verify(taskRepository, never()).complete(eq(task), any());
        verify(taskRepository, never()).retry(eq(task), anyLong(), any());
    }
    
    @Test
    void shouldDeleteMcpIndexWhenCanonicalResourceIsGone() throws Exception {
        AiResourceIndexTask task = task();
        task.setResourceType("mcp");
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), anyLong())).thenReturn(true);
        doThrow(new NacosException(NacosException.NOT_FOUND, "not found"))
            .when(mcpServerOperationService).getMcpServerDetail("public", "avatar", null, null);
        consumer = new AiResourceIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService, Runnable::run);
        
        consumer.consume();
        
        verify(indexBuildService).deleteResource("public", "mcp", "avatar");
        verify(taskRepository).remove(task);
    }
    
    private AiResourceIndexTask task() {
        AiResourceIndexTask task = new AiResourceIndexTask();
        task.setTaskKey("task-key");
        task.setNamespaceId("public");
        task.setResourceType("skill");
        task.setResourceName("avatar");
        task.setTaskStage(AiResourceIndexTask.STAGE_BASE_INDEX);
        task.setRevision(1L);
        return task;
    }
}
