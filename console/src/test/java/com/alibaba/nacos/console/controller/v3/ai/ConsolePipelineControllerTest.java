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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.PipelineProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConsolePipelineController} including {@code /list} and {@code /detail} endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ConsolePipelineControllerTest {
    
    private static final String CONSOLE_BASE = Constants.Pipeline.CONSOLE_PATH;
    
    @Mock
    private PipelineProxy pipelineProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        mockMvc =
            MockMvcBuilders.standaloneSetup(new ConsolePipelineController(pipelineProxy)).build();
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listPipelinesAtListSubpath() throws Exception {
        Page<PipelineExecution> page = new Page<>();
        when(pipelineProxy.listPipelines(anyString(), isNull(), isNull(), isNull(), anyInt(),
            anyInt()))
            .thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(CONSOLE_BASE + Constants.Pipeline.LIST_SUBPATH)
                .param("resourceType", "SKILL").param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<Page<PipelineExecution>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void getPipelineAtDetailSubpath() throws Exception {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("exec-1");
        when(pipelineProxy.getPipeline("exec-1")).thenReturn(execution);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(CONSOLE_BASE + Constants.Pipeline.DETAIL_SUBPATH)
                .param("pipelineId", "exec-1");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<PipelineExecution> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("exec-1", result.getData().getExecutionId());
    }
    
    @Test
    void listPipelinesLegacyBasePathStillWorks() throws Exception {
        Page<PipelineExecution> page = new Page<>();
        when(pipelineProxy.listPipelines(anyString(), isNull(), isNull(), isNull(), anyInt(),
            anyInt()))
            .thenReturn(page);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(CONSOLE_BASE).param("resourceType", "SKILL")
                .param("pageNo", "1").param("pageSize", "10");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<Page<PipelineExecution>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }
    
    @Test
    void getPipelineLegacyPathVariableStillWorks() throws Exception {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("legacy-id");
        when(pipelineProxy.getPipeline("legacy-id")).thenReturn(execution);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(CONSOLE_BASE + "/legacy-id");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<PipelineExecution> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("legacy-id", result.getData().getExecutionId());
    }
}
