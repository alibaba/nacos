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

import com.alibaba.nacos.ai.model.ard.ArdIndexTask;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.ard.ArdIndexBuildService;
import com.alibaba.nacos.ai.service.ard.ArdIndexTaskRepository;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdIndexTaskConsumer}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdIndexTaskConsumerTest {
    
    @Mock
    private ArdIndexTaskRepository taskRepository;
    
    @Mock
    private ArdIndexBuildService indexBuildService;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    private ArdIndexTaskConsumer consumer;
    
    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.destroy();
        }
    }
    
    @Test
    void shouldCompleteTaskAfterBothIndexesConverge() throws Exception {
        ArdIndexTask task = task();
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), any(Timestamp.class))).thenReturn(true);
        consumer = new ArdIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService);
        
        consumer.consume();
        
        verify(indexBuildService).rebuildLatestAiResource("public", "skill", "avatar");
        verify(taskRepository).complete(task);
        verify(taskRepository, never()).retry(eq(task), any(), any());
    }
    
    @Test
    void shouldRetainTaskWithBackoffAfterIndexFailure() throws Exception {
        ArdIndexTask task = task();
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), any(Timestamp.class))).thenReturn(true);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "vector unavailable"))
            .when(indexBuildService).rebuildLatestAiResource("public", "skill", "avatar");
        consumer = new ArdIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService);
        
        consumer.consume();
        
        verify(taskRepository).retry(eq(task), any(Timestamp.class),
            eq("vector unavailable"));
        verify(taskRepository, never()).complete(task);
    }
    
    @Test
    void shouldDeleteMcpIndexWhenCanonicalResourceIsGone() throws Exception {
        ArdIndexTask task = task();
        task.setResourceType("mcp");
        when(taskRepository.findDueTasks(100)).thenReturn(List.of(task));
        when(taskRepository.claim(eq(task), any(Timestamp.class))).thenReturn(true);
        doThrow(new NacosException(NacosException.NOT_FOUND, "not found"))
            .when(mcpServerOperationService).getMcpServerDetail("public", "avatar", null, null);
        consumer = new ArdIndexTaskConsumer(taskRepository, indexBuildService,
            mcpServerOperationService);
        
        consumer.consume();
        
        verify(indexBuildService).deleteResource("public", "mcp", "avatar");
        verify(taskRepository).complete(task);
    }
    
    private ArdIndexTask task() {
        ArdIndexTask task = new ArdIndexTask();
        task.setTaskKey("task-key");
        task.setNamespaceId("public");
        task.setResourceType("skill");
        task.setResourceName("avatar");
        task.setRevision(1L);
        return task;
    }
}
