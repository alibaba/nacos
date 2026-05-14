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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.service.pipeline.PipelineQueryService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineInnerHandlerTest {
    
    @Mock
    private PipelineQueryService pipelineQueryService;
    
    private PipelineInnerHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new PipelineInnerHandler(pipelineQueryService);
    }
    
    @Test
    void testGetPipeline() throws NacosException {
        PipelineExecution execution = new PipelineExecution();
        when(pipelineQueryService.getPipeline("pipe-123")).thenReturn(execution);
        
        PipelineExecution result = handler.getPipeline("pipe-123");
        
        assertNotNull(result);
        verify(pipelineQueryService).getPipeline("pipe-123");
    }
    
    @Test
    void testListPipelines() throws NacosException {
        Page<PipelineExecution> page = new Page<>();
        page.setTotalCount(3);
        when(pipelineQueryService.listPipelines("prompt", "my-prompt", "public",
            "0.0.1", 1, 10)).thenReturn(page);
        
        Page<PipelineExecution> result = handler.listPipelines("prompt", "my-prompt",
            "public", "0.0.1", 1, 10);
        
        assertEquals(3, result.getTotalCount());
        verify(pipelineQueryService).listPipelines("prompt", "my-prompt", "public",
            "0.0.1", 1, 10);
    }
}
