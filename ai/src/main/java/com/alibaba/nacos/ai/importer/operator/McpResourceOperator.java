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
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Applies imported MCP artifacts to the current MCP server domain service.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class McpResourceOperator implements AiResourceOperator {
    
    private static final String CONFLICT_TYPE_EXISTING = "existing";
    
    private static final String CONFLICT_TYPE_DUPLICATE = "duplicate";
    
    private final McpServerImportService mcpServerImportService;
    
    public McpResourceOperator(McpServerImportService mcpServerImportService) {
        this.mcpServerImportService = mcpServerImportService;
    }
    
    @Override
    public String resourceType() {
        return AiResourceImportConstants.RESOURCE_TYPE_MCP;
    }
    
    @Override
    public AiResourceImportValidationItem validate(String namespaceId,
        AiResourceImportArtifact artifact, boolean overwriteExisting) throws NacosException {
        McpServerValidationItem validationItem = validateArtifact(namespaceId, artifact);
        return toValidationItem(artifact, validationItem);
    }
    
    @Override
    public AiResourceImportResultItem importResource(String namespaceId,
        AiResourceImportArtifact artifact, boolean overwriteExisting) throws NacosException {
        McpServerValidationItem validationItem = validateArtifact(namespaceId, artifact);
        if (McpServerValidationConstants.STATUS_INVALID.equals(validationItem.getStatus())) {
            return failedItem(artifact, String.join(", ", validationItem.getErrors()));
        }
        McpServerImportResult importResult = mcpServerImportService.importValidatedServer(
            namespaceId, validationItem, overwriteExisting);
        return toResultItem(artifact, importResult);
    }
    
    private McpServerValidationItem validateArtifact(String namespaceId,
        AiResourceImportArtifact artifact) throws NacosException {
        McpServerDetailInfo server = parseServer(artifact);
        McpServerImportValidationResult validationResult = mcpServerImportService
            .validateMcpServers(namespaceId, Collections.singletonList(server));
        if (validationResult == null || CollectionUtils.isEmpty(validationResult.getServers())) {
            throw invalid("MCP import validation returned empty result.");
        }
        return validationResult.getServers().get(0);
    }
    
    private McpServerDetailInfo parseServer(AiResourceImportArtifact artifact)
        throws NacosException {
        if (artifact == null) {
            throw invalid("MCP import artifact must not be null.");
        }
        if (artifact.getPayloadKind() != AiResourceImportPayloadKind.MCP_DETAIL
            && artifact.getPayloadKind() != AiResourceImportPayloadKind.JSON) {
            throw invalid("MCP import artifact payload kind is unsupported.");
        }
        if (StringUtils.isBlank(artifact.getPayloadJson())) {
            throw invalid("MCP import artifact payload json must not be empty.");
        }
        try {
            McpServerDetailInfo result =
                JacksonUtils.toObj(artifact.getPayloadJson(), McpServerDetailInfo.class);
            if (result == null) {
                throw invalid("MCP import artifact cannot be parsed.");
            }
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("MCP import artifact cannot be parsed: " + e.getMessage());
        }
    }
    
    private AiResourceImportValidationItem toValidationItem(AiResourceImportArtifact artifact,
        McpServerValidationItem item) {
        AiResourceImportValidationItem result = new AiResourceImportValidationItem();
        result.setExternalId(artifact.getExternalId());
        result.setName(StringUtils.isNotBlank(item.getServerName()) ? item.getServerName()
            : artifact.getName());
        result.setVersion(resolveVersion(artifact, item.getServer()));
        result.setExists(item.isExists());
        result.setErrors(item.getErrors());
        if (McpServerValidationConstants.STATUS_VALID.equals(item.getStatus())) {
            result.setStatus(AiResourceImportValidationStatus.VALID);
        } else if (McpServerValidationConstants.STATUS_DUPLICATE.equals(item.getStatus())) {
            result.setStatus(AiResourceImportValidationStatus.CONFLICT);
            result.setConflictType(item.isExists() ? CONFLICT_TYPE_EXISTING
                : CONFLICT_TYPE_DUPLICATE);
        } else {
            result.setStatus(AiResourceImportValidationStatus.INVALID);
        }
        return result;
    }
    
    private AiResourceImportResultItem toResultItem(AiResourceImportArtifact artifact,
        McpServerImportResult importResult) {
        AiResourceImportResultItem result = new AiResourceImportResultItem();
        result.setExternalId(artifact.getExternalId());
        result.setResourceName(importResult.getServerName());
        result.setVersion(artifact.getVersion());
        result.setErrorMessage(importResult.getErrorMessage());
        if (McpImportResultStatusEnum.SUCCESS.getName().equals(importResult.getStatus())) {
            result.setStatus(AiResourceImportResultStatus.SUCCESS);
        } else if (McpImportResultStatusEnum.SKIPPED.getName().equals(importResult.getStatus())) {
            result.setStatus(AiResourceImportResultStatus.SKIPPED);
        } else {
            result.setStatus(AiResourceImportResultStatus.FAILED);
        }
        return result;
    }
    
    private AiResourceImportResultItem failedItem(AiResourceImportArtifact artifact,
        String errorMessage) {
        AiResourceImportResultItem result = new AiResourceImportResultItem();
        result.setExternalId(artifact.getExternalId());
        result.setResourceName(artifact.getName());
        result.setVersion(artifact.getVersion());
        result.setStatus(AiResourceImportResultStatus.FAILED);
        result.setErrorMessage(errorMessage);
        return result;
    }
    
    private String resolveVersion(AiResourceImportArtifact artifact, McpServerDetailInfo server) {
        if (server != null && server.getVersionDetail() != null) {
            return server.getVersionDetail().getVersion();
        }
        return artifact.getVersion();
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
