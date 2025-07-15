/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolSpecificationTest extends BasicRequestTest {
    
    private static final String MCP_TOOL_SPEC =
            "{\"tools\":[{\"name\":\"testTool\",\"description\":\"test tool description\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\",\"description\":\"aaa\"}}}}],"
                    + "\"toolsMeta\":{\"testTool\":{\"invokeContext\":{\"path\":\"/xxx\",\"method\":\"GET\"},\"enabled\":true,"
                    + "\"templates\":{\"json-go-tamplate\":{\"templateType\":\"string\",\"requestTemplate\":{\"url\":\"\",\"method\":\"GET\","
                    + "\"headers\":[],\"argsToJsonBody\":false,\"argsToUrlParam\":true,\"argsToFormBody\":true,\"body\":\"string\"},"
                    + "\"responseTemplate\":{\"body\":\"string\"}}}}}}";
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpToolSpecification toolSpecification = new McpToolSpecification();
        McpTool mcpTool = new McpTool();
        toolSpecification.setTools(Collections.singletonList(mcpTool));
        mcpTool.setName("testTool");
        mcpTool.setDescription("test tool description");
        
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        inputSchema.put("properties", properties);
        Map<String, String> aSchema = new HashMap<>();
        properties.put("a", aSchema);
        aSchema.put("type", "string");
        aSchema.put("description", "aaa");
        mcpTool.setInputSchema(inputSchema);
        
        McpToolMeta mcpToolMeta = new McpToolMeta();
        Map<String, Object> templates = new HashMap<>();
        mcpToolMeta.setTemplates(templates);
        Map<String, Object> jsonGoTemplate = new HashMap<>();
        templates.put("json-go-tamplate", jsonGoTemplate);
        jsonGoTemplate.put("templateType", "string");
        Map<String, Object> requestTemplate = new HashMap<>();
        jsonGoTemplate.put("requestTemplate", requestTemplate);
        requestTemplate.put("url", "");
        requestTemplate.put("method", "GET");
        requestTemplate.put("headers", Collections.emptyList());
        requestTemplate.put("argsToJsonBody", false);
        requestTemplate.put("argsToUrlParam", true);
        requestTemplate.put("argsToFormBody", true);
        requestTemplate.put("body", "string");
        Map<String, Object> responseTemplate = new HashMap<>();
        jsonGoTemplate.put("responseTemplate", responseTemplate);
        responseTemplate.put("body", "string");
        
        Map<String, String> invokeContext = new HashMap<>();
        invokeContext.put("path", "/xxx");
        invokeContext.put("method", "GET");
        mcpToolMeta.setInvokeContext(invokeContext);
        toolSpecification.setToolsMeta(Collections.singletonMap("testTool", mcpToolMeta));
        
        String json = mapper.writeValueAsString(toolSpecification);
        assertNotNull(json);
        assertTrue(json.contains("\"tools\":[{"));
        assertTrue(json.contains("\"name\":\"testTool\""));
        assertTrue(json.contains("\"description\":\"test tool description\""));
        assertTrue(json.contains("\"inputSchema\":{"));
        assertTrue(json.contains("{\"type\":\"object\""));
        assertTrue(json.contains("\"properties\":{\"a\":{"));
        assertTrue(json.contains("\"toolsMeta\":{\"testTool\":{"));
        assertTrue(json.contains("\"invokeContext\":{"));
        assertTrue(json.contains("\"templates\":{"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        McpToolSpecification result = mapper.readValue(MCP_TOOL_SPEC, McpToolSpecification.class);
        assertEquals(1, result.getTools().size());
        assertEquals("testTool", result.getTools().get(0).getName());
        assertEquals("test tool description", result.getTools().get(0).getDescription());
        assertEquals("object", result.getTools().get(0).getInputSchema().get("type"));
        assertNotNull(result.getTools().get(0).getInputSchema().get("properties"));
        assertEquals(1, result.getToolsMeta().size());
        assertNotNull(result.getToolsMeta().get("testTool"));
        assertNotNull(result.getToolsMeta().get("testTool").getInvokeContext());
        assertNotNull(result.getToolsMeta().get("testTool").getTemplates());
    }
}