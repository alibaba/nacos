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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.importer.manager.AiResourceImportManager;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportCandidateItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
class AiResourceImportAdminControllerTest {
    
    @Mock
    private AiResourceImportManager importManager;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new AiResourceImportAdminController(importManager)).build();
    }
    
    @Test
    void testListSources() throws Exception {
        AiResourceImportSourceInfo source = new AiResourceImportSourceInfo();
        source.setSourceId("source-1");
        when(importManager.listSources("mcp")).thenReturn(Collections.singletonList(source));
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.get(Constants.AI_RESOURCE_IMPORT_ADMIN_PATH + "/sources")
                .param("resourceType", "mcp"))
            .andReturn().getResponse();
        
        Result<List<AiResourceImportSourceInfo>> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("source-1", result.getData().get(0).getSourceId());
    }
    
    @Test
    void testSearch() throws Exception {
        AiResourceImportSearchResponse searchResponse = new AiResourceImportSearchResponse();
        AiResourceImportCandidateItem candidate = new AiResourceImportCandidateItem();
        candidate.setExternalId("server-1");
        searchResponse.setItems(Collections.singletonList(candidate));
        when(importManager.search(any(AiResourceImportSearchRequest.class))).thenReturn(
            searchResponse);
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.AI_RESOURCE_IMPORT_ADMIN_PATH + "/search")
                .param("resourceType", "mcp")
                .param("sourceId", "source-1")
                .param("query", "database")
                .param("limit", "10")
                .param("options", "{\"stage\":\"dev\"}"))
            .andReturn().getResponse();
        
        Result<AiResourceImportSearchResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("server-1", result.getData().getItems().get(0).getExternalId());
        verify(importManager).search(argThat(request -> "mcp".equals(request.getResourceType())
            && "source-1".equals(request.getSourceId())
            && "database".equals(request.getQuery()) && request.getLimit() == 10
            && "dev".equals(request.getOptions().get("stage"))));
    }
    
    @Test
    void testValidate() throws Exception {
        when(importManager.validate(any())).thenReturn(new AiResourceImportValidateResponse());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.AI_RESOURCE_IMPORT_ADMIN_PATH + "/validate")
                .param("resourceType", "mcp")
                .param("sourceId", "source-1")
                .param("selectedItems", selectedItemsJson())
                .param("overwriteExisting", "true"))
            .andReturn().getResponse();
        
        Result<AiResourceImportValidateResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(importManager).validate(argThat(request -> "mcp".equals(request.getResourceType())
            && "source-1".equals(request.getSourceId()) && request.isOverwriteExisting()
            && "server-1".equals(request.getSelectedItems().get(0).getExternalId())));
    }
    
    @Test
    void testExecute() throws Exception {
        when(importManager.execute(any())).thenReturn(new AiResourceImportExecuteResponse());
        
        MockHttpServletResponse response = mockMvc.perform(
            MockMvcRequestBuilders.post(Constants.AI_RESOURCE_IMPORT_ADMIN_PATH + "/execute")
                .param("resourceType", "mcp")
                .param("sourceId", "source-1")
                .param("selectedItems", selectedItemsJson())
                .param("skipInvalid", "true"))
            .andReturn().getResponse();
        
        Result<AiResourceImportExecuteResponse> result = JacksonUtils.toObj(
            response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(importManager).execute(argThat(request -> "mcp".equals(request.getResourceType())
            && "source-1".equals(request.getSourceId()) && request.isSkipInvalid()
            && "server-1".equals(request.getSelectedItems().get(0).getExternalId())));
    }
    
    private String selectedItemsJson() {
        return "[{\"externalId\":\"server-1\",\"name\":\"server\",\"version\":\"1.0.0\"}]";
    }
}
