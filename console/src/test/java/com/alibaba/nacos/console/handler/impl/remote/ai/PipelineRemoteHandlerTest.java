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

import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecution;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.PipelineMaintainerService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(pipelineMaintainerService.getPipelineDetail("pipe-123"))
            .thenReturn(Result.success(execution));
        
        PipelineExecution result = handler.getPipeline("pipe-123");
        
        assertNotNull(result);
        assertEquals("pipe-123", result.getExecutionId());
        verify(pipelineMaintainerService).getPipelineDetail("pipe-123");
    }
    
    @Test
    void testListPipelines() throws NacosException {
        Page<PipelineExecution> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(Collections.emptyList());
        when(pipelineMaintainerService.listPipelineExecutions("prompt", "my-prompt",
            "public", "0.0.1", 1, 10)).thenReturn(Result.success(page));
        
        Page<PipelineExecution> result = handler.listPipelines("prompt", "my-prompt",
            "public", "0.0.1", 1, 10);
        
        assertNotNull(result);
        assertEquals(0, result.getTotalCount());
        verify(pipelineMaintainerService).listPipelineExecutions("prompt", "my-prompt",
            "public", "0.0.1", 1, 10);
    }
    
    @Test
    void testGetPipelineWithHttp200ResultCode() throws NacosException {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("pipe-200");
        when(pipelineMaintainerService.getPipelineDetail("pipe-200"))
            .thenReturn(new Result<>(200, "success", execution));
        
        PipelineExecution result = handler.getPipeline("pipe-200");
        
        assertNotNull(result);
        assertEquals("pipe-200", result.getExecutionId());
    }
    
    @Test
    void testGetPipelineThrowsOnNullResult() throws NacosException {
        when(pipelineMaintainerService.getPipelineDetail("pipe-null")).thenReturn(null);
        
        NacosException ex =
            assertThrows(NacosException.class, () -> handler.getPipeline("pipe-null"));
        
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertEquals("empty Result", ex.getErrMsg());
    }
    
    @Test
    void testGetPipelineThrowsOnFailedResult() throws NacosException {
        when(pipelineMaintainerService.getPipelineDetail("pipe-failed"))
            .thenReturn(Result.failure(ErrorCode.DATA_ACCESS_ERROR));
        
        NacosException ex =
            assertThrows(NacosException.class, () -> handler.getPipeline("pipe-failed"));
        
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertEquals(ErrorCode.DATA_ACCESS_ERROR.getMsg(), ex.getErrMsg());
    }
}
