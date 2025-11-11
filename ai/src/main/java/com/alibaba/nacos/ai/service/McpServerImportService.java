/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.ai.constant.McpServerValidationConstants;
import com.alibaba.nacos.ai.enums.ExternalDataTypeEnum;
import com.alibaba.nacos.ai.enums.McpImportResultStatusEnum;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MCP Server Import Service.
 * Handles the import logic for MCP servers from various sources.
 *
 * @author WangzJi
 */
@Service
public class McpServerImportService {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerImportService.class);

    private final McpExternalDataAdaptor transformService;

    private final McpServerValidationService validationService;

    private final McpServerOperationService operationService;

    public McpServerImportService(McpExternalDataAdaptor transformService,
                                  McpServerValidationService validationService,
                                  McpServerOperationService operationService) {
        this.transformService = transformService;
        this.validationService = validationService;
        this.operationService = operationService;
    }

    /**
     * Validate servers for import.
     *
     * @param namespaceId namespace ID
     * @param request     import request
     * @return validation result
     * @throws NacosException if validation fails
     */
    public McpServerImportValidationResult validateImport(String namespaceId, McpServerImportRequest request)
            throws NacosException {
        ExternalDataTypeEnum externalDataTypeEnum = ExternalDataTypeEnum.parseType(request.getImportType());
        if (Objects.isNull(externalDataTypeEnum)) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid import type: " + request.getImportType());
        }

        try {
            List<McpServerDetailInfo> servers;
            servers = transformService.adaptExternalDataToNacosMcpServerFormat(request);
            return validationService.validateServers(namespaceId, servers);

        } catch (Exception e) {
            McpServerImportValidationResult result = new McpServerImportValidationResult();
            result.setValid(false);
            List<String> errors = new ArrayList<>();
            errors.add("Import validation failed: " + e.getMessage());
            result.setErrors(errors);
            return result;
        }
    }

    /**
     * Execute import of MCP servers.
     *
     * @param namespaceId namespace ID
     * @param request     import request
     * @return import response
     */
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request) {
        try {
            McpServerImportValidationResult validationResult = validateImport(namespaceId, request);
            boolean valid = validationResult.isValid();
            boolean overrideExisting = request.isOverrideExisting();
            if (valid || request.isSkipInvalid()) {
                List<McpServerValidationItem> validSelectedServers =
                        filterValidSelectedServers(validationResult.getServers(), request.getSelectedServers());
                return applyResultOnRequestPolicy(namespaceId, validSelectedServers, overrideExisting);
            } else {
                return responseError("Import validation failed: " + String.join(", ",
                        validationResult.getErrors()));
            }
        } catch (Exception e) {
            LOG.error("Import execution failed", e);
            return responseError("Import execution failed: " + e.getMessage());
        }
    }

    private static McpServerImportResponse responseError(String msg) {
        McpServerImportResponse response = new McpServerImportResponse();
        response.setSuccess(false);
        response.setErrorMessage(msg);
        return response;
    }

    private McpServerImportResponse applyResultOnRequestPolicy(String namespaceId,
                                                               List<McpServerValidationItem> serversToImport,
                                                               boolean overwrite) {
        McpServerImportResponse response = new McpServerImportResponse();
        List<McpServerImportResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (McpServerValidationItem item : serversToImport) {
            McpServerImportResult result = importSingleServer(namespaceId, item, overwrite);
            if ("success".equals(result.getStatus())) {
                successCount++;
            } else if ("failed".equals(result.getStatus())) {
                failedCount++;
            } else if ("skipped".equals(result.getStatus())) {
                skippedCount++;
            }
            results.add(result);
        }
        response.setSuccess(failedCount == 0);
        response.setTotalCount(serversToImport.size());
        response.setSuccessCount(successCount);
        response.setFailedCount(failedCount);
        response.setSkippedCount(skippedCount);
        response.setResults(results);
        return response;
    }

    /**
     * Filter valid selected servers for import.
     *
     * @param validationItems validation items
     * @param selectedServers selected server IDs
     * @return filtered servers
     */
    private List<McpServerValidationItem> filterValidSelectedServers(List<McpServerValidationItem> validationItems,
            String[] selectedServers) {
        if (CollectionUtils.isEmpty(validationItems)) {
            return Collections.emptyList();
        }

        if (selectedServers == null || selectedServers.length == 0) {
            return validationItems;
        }

        Set<String> selectServers = new HashSet<>(Arrays.asList(selectedServers));
        return validationItems.stream()
                .filter(item -> McpServerValidationConstants.STATUS_VALID.equals(item.getStatus()))
                .filter(item -> selectServers.isEmpty() || selectServers.contains(item.getServerId()))
                .toList();
    }

    /**
     * Import single MCP server.
     *
     * @param namespaceId      namespace ID
     * @param item             validation item
     * @param overrideExisting whether to override existing servers
     * @return import result
     */
    private McpServerImportResult importSingleServer(String namespaceId, McpServerValidationItem item,
            boolean overrideExisting) {
        McpServerImportResult result = new McpServerImportResult();
        result.setServerName(item.getServerName());
        result.setServerId(item.getServerId());
        try {
            if (item.isExists() && !overrideExisting) {
                result.setStatus(McpImportResultStatusEnum.SKIPPED.getName());
                result.setConflictType("existing");
                return result;
            }

            McpServerDetailInfo server = item.getServer();
            McpToolSpecification toolSpec = server.getToolSpec();
            McpServerBasicInfo basicInfo = generateMcpBasicInfo(server);
            McpEndpointSpec endpointSpec = generateEndpointSpec(server);
            if (item.isExists() && overrideExisting) {
                operationService.updateMcpServer(namespaceId, true, basicInfo, toolSpec, endpointSpec, true);
            } else {
                operationService.createMcpServer(namespaceId, basicInfo, toolSpec, endpointSpec);
            }
            result.setStatus(McpImportResultStatusEnum.SUCCESS.getName());
        } catch (Exception e) {
            result.setStatus(McpImportResultStatusEnum.FAILED.getName());
            result.setErrorMessage("Failed to import server: " + e.getMessage());
        }
        return result;
    }

    private static McpServerBasicInfo generateMcpBasicInfo(McpServerDetailInfo server) {
        McpServerBasicInfo basicInfo = new McpServerBasicInfo();
        basicInfo.setId(server.getId());
        basicInfo.setName(server.getName());
        basicInfo.setProtocol(server.getProtocol());
        basicInfo.setFrontProtocol(server.getFrontProtocol());
        basicInfo.setDescription(server.getDescription());
        basicInfo.setStatus(server.getStatus());
        basicInfo.setRepository(server.getRepository());
        basicInfo.setVersionDetail(server.getVersionDetail());
        basicInfo.setRemoteServerConfig(server.getRemoteServerConfig());
        basicInfo.setPackages(server.getPackages());
        return basicInfo;
    }

    /**
     * Convert {@link McpServerDetailInfo} info to {@link McpEndpointSpec}.
     * <p>Only keep first item in frontEndpointConfigList for endpoint spec generation.</p>
     *
     * @param server server detail info
     * @return endpoint spec, null if not convert is not supported.
     */
    private McpEndpointSpec generateEndpointSpec(McpServerDetailInfo server) {
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(server.getProtocol())) {
            return null;
        }

        if (server.getRemoteServerConfig() == null
                || CollectionUtils.isEmpty(server.getRemoteServerConfig().getFrontEndpointConfigList())) {
            return null;
        }

        try {
            FrontEndpointConfig first = server.getRemoteServerConfig()
                            .getFrontEndpointConfigList()
                            .get(0);
            return McpConfigUtils.convertFrontEndpointConfig(first);
        } catch (Exception e) {
            LOG.error("Failed to convert to endpoint spec", e);
            throw new RuntimeException("Failed to convert to endpoint spec", e);
        }
    }
}