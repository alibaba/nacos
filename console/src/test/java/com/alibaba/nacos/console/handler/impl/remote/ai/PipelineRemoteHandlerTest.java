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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.PipelineMaintainerService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineRemoteHandlerTest {
    
    @Mock
    private NacosMaintainerClientHolder clientHolder;
    
    @Mock
    private AiMaintainerService aiMaintainerService;
    
    @Mock
    private PipelineMaintainerService pipelineMaintainerService;
    
    private PipelineRemoteHandler handler;
    
    @BeforeEach
    void setUp() {
        when(clientHolder.getAiMaintainerService()).thenReturn(aiMaintainerService);
        when(aiMaintainerService.pipeline()).thenReturn(pipelineMaintainerService);
        handler = new PipelineRemoteHandler(clientHolder);
    }
    
    @Test
    void testGetPipeline() throws NacosException {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("pipe-123");
        JsonNode jsonNode = JacksonUtils.toObj(JacksonUtils.toJson(execution),
            JsonNode.class);
        when(pipelineMaintainerService.getPipeline("pipe-123")).thenReturn(jsonNode);
        
        PipelineExecution result = handler.getPipeline("pipe-123");
        
        assertNotNull(result);
        assertEquals("pipe-123", result.getExecutionId());
        verify(pipelineMaintainerService).getPipeline("pipe-123");
    }
    
    @Test
    void testListPipelines() throws NacosException {
        Page<PipelineExecution> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(Collections.emptyList());
        JsonNode jsonNode = JacksonUtils.toObj(JacksonUtils.toJson(page), JsonNode.class);
        when(pipelineMaintainerService.listPipelines("prompt", "my-prompt",
            "public", "0.0.1", 1, 10)).thenReturn(jsonNode);
        
        Page<PipelineExecution> result = handler.listPipelines("prompt", "my-prompt",
            "public", "0.0.1", 1, 10);
        
        assertNotNull(result);
        assertEquals(0, result.getTotalCount());
        verify(pipelineMaintainerService).listPipelines("prompt", "my-prompt",
            "public", "0.0.1", 1, 10);
    }
}
