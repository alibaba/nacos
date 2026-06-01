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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.PipelineHandler;
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
class PipelineProxyTest {
    
    @Mock
    private PipelineHandler pipelineHandler;
    
    private PipelineProxy pipelineProxy;
    
    @BeforeEach
    void setUp() {
        pipelineProxy = new PipelineProxy(pipelineHandler);
    }
    
    @Test
    void testGetPipeline() throws NacosException {
        PipelineExecution execution = new PipelineExecution();
        when(pipelineHandler.getPipeline("pipe-123")).thenReturn(execution);
        
        PipelineExecution result = pipelineProxy.getPipeline("pipe-123");
        
        assertNotNull(result);
        verify(pipelineHandler).getPipeline("pipe-123");
    }
    
    @Test
    void testListPipelines() throws NacosException {
        Page<PipelineExecution> page = new Page<>();
        page.setTotalCount(5);
        when(pipelineHandler.listPipelines("prompt", "my-prompt", "public",
            "0.0.1", 1, 10)).thenReturn(page);
        
        Page<PipelineExecution> result = pipelineProxy.listPipelines("prompt",
            "my-prompt", "public", "0.0.1", 1, 10);
        
        assertEquals(5, result.getTotalCount());
        verify(pipelineHandler).listPipelines("prompt", "my-prompt", "public",
            "0.0.1", 1, 10);
    }
}
