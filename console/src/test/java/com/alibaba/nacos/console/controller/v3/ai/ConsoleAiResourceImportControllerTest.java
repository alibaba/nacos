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
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.AiResourceImportProxy;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleAiResourceImportControllerTest {
    
    @Mock
    private AiResourceImportProxy importProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new ConsoleAiResourceImportController(importProxy)).build();
    }
    
    @Test
    void testListSources() throws Exception {
        AiResourceImportSourceInfo source = new AiResourceImportSourceInfo();
        source.setSourceId("source-1");
        when(importProxy.listSources("skill")).thenReturn(Collections.singletonList(source));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(Constants.AI_RESOURCE_IMPORT_CONSOLE_PATH + "/sources")
                .param("resourceType", "skill"))
            .andReturn().getResponse();
        
        Result<List<AiResourceImportSourceInfo>> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("source-1", result.getData().get(0).getSourceId());
        verify(importProxy).listSources("skill");
    }
    
    @Test
    void testSearch() throws Exception {
        when(importProxy.search(any(AiResourceImportSearchRequest.class))).thenReturn(
            new AiResourceImportSearchResponse());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.AI_RESOURCE_IMPORT_CONSOLE_PATH + "/search")
                .param("resourceType", "mcp")
                .param("sourceId", "source-1")
                .param("query", "database")
                .param("options", "{\"stage\":\"dev\"}"))
            .andReturn().getResponse();
        
        Result<AiResourceImportSearchResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(importProxy).search(argThat(request -> "mcp".equals(request.getResourceType())
            && "source-1".equals(request.getSourceId())
            && "database".equals(request.getQuery())
            && "dev".equals(request.getOptions().get("stage"))));
    }
    
    @Test
    void testValidate() throws Exception {
        when(importProxy.validate(any())).thenReturn(new AiResourceImportValidateResponse());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.AI_RESOURCE_IMPORT_CONSOLE_PATH + "/validate")
                .param("resourceType", "mcp")
                .param("sourceId", "source-1")
                .param("selectedItems", selectedItemsJson()))
            .andReturn().getResponse();
        
        Result<AiResourceImportValidateResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(importProxy).validate(argThat(request -> "mcp".equals(request.getResourceType())
            && "source-1".equals(request.getSourceId())
            && "server-1".equals(request.getSelectedItems().get(0).getExternalId())));
    }
    
    @Test
    void testExecute() throws Exception {
        when(importProxy.execute(any())).thenReturn(new AiResourceImportExecuteResponse());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.AI_RESOURCE_IMPORT_CONSOLE_PATH + "/execute")
                .param("resourceType", "mcp")
                .param("sourceId", "source-1")
                .param("selectedItems", selectedItemsJson())
                .param("overwriteExisting", "true"))
            .andReturn().getResponse();
        
        Result<AiResourceImportExecuteResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(importProxy).execute(argThat(request -> "mcp".equals(request.getResourceType())
            && "source-1".equals(request.getSourceId()) && request.isOverwriteExisting()
            && "server-1".equals(request.getSelectedItems().get(0).getExternalId())));
    }
    
    private String selectedItemsJson() {
        return "[{\"externalId\":\"server-1\",\"name\":\"server\",\"version\":\"1.0.0\"}]";
    }
}
