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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolSpecificationTest extends BasicRequestTest {
    
    private static final String MCP_TOOL_SPEC =
            "{\"tools\":[{\"name\":\"testTool\",\"description\":\"test tool description\",\"inputSchema\":{\"type\":\"object\","
                    + "\"properties\":{\"a\":{\"description\":\"aaa\",\"type\":\"string\"}}}}],\"toolsMeta\":{\"testTool\":"
                    + "{\"invokeContext\":{\"path\":\"/xxx\",\"method\":\"GET\"},\"enabled\":true,\"templates\":"
                    + "{\"json-go-tamplate\":{\"templateType\":\"string\",\"responseTemplate\":{\"body\":\"string\"},"
                    + "\"requestTemplate\":{\"headers\":[],\"method\":\"GET\",\"argsToFormBody\":true,\"argsToJsonBody\":false,"
                    + "\"body\":\"string\",\"url\":\"\",\"argsToUrlParam\":true}}}}},\"securitySchemes\":[{\"id\":\"1\","
                    + "\"type\":\"apiKey\",\"scheme\":\"\",\"in\":\"header\",\"name\":\"testSecurity\","
                    + "\"defaultCredential\":\"publicKey\"}]}";
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpToolSpecification toolSpecification = new McpToolSpecification();
        toolSpecification.setSpecificationType("encrypted");
        
        // 添加 EncryptObject 测试
        EncryptObject encryptObject = new EncryptObject();
        encryptObject.setData("encryptedData");
        Map<String, String> encryptInfo = new HashMap<>();
        encryptInfo.put("alg", "AES");
        encryptInfo.put("iv", "initialVector");
        encryptObject.setEncryptInfo(encryptInfo);
        toolSpecification.setEncryptData(encryptObject);
        
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

        Map<String, Object> outputSchema = new HashMap<>();
        outputSchema.put("type", "object");
        Map<String, Object> outProperties = new HashMap<>();
        Map<String, String> resultSchema = new HashMap<>();
        resultSchema.put("type", "string");
        resultSchema.put("description", "result");
        outProperties.put("result", resultSchema);
        outputSchema.put("properties", outProperties);
        mcpTool.setOutputSchema(outputSchema);
        
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
        
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.setId("1");
        securityScheme.setType("apiKey");
        securityScheme.setName("testSecurity");
        securityScheme.setScheme("");
        securityScheme.setIn("header");
        securityScheme.setDefaultCredential("publicKey");
        toolSpecification.setSecuritySchemes(Collections.singletonList(securityScheme));
        
        String json = mapper.writeValueAsString(toolSpecification);
        assertNotNull(json);
        assertTrue(json.contains("\"specificationType\":\"encrypted\""));
        assertTrue(json.contains("\"encryptData\":{"));
        assertTrue(json.contains("\"data\":\"encryptedData\""));
        assertTrue(json.contains("\"encryptInfo\":{"));
        assertTrue(json.contains("\"tools\":[{"));
        assertTrue(json.contains("\"name\":\"testTool\""));
        assertTrue(json.contains("\"description\":\"test tool description\""));
        assertTrue(json.contains("\"inputSchema\":{"));
        assertTrue(json.contains("{\"type\":\"object\""));
        assertTrue(json.contains("\"properties\":{\"a\":{"));
        assertTrue(json.contains("\"outputSchema\":{"));
        assertTrue(json.contains("\"toolsMeta\":{\"testTool\":{"));
        assertTrue(json.contains("\"invokeContext\":{"));
        assertTrue(json.contains("\"templates\":{"));
        assertTrue(json.contains("\"securitySchemes\":[{"));
        
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"specificationType\":\"encrypted\",\"encryptData\":{\"data\":\"encryptedData\","
                + "\"encryptInfo\":{\"alg\":\"AES\",\"iv\":\"initialVector\"}},"
                + "\"tools\":[{\"name\":\"testTool\",\"description\":\"test tool description\","
                + "\"inputSchema\":{\"type\":\"object\","
                + "\"properties\":{\"a\":{\"description\":\"aaa\",\"type\":\"string\"}}},"
                + "\"outputSchema\":{\"type\":\"object\","
                + "\"properties\":{\"result\":{\"type\":\"string\","
                + "\"description\":\"result\"}}}}],"
                + "\"toolsMeta\":{\"testTool\":"
                + "{\"invokeContext\":{\"path\":\"/xxx\",\"method\":\"GET\"},\"enabled\":true,\"templates\":"
                + "{\"json-go-tamplate\":{\"templateType\":\"string\",\"responseTemplate\":{\"body\":\"string\"},"
                + "\"requestTemplate\":{\"headers\":[],\"method\":\"GET\",\"argsToFormBody\":true,"
                + "\"argsToJsonBody\":false,"
                + "\"body\":\"string\",\"url\":\"\",\"argsToUrlParam\":true}}}}},"
                + "\"securitySchemes\":[{\"id\":\"1\","
                + "\"type\":\"apiKey\",\"scheme\":\"\",\"in\":\"header\",\"name\":\"testSecurity\","
                + "\"defaultCredential\":\"publicKey\"}]}";
        
        McpToolSpecification result = mapper.readValue(json, McpToolSpecification.class);
        assertEquals("encrypted", result.getSpecificationType());
        assertNotNull(result.getEncryptData());
        assertEquals("encryptedData", result.getEncryptData().getData());
        assertNotNull(result.getEncryptData().getEncryptInfo());
        assertEquals("AES", result.getEncryptData().getEncryptInfo().get("alg"));
        assertEquals("initialVector", result.getEncryptData().getEncryptInfo().get("iv"));
        assertEquals(1, result.getTools().size());
        assertEquals("testTool", result.getTools().get(0).getName());
        assertEquals("test tool description", result.getTools().get(0).getDescription());
        assertEquals("object", result.getTools().get(0).getInputSchema().get("type"));
        assertNotNull(result.getTools().get(0).getInputSchema().get("properties"));
        assertEquals("object", result.getTools().get(0).getOutputSchema().get("type"));
        assertNotNull(result.getTools().get(0).getOutputSchema().get("properties"));
        assertEquals(1, result.getToolsMeta().size());
        assertNotNull(result.getToolsMeta().get("testTool"));
        assertNotNull(result.getToolsMeta().get("testTool").getInvokeContext());
        assertNotNull(result.getToolsMeta().get("testTool").getTemplates());
        assertNotNull(result.getSecuritySchemes());
        assertEquals(1, result.getSecuritySchemes().size());
    }
    
    @Test
    void testDeserializeOriginal() throws JsonProcessingException {
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
        assertNotNull(result.getSecuritySchemes());
        assertEquals(1, result.getSecuritySchemes().size());
        // 默认值测试
        assertNull(result.getSpecificationType());
        assertNull(result.getEncryptData());
    }
}