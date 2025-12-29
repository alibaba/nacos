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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRequestUtilTest {
    
    private static final String MCP_SERVER_SPEC_OLD = "{\"protocol\":\"stdio\",\"name\":\"nacos-mcp-server\","
            + "\"description\":\"nacos local mcp server(test version)\",\"version\":\"0.1.0\",\"enabled\":true,\"localServerConfig\":{}}";
    
    private static final String MCP_SERVER_SPEC_NEW =
            "{\"protocol\":\"stdio\",\"frontProtocol\":\"stdio\",\"name\":\"nacos-mcp-server\","
                    + "\"id\":\"\",\"description\":\"nacos local mcp server(test version)\",\"versionDetail\":{\"version\":\"1.0.0\"},"
                    + "\"enabled\":true,\"localServerConfig\":{}}'";
    
    private static final String MCP_TOOL_SPEC =
            "{\"tools\":[{\"name\":\"list_namespace\",\"description\":\"list namespace in nacos\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\",\"description\":\"aaa\"}}},"
                + "\"outputSchema\":{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\"}}}}],"
                    + "\"toolsMeta\":{\"list_namespace\":{\"invokeContext\":{\"path\":\"/xxx\",\"method\":\"GET\"},\"enabled\":true,"
                    + "\"templates\":{\"json-go-tamplate\":{\"templateType\":\"string\",\"requestTemplate\":{\"url\":\"\",\"method\":\"GET\","
                    + "\"headers\":[],\"argsToJsonBody\":false,\"argsToUrlParam\":true,\"argsToFormBody\":true,\"body\":\"string\"},"
                    + "\"responseTemplate\":{\"body\":\"string\"}}}}}}";
    
    private static final String MCP_ENDPOINT_SPEC = "{\"type\":\"DIRECT\",\"data\":{\"address\":\"127.0.0.1\",\"port\":8848}}";
    
    @Test
    void parseMcpServerBasicInfoWithOldData() throws NacosApiException {
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setServerSpecification(MCP_SERVER_SPEC_OLD);
        McpServerBasicInfo actual = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, actual.getProtocol());
        assertEquals("nacos-mcp-server", actual.getName());
        assertEquals("nacos local mcp server(test version)", actual.getDescription());
        assertEquals("0.1.0", actual.getVersion());
        assertNull(actual.getVersionDetail());
        assertTrue(actual.isEnabled());
        assertTrue(actual.getLocalServerConfig().isEmpty());
    }
    
    @Test
    void parseMcpServerBasicInfoWithNewData() throws NacosApiException {
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setServerSpecification(MCP_SERVER_SPEC_NEW);
        McpServerBasicInfo actual = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, actual.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, actual.getFrontProtocol());
        assertEquals("nacos-mcp-server", actual.getName());
        assertEquals("nacos local mcp server(test version)", actual.getDescription());
        assertNull(actual.getVersion());
        assertEquals("1.0.0", actual.getVersionDetail().getVersion());
        assertTrue(actual.isEnabled());
        assertTrue(actual.getLocalServerConfig().isEmpty());
    }
    
    @Test
    void parseMcpServerBasicInfoWithNewDataNoName() throws NacosApiException {
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setServerSpecification(MCP_SERVER_SPEC_NEW.replace("nacos-mcp-server", ""));
        mcpForm.setMcpName("nacos-mcp-server");
        McpServerBasicInfo actual = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, actual.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, actual.getFrontProtocol());
        assertEquals("nacos-mcp-server", actual.getName());
        assertEquals("nacos local mcp server(test version)", actual.getDescription());
        assertNull(actual.getVersion());
        assertEquals("1.0.0", actual.getVersionDetail().getVersion());
        assertTrue(actual.isEnabled());
        assertTrue(actual.getLocalServerConfig().isEmpty());
    }
    
    @Test
    void parseMcpServerBasicInfoWithWrongData() {
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setServerSpecification("{");
        assertThrows(NacosApiException.class, () -> McpRequestUtil.parseMcpServerBasicInfo(mcpForm),
                "serverSpecification or toolSpecification is invalid. Can't be parsed.");
    }
    
    @Test
    void parseMcpToolsWithoutToolSpec() throws NacosApiException {
        McpDetailForm mcpForm = new McpDetailForm();
        assertNull(McpRequestUtil.parseMcpTools(mcpForm));
    }
    
    @Test
    void parseMcpToolsWithWrongData() {
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setToolSpecification("{");
        assertThrows(NacosApiException.class, () -> McpRequestUtil.parseMcpTools(mcpForm),
                "serverSpecification or toolSpecification is invalid. Can't be parsed.");
    }
    
    @Test
    void parseMcpToolsSuccess() throws NacosApiException {
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setToolSpecification(MCP_TOOL_SPEC);
        McpToolSpecification actual = McpRequestUtil.parseMcpTools(mcpForm);
        assertEquals(1, actual.getTools().size());
        assertEquals("list_namespace", actual.getTools().get(0).getName());
        assertEquals("list namespace in nacos", actual.getTools().get(0).getDescription());
        assertEquals(2, actual.getTools().get(0).getInputSchema().size());
        assertNotNull(actual.getTools().get(0).getOutputSchema());
        assertEquals("object", actual.getTools().get(0).getOutputSchema().get("type"));
        assertEquals(1, actual.getToolsMeta().size());
        assertNotNull(actual.getToolsMeta().get("list_namespace"));
        assertNotNull(actual.getToolsMeta().get("list_namespace").getInvokeContext());
        assertTrue(actual.getToolsMeta().get("list_namespace").isEnabled());
        assertNotNull(actual.getToolsMeta().get("list_namespace").getTemplates());
    }
    
    @Test
    void parseMcpEndpointSpecForStdioType() throws NacosApiException {
        McpServerBasicInfo mcpServerBasicInfo = new McpServerBasicInfo();
        mcpServerBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setEndpointSpecification(MCP_ENDPOINT_SPEC);
        assertNull(McpRequestUtil.parseMcpEndpointSpec(mcpServerBasicInfo, mcpForm));
    }
    
    @Test
    void parseMcpEndpointSpecWithoutSpec() {
        McpServerBasicInfo mcpServerBasicInfo = new McpServerBasicInfo();
        mcpServerBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        assertThrows(NacosApiException.class,
                () -> McpRequestUtil.parseMcpEndpointSpec(mcpServerBasicInfo, new McpDetailForm()),
                "request parameter `endpointSpecification` is required if mcp server type not `local`.");
    }
    
    @Test
    void parseMcpEndpointSpecSuccess() throws NacosApiException {
        McpServerBasicInfo mcpServerBasicInfo = new McpServerBasicInfo();
        mcpServerBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        McpDetailForm mcpForm = new McpDetailForm();
        mcpForm.setEndpointSpecification(MCP_ENDPOINT_SPEC);
        McpEndpointSpec actual = McpRequestUtil.parseMcpEndpointSpec(mcpServerBasicInfo, mcpForm);
        assertEquals(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT, actual.getType());
        assertEquals(2, actual.getData().size());
        assertEquals("127.0.0.1", actual.getData().get("address"));
        assertEquals("8848", actual.getData().get("port"));
    }
    
    @Test
    void transferToMcpServiceRefForMcpServiceRef() {
        McpServiceRef mcpServiceRef = new McpServiceRef();
        McpServiceRef actual = McpRequestUtil.transferToMcpServiceRef(mcpServiceRef);
        assertEquals(mcpServiceRef, actual);
    }
    
    @Test
    void transferToMcpServiceRefForMap() {
        Map<String, String> input = new HashMap<>();
        input.put("namespaceId", "test");
        input.put("groupName", "testGroup");
        input.put("serviceName", "testService");
        McpServiceRef actual = McpRequestUtil.transferToMcpServiceRef(input);
        assertEquals("test", actual.getNamespaceId());
        assertEquals("testGroup", actual.getGroupName());
        assertEquals("testService", actual.getServiceName());
    }
    
    @Test
    void transferToMcpServiceRefForOther() {
        assertThrows(IllegalArgumentException.class, () -> McpRequestUtil.transferToMcpServiceRef(new Object()));
    }
}