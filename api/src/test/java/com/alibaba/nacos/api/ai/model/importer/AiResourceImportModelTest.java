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

package com.alibaba.nacos.api.ai.model.importer;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportModelTest extends BasicRequestTest {
    
    @Test
    void testSourceInfoSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportSourceInfo source = new AiResourceImportSourceInfo();
        source.setSourceId("official-mcp");
        source.setDisplayName("Official MCP Registry");
        source.setDescription("MCP registry maintained by operators");
        source.setPluginName("mcp-registry");
        source.setResourceTypes(Arrays.asList("mcp", "skill"));
        source.setEnabled(true);
        source.setCapabilities(Collections.singletonList("search"));
        
        String json = mapper.writeValueAsString(source);
        AiResourceImportSourceInfo result =
            mapper.readValue(json, AiResourceImportSourceInfo.class);
        
        assertTrue(json.contains("\"sourceId\":\"official-mcp\""));
        assertNotNull(result);
        assertEquals("official-mcp", result.getSourceId());
        assertEquals("Official MCP Registry", result.getDisplayName());
        assertEquals("mcp-registry", result.getPluginName());
        assertEquals(Arrays.asList("mcp", "skill"), result.getResourceTypes());
        assertTrue(result.isEnabled());
        assertEquals(Collections.singletonList("search"), result.getCapabilities());
    }
    
    @Test
    void testSearchRequestSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportSearchRequest request = new AiResourceImportSearchRequest();
        request.setNamespaceId("public");
        request.setResourceType("mcp");
        request.setSourceId("official-mcp");
        request.setQuery("database");
        request.setCursor("cursor-1");
        request.setLimit(20);
        request.setOptions(Collections.singletonMap("sort", "updated"));
        
        String json = mapper.writeValueAsString(request);
        AiResourceImportSearchRequest result =
            mapper.readValue(json, AiResourceImportSearchRequest.class);
        
        assertTrue(json.contains("\"resourceType\":\"mcp\""));
        assertNotNull(result);
        assertEquals("public", result.getNamespaceId());
        assertEquals("mcp", result.getResourceType());
        assertEquals("official-mcp", result.getSourceId());
        assertEquals("database", result.getQuery());
        assertEquals("cursor-1", result.getCursor());
        assertEquals(Integer.valueOf(20), result.getLimit());
        assertEquals("updated", result.getOptions().get("sort"));
    }
    
    @Test
    void testSearchResponseSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportCandidateItem candidate = new AiResourceImportCandidateItem();
        candidate.setExternalId("server-1");
        candidate.setName("database-server");
        candidate.setVersion("1.0.0");
        candidate.setDescription("Database MCP server");
        candidate.setMetadata(Collections.singletonMap("official", "true"));
        AiResourceImportSearchResponse response = new AiResourceImportSearchResponse();
        response.setSourceId("official-mcp");
        response.setResourceType("mcp");
        response.setNextCursor("cursor-2");
        response.setHasMore(true);
        response.setItems(Collections.singletonList(candidate));
        
        String json = mapper.writeValueAsString(response);
        AiResourceImportSearchResponse result =
            mapper.readValue(json, AiResourceImportSearchResponse.class);
        
        assertTrue(json.contains("\"externalId\":\"server-1\""));
        assertFalse(json.contains("dependencies"));
        assertNotNull(result);
        assertEquals("official-mcp", result.getSourceId());
        assertEquals("mcp", result.getResourceType());
        assertEquals("cursor-2", result.getNextCursor());
        assertTrue(result.isHasMore());
        assertEquals(1, result.getItems().size());
        assertEquals("database-server", result.getItems().get(0).getName());
        assertEquals("true", result.getItems().get(0).getMetadata().get("official"));
    }
    
    @Test
    void testValidateRequestSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportValidateRequest request = new AiResourceImportValidateRequest();
        request.setNamespaceId("public");
        request.setResourceType("mcp");
        request.setSourceId("official-mcp");
        request.setSelectedItems(Collections.singletonList(createSelectedItem()));
        request.setOverwriteExisting(true);
        request.setOptions(Collections.singletonMap("dryRun", "true"));
        
        String json = mapper.writeValueAsString(request);
        AiResourceImportValidateRequest result =
            mapper.readValue(json, AiResourceImportValidateRequest.class);
        
        assertTrue(json.contains("\"overwriteExisting\":true"));
        assertFalse(json.contains("dependencyPolicy"));
        assertNotNull(result);
        assertEquals("public", result.getNamespaceId());
        assertEquals("mcp", result.getResourceType());
        assertEquals("official-mcp", result.getSourceId());
        assertTrue(result.isOverwriteExisting());
        assertEquals(1, result.getSelectedItems().size());
        assertEquals("server-1", result.getSelectedItems().get(0).getExternalId());
        assertEquals("true", result.getOptions().get("dryRun"));
    }
    
    @Test
    void testValidateResponseSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportValidationItem item = new AiResourceImportValidationItem();
        item.setExternalId("server-1");
        item.setName("database-server");
        item.setVersion("1.0.0");
        item.setStatus(AiResourceImportValidationStatus.CONFLICT);
        item.setExists(true);
        item.setConflictType("duplicate_name");
        item.setWarnings(Collections.singletonList("existing server"));
        item.setErrors(Collections.singletonList("name already exists"));
        AiResourceImportValidateResponse response = new AiResourceImportValidateResponse();
        response.setSourceId("official-mcp");
        response.setResourceType("mcp");
        response.setValidationToken("token-1");
        response.setItems(Collections.singletonList(item));
        
        String json = mapper.writeValueAsString(response);
        AiResourceImportValidateResponse result =
            mapper.readValue(json, AiResourceImportValidateResponse.class);
        
        assertTrue(json.contains("\"status\":\"CONFLICT\""));
        assertFalse(json.contains("dependencies"));
        assertNotNull(result);
        assertEquals("token-1", result.getValidationToken());
        assertEquals(1, result.getItems().size());
        assertEquals(AiResourceImportValidationStatus.CONFLICT,
            result.getItems().get(0).getStatus());
        assertTrue(result.getItems().get(0).isExists());
        assertEquals("duplicate_name", result.getItems().get(0).getConflictType());
        assertEquals("existing server", result.getItems().get(0).getWarnings().get(0));
        assertEquals("name already exists", result.getItems().get(0).getErrors().get(0));
    }
    
    @Test
    void testExecuteRequestSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportExecuteRequest request = new AiResourceImportExecuteRequest();
        request.setNamespaceId("public");
        request.setResourceType("mcp");
        request.setSourceId("official-mcp");
        request.setSelectedItems(Collections.singletonList(createSelectedItem()));
        request.setOverwriteExisting(true);
        request.setSkipInvalid(true);
        request.setValidationToken("token-1");
        request.setOptions(Collections.singletonMap("mode", "selected"));
        
        String json = mapper.writeValueAsString(request);
        AiResourceImportExecuteRequest result =
            mapper.readValue(json, AiResourceImportExecuteRequest.class);
        
        assertTrue(json.contains("\"skipInvalid\":true"));
        assertFalse(json.contains("dependencyPolicy"));
        assertNotNull(result);
        assertEquals("public", result.getNamespaceId());
        assertEquals("mcp", result.getResourceType());
        assertEquals("official-mcp", result.getSourceId());
        assertTrue(result.isOverwriteExisting());
        assertTrue(result.isSkipInvalid());
        assertEquals("token-1", result.getValidationToken());
        assertEquals("selected", result.getOptions().get("mode"));
        assertEquals("server-1", result.getSelectedItems().get(0).getExternalId());
    }
    
    @Test
    void testExecuteResponseSerializeAndDeserialize() throws JsonProcessingException {
        AiResourceImportResultItem resultItem = new AiResourceImportResultItem();
        resultItem.setExternalId("server-1");
        resultItem.setResourceName("database-server");
        resultItem.setVersion("1.0.0");
        resultItem.setStatus(AiResourceImportResultStatus.SUCCESS);
        resultItem.setWarnings(Collections.singletonList("imported as latest"));
        AiResourceImportExecuteResponse response = new AiResourceImportExecuteResponse();
        response.setSuccess(true);
        response.setTotalCount(1);
        response.setSuccessCount(1);
        response.setFailedCount(0);
        response.setSkippedCount(0);
        response.setResults(Collections.singletonList(resultItem));
        
        String json = mapper.writeValueAsString(response);
        AiResourceImportExecuteResponse result =
            mapper.readValue(json, AiResourceImportExecuteResponse.class);
        
        assertTrue(json.contains("\"status\":\"SUCCESS\""));
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(AiResourceImportResultStatus.SUCCESS,
            result.getResults().get(0).getStatus());
        assertEquals("imported as latest", result.getResults().get(0).getWarnings().get(0));
    }
    
    private AiResourceImportItem createSelectedItem() {
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId("server-1");
        item.setName("database-server");
        item.setVersion("1.0.0");
        item.setMetadata(Collections.singletonMap("package", "npm"));
        return item;
    }
}
