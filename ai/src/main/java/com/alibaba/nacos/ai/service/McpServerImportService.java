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

import com.alibaba.nacos.ai.constants.McpServerValidationConstants;
import com.alibaba.nacos.api.ai.constant.AiConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP Server Import Service.
 * Handles the import logic for MCP servers from various sources.
 *
 * @author WangzJi
 */
@Service
public class McpServerImportService {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerImportService.class);

    /**
     * Maximum servers per import batch.
     */
    private static final int MAX_IMPORT_BATCH_SIZE = 100;

    private final McpServerTransformService transformService;

    private final McpServerValidationService validationService;

    private final McpServerOperationService operationService;

    public McpServerImportService(McpServerTransformService transformService,
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
     * @param request import request
     * @return validation result
     * @throws NacosException if validation fails
     */
    public McpServerImportValidationResult validateImport(String namespaceId, McpServerImportRequest request)
            throws NacosException {
        try {
            List<McpServerDetailInfo> servers = transformService.transformToNacosFormat(
                    request.getData(), request.getImportType());

            if (servers.size() > MAX_IMPORT_BATCH_SIZE) {
                McpServerImportValidationResult result = new McpServerImportValidationResult();
                result.setValid(false);
                result.setTotalCount(servers.size());
                List<String> errors = new ArrayList<>();
                errors.add("Import batch size exceeds maximum limit: " + MAX_IMPORT_BATCH_SIZE);
                result.setErrors(errors);
                return result;
            }

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
     * @param request import request
     * @return import response
     * @throws NacosException if import fails
     */
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request)
            throws NacosException {
        McpServerImportResponse response = new McpServerImportResponse();
        List<McpServerImportResult> results = new ArrayList<>();

        try {
            McpServerImportValidationResult validationResult = validateImport(namespaceId, request);
            if (!validationResult.isValid()) {
                response.setSuccess(false);
                response.setErrorMessage("Import validation failed: " + String.join(", ", validationResult.getErrors()));
                return response;
            }

            List<McpServerValidationItem> serversToImport = filterSelectedServers(
                    validationResult.getServers(), request.getSelectedServers());

            int successCount = 0;
            int failedCount = 0;
            int skippedCount = 0;

            for (McpServerValidationItem item : serversToImport) {
                McpServerImportResult result = importSingleServer(namespaceId, item, request.isOverrideExisting());
                results.add(result);

                switch (result.getStatus()) {
                    case "success":
                        successCount++;
                        break;
                    case "failed":
                        failedCount++;
                        break;
                    case "skipped":
                        skippedCount++;
                        break;
                    default:
                        break;
                }
            }

            response.setSuccess(failedCount == 0);
            response.setTotalCount(serversToImport.size());
            response.setSuccessCount(successCount);
            response.setFailedCount(failedCount);
            response.setSkippedCount(skippedCount);
            response.setResults(results);

        } catch (Exception e) {
            LOG.error("Import execution failed", e);
            response.setSuccess(false);
            response.setErrorMessage("Import execution failed: " + e.getMessage());
        }

        return response;
    }

    /**
     * Filter selected servers for import.
     *
     * @param validationItems validation items
     * @param selectedServers selected server IDs
     * @return filtered servers
     */
    private List<McpServerValidationItem> filterSelectedServers(List<McpServerValidationItem> validationItems,
            String[] selectedServers) {
        if (selectedServers == null || selectedServers.length == 0) {
            return validationItems.stream()
                    .filter(item -> McpServerValidationConstants.STATUS_VALID.equals(item.getStatus()))
                    .collect(Collectors.toList());
        }

        Set<String> selectedSet = new HashSet<>(Arrays.asList(selectedServers));
        return validationItems.stream()
                .filter(item -> selectedSet.contains(item.getServerId()) && McpServerValidationConstants.STATUS_VALID.equals(item.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Import single MCP server.
     *
     * @param namespaceId namespace ID
     * @param item validation item
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
                result.setStatus("skipped");
                result.setConflictType("existing");
                return result;
            }

            McpServerDetailInfo server = item.getServer();

            // Create basic info from server detail
            McpServerBasicInfo basicInfo = new McpServerBasicInfo();
            basicInfo.setId(server.getId());
            basicInfo.setName(server.getName());
            basicInfo.setProtocol(server.getProtocol());
            basicInfo.setFrontProtocol(server.getFrontProtocol());
            basicInfo.setDescription(server.getDescription());
            basicInfo.setRepository(server.getRepository());
            basicInfo.setVersionDetail(server.getVersionDetail());
            basicInfo.setRemoteServerConfig(server.getRemoteServerConfig());

            // Extract tool specification
            McpToolSpecification toolSpec = server.getToolSpec();

            // Create endpoint specification
            McpEndpointSpec endpointSpec = convertToEndpointSpec(server);

            if (item.isExists() && overrideExisting) {
                operationService.updateMcpServer(namespaceId, true, basicInfo, toolSpec, endpointSpec);
            } else {
                operationService.createMcpServer(namespaceId, basicInfo, toolSpec, endpointSpec);
            }

            result.setStatus("success");

        } catch (Exception e) {
            result.setStatus("failed");
            result.setErrorMessage("Failed to import server: " + e.getMessage());
        }

        return result;
    }

    /**
     * Convert server detail info to endpoint spec.
     *
     * @param server server detail info
     * @return endpoint spec
     */
    private McpEndpointSpec convertToEndpointSpec(McpServerDetailInfo server) {
        McpEndpointSpec endpointSpec = new McpEndpointSpec();

        if (server.getRemoteServerConfig() != null) {
            // Set basic endpoint spec info
            if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(server.getProtocol())) {
                endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
            } else {
                endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
            }
        }

        return endpointSpec;
    }
}