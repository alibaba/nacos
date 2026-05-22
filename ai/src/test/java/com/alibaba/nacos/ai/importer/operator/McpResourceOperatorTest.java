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

package com.alibaba.nacos.ai.importer.operator;

import com.alibaba.nacos.ai.constant.McpServerValidationConstants;
import com.alibaba.nacos.ai.enums.McpImportResultStatusEnum;
import com.alibaba.nacos.ai.service.McpServerImportService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpResourceOperator}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class McpResourceOperatorTest {
    
    @Mock
    private McpServerImportService mcpServerImportService;
    
    private McpResourceOperator operator;
    
    @BeforeEach
    void setUp() {
        operator = new McpResourceOperator(mcpServerImportService);
    }
    
    @Test
    void testValidateMapsDuplicateToConflict() throws Exception {
        when(mcpServerImportService.validateMcpServers(eq("public"), any()))
            .thenReturn(validationResult(McpServerValidationConstants.STATUS_DUPLICATE, true));
        
        AiResourceImportValidationItem result =
            operator.validate("public", artifact(), false);
        
        assertEquals(AiResourceImportValidationStatus.CONFLICT, result.getStatus());
        assertEquals("existing", result.getConflictType());
        assertEquals("io.nacos/test-server", result.getName());
        assertEquals("1.0.0", result.getVersion());
    }
    
    @Test
    void testImportUsesMcpImportService() throws Exception {
        when(mcpServerImportService.validateMcpServers(eq("public"), any()))
            .thenReturn(validationResult(McpServerValidationConstants.STATUS_VALID, false));
        McpServerImportResult importResult = new McpServerImportResult();
        importResult.setServerId("server-id");
        importResult.setServerName("io.nacos/test-server");
        importResult.setStatus(McpImportResultStatusEnum.SUCCESS.getName());
        when(mcpServerImportService.importValidatedServer(eq("public"), any(), eq(true)))
            .thenReturn(importResult);
        
        AiResourceImportResultItem result = operator.importResource("public", artifact(), true);
        
        assertEquals(AiResourceImportResultStatus.SUCCESS, result.getStatus());
        assertEquals("io.nacos/test-server", result.getResourceName());
        verify(mcpServerImportService).importValidatedServer(eq("public"), any(), eq(true));
    }
    
    @Test
    void testImportInvalidArtifactReturnsFailedItem() throws Exception {
        when(mcpServerImportService.validateMcpServers(eq("public"), any()))
            .thenReturn(validationResult(McpServerValidationConstants.STATUS_INVALID, false));
        
        AiResourceImportResultItem result = operator.importResource("public", artifact(), false);
        
        assertEquals(AiResourceImportResultStatus.FAILED, result.getStatus());
        verify(mcpServerImportService, never()).importValidatedServer(any(), any(), eq(false));
    }
    
    @Test
    void testValidateRejectsUnsupportedPayloadKind() {
        AiResourceImportArtifact artifact = artifact();
        artifact.setPayloadKind(AiResourceImportPayloadKind.SKILL_ZIP);
        
        assertThrows(Exception.class, () -> operator.validate("public", artifact, false));
    }
    
    @Test
    void testResourceType() {
        assertEquals(AiResourceImportConstants.RESOURCE_TYPE_MCP, operator.resourceType());
    }
    
    private McpServerImportValidationResult validationResult(String status, boolean exists) {
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerId("server-id");
        item.setServerName("io.nacos/test-server");
        item.setStatus(status);
        item.setExists(exists);
        item.setErrors(Collections.singletonList("validation error"));
        item.setServer(server());
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        result.setServers(Collections.singletonList(item));
        return result;
    }
    
    private AiResourceImportArtifact artifact() {
        AiResourceImportArtifact artifact = new AiResourceImportArtifact();
        artifact.setResourceType(AiResourceImportConstants.RESOURCE_TYPE_MCP);
        artifact.setExternalId("io.nacos/test-server");
        artifact.setName("io.nacos/test-server");
        artifact.setVersion("1.0.0");
        artifact.setPayloadKind(AiResourceImportPayloadKind.MCP_DETAIL);
        artifact.setPayloadJson(JacksonUtils.toJson(server()));
        return artifact;
    }
    
    private McpServerDetailInfo server() {
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setId("server-id");
        server.setName("io.nacos/test-server");
        server.setDescription("test server");
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        server.setVersionDetail(versionDetail);
        return server;
    }
}
