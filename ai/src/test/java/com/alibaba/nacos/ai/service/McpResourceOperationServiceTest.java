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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpResourceOperationServiceTest {
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    private McpResourceOperationService resourceOperationService;
    
    @BeforeEach
    void setUp() {
        resourceOperationService =
            new McpResourceOperationService(configQueryChainService, configOperationService);
    }
    
    @Test
    void refreshMcpResource() throws NacosException {
        McpServerBasicInfo serverBasicInfo = getMcpServerBasicInfo();
        McpResourceSpecification resourceSpecification = getMcpResourceSpecification();
        resourceOperationService.refreshMcpResource(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
            serverBasicInfo,
            resourceSpecification);
        verify(configOperationService).publishConfig(any(ConfigFormV3.class),
            any(ConfigRequestInfo.class), isNull());
    }
    
    @Test
    void getMcpResource() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(JacksonUtils.toJson(getMcpResourceSpecification()));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        String id = UUID.randomUUID().toString();
        String version = "1.0.0";
        String resourceRef = McpConfigUtils.formatServerResourceSpecDataId(id, version);
        McpResourceSpecification actual = resourceOperationService.getMcpResource(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, resourceRef);
        assertNotNull(actual);
    }
    
    @Test
    void getMcpResourceNotFound() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        String id = UUID.randomUUID().toString();
        String version = "1.0.0";
        String resourceRef = McpConfigUtils.formatServerResourceSpecDataId(id, version);
        McpResourceSpecification actual = resourceOperationService.getMcpResource(
            AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, resourceRef);
        assertNull(actual);
    }
    
    @Test
    void deleteMcpResource() throws NacosException {
        String id = UUID.randomUUID().toString();
        resourceOperationService.deleteMcpResource(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, id,
            "1.0.0");
        verify(configOperationService).deleteConfig(
            McpConfigUtils.formatServerResourceSpecDataId(id, "1.0.0"),
            Constants.MCP_SERVER_RESOURCE_GROUP, AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, null, null,
            "nacos",
            null);
    }
    
    private McpServerBasicInfo getMcpServerBasicInfo() {
        String id = UUID.randomUUID().toString();
        McpServerBasicInfo serverBasicInfo = new McpServerBasicInfo();
        serverBasicInfo.setId(id);
        serverBasicInfo.setName("mcpName");
        serverBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        serverBasicInfo.setDescription("Mock Mcp Server");
        serverBasicInfo.setEnabled(true);
        serverBasicInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        ServerVersionDetail serverVersionDetail = new ServerVersionDetail();
        serverVersionDetail.setVersion("1.0.0");
        serverBasicInfo.setVersionDetail(serverVersionDetail);
        return serverBasicInfo;
    }
    
    private McpResourceSpecification getMcpResourceSpecification() {
        McpResourceSpecification resourceSpecification = new McpResourceSpecification();
        HashMap<String, Object> resource = new HashMap<>();
        resource.put("name", "readme");
        resource.put("uri", "file:///README.md");
        resourceSpecification.getResources().add(resource);
        return resourceSpecification;
    }
}
