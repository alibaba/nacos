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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.enums.ExternalDataTypeEnum;
import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportCandidateItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerValidationItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Compatibility adapter from legacy MCP import APIs to unified AI resource import APIs.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class McpLegacyImportAdapter {
    
    private static final String STATUS_VALID = "valid";
    
    private static final String STATUS_INVALID = "invalid";
    
    private static final String STATUS_DUPLICATE = "duplicate";
    
    private final McpServerImportService mcpServerImportService;
    
    private final com.alibaba.nacos.ai.importer.manager.AiResourceImportManager importManager;
    
    private Supplier<AiResourceImportProperties> propertiesSupplier =
        AiResourceImportProperties::loadFromEnvironment;
    
    public McpLegacyImportAdapter(McpServerImportService mcpServerImportService,
        com.alibaba.nacos.ai.importer.manager.AiResourceImportManager importManager) {
        this.mcpServerImportService = mcpServerImportService;
        this.importManager = importManager;
    }
    
    /**
     * Validate a legacy MCP import request.
     *
     * @param namespaceId namespace ID
     * @param request legacy import request
     * @return legacy validation response
     * @throws NacosException if validation cannot start
     */
    public McpServerImportValidationResult validateImport(String namespaceId,
        McpServerImportRequest request) throws NacosException {
        if (!isLegacyApiEnabled()) {
            return deprecatedValidation();
        }
        if (!shouldRouteToUnifiedImport(request)) {
            return shouldRejectUserUrl(request) ? rejectedValidation()
                : mcpServerImportService.validateImport(namespaceId, request);
        }
        try {
            AiResourceImportValidateResponse response = importManager
                .validate(buildValidateRequest(namespaceId, request));
            return toLegacyValidationResult(response);
        } catch (NacosException e) {
            return failedValidation(e.getErrMsg());
        }
    }
    
    /**
     * Execute a legacy MCP import request.
     *
     * @param namespaceId namespace ID
     * @param request legacy import request
     * @return legacy execute response
     * @throws NacosException if import cannot start
     */
    public McpServerImportResponse executeImport(String namespaceId, McpServerImportRequest request)
        throws NacosException {
        if (!isLegacyApiEnabled()) {
            return deprecatedResponse();
        }
        if (!shouldRouteToUnifiedImport(request)) {
            return shouldRejectUserUrl(request) ? rejectedResponse()
                : mcpServerImportService.executeImport(namespaceId, request);
        }
        try {
            AiResourceImportExecuteResponse response = importManager
                .execute(buildExecuteRequest(namespaceId, request));
            return toLegacyExecuteResponse(response);
        } catch (NacosException e) {
            return failedResponse(e.getErrMsg());
        }
    }
    
    private boolean shouldRouteToUnifiedImport(McpServerImportRequest request) {
        return request != null && ExternalDataTypeEnum.URL.getName().equals(request.getImportType())
            && !isUrl(request.getData());
    }
    
    private boolean isLegacyApiEnabled() {
        return propertiesSupplier.get().isLegacyMcpImportApiEnabled();
    }
    
    private boolean shouldRejectUserUrl(McpServerImportRequest request) {
        return request != null && ExternalDataTypeEnum.URL.getName().equals(request.getImportType())
            && isUrl(request.getData()) && !propertiesSupplier.get().isAllowUserUrl();
    }
    
    private boolean isUrl(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception e) {
            return false;
        }
    }
    
    private AiResourceImportValidateRequest buildValidateRequest(String namespaceId,
        McpServerImportRequest request) throws NacosException {
        AiResourceImportValidateRequest result = new AiResourceImportValidateRequest();
        result.setNamespaceId(namespaceId);
        result.setResourceType(AiResourceImportConstants.RESOURCE_TYPE_MCP);
        result.setSourceId(request.getData());
        result.setOverwriteExisting(request.isOverrideExisting());
        result.setSelectedItems(resolveSelectedItems(namespaceId, request));
        return result;
    }
    
    private AiResourceImportExecuteRequest buildExecuteRequest(String namespaceId,
        McpServerImportRequest request) throws NacosException {
        AiResourceImportExecuteRequest result = new AiResourceImportExecuteRequest();
        result.setNamespaceId(namespaceId);
        result.setResourceType(AiResourceImportConstants.RESOURCE_TYPE_MCP);
        result.setSourceId(request.getData());
        result.setOverwriteExisting(request.isOverrideExisting());
        result.setSkipInvalid(request.isSkipInvalid());
        result.setSelectedItems(resolveSelectedItems(namespaceId, request));
        return result;
    }
    
    private List<AiResourceImportItem> resolveSelectedItems(String namespaceId,
        McpServerImportRequest request) throws NacosException {
        if (request.getSelectedServers() != null && request.getSelectedServers().length > 0) {
            List<AiResourceImportItem> result =
                new ArrayList<>(request.getSelectedServers().length);
            for (String each : request.getSelectedServers()) {
                if (StringUtils.isNotBlank(each)) {
                    result.add(selectedItem(each, each, null));
                }
            }
            return result;
        }
        AiResourceImportSearchRequest searchRequest = new AiResourceImportSearchRequest();
        searchRequest.setNamespaceId(namespaceId);
        searchRequest.setResourceType(AiResourceImportConstants.RESOURCE_TYPE_MCP);
        searchRequest.setSourceId(request.getData());
        searchRequest.setCursor(request.getCursor());
        searchRequest.setLimit(request.getLimit());
        searchRequest.setQuery(request.getSearch());
        AiResourceImportSearchResponse searchResponse = importManager.search(searchRequest);
        if (searchResponse == null || CollectionUtils.isEmpty(searchResponse.getItems())) {
            return Collections.emptyList();
        }
        List<AiResourceImportItem> result = new ArrayList<>(searchResponse.getItems().size());
        for (AiResourceImportCandidateItem each : searchResponse.getItems()) {
            result.add(selectedItem(each.getExternalId(), each.getName(), each.getVersion()));
        }
        return result;
    }
    
    private AiResourceImportItem selectedItem(String externalId, String name, String version) {
        AiResourceImportItem result = new AiResourceImportItem();
        result.setExternalId(externalId);
        result.setName(name);
        result.setVersion(version);
        return result;
    }
    
    private McpServerImportValidationResult toLegacyValidationResult(
        AiResourceImportValidateResponse response) {
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        List<McpServerValidationItem> servers = new ArrayList<>();
        int validCount = 0;
        int invalidCount = 0;
        int duplicateCount = 0;
        for (AiResourceImportValidationItem each : response.getItems()) {
            McpServerValidationItem item = toLegacyValidationItem(each);
            servers.add(item);
            if (STATUS_VALID.equals(item.getStatus())) {
                validCount++;
            } else if (STATUS_DUPLICATE.equals(item.getStatus())) {
                duplicateCount++;
            } else {
                invalidCount++;
            }
        }
        result.setServers(servers);
        result.setTotalCount(servers.size());
        result.setValidCount(validCount);
        result.setInvalidCount(invalidCount);
        result.setDuplicateCount(duplicateCount);
        result.setValid(invalidCount == 0);
        result.setErrors(Collections.emptyList());
        return result;
    }
    
    private McpServerValidationItem toLegacyValidationItem(AiResourceImportValidationItem item) {
        McpServerValidationItem result = new McpServerValidationItem();
        result.setServerId(item.getExternalId());
        result.setServerName(item.getName());
        result.setExists(item.isExists());
        result.setErrors(item.getErrors());
        result.setSelected(true);
        if (AiResourceImportValidationStatus.VALID == item.getStatus()
            || AiResourceImportValidationStatus.WARNING == item.getStatus()) {
            result.setStatus(STATUS_VALID);
        } else if (AiResourceImportValidationStatus.CONFLICT == item.getStatus()) {
            result.setStatus(STATUS_DUPLICATE);
        } else {
            result.setStatus(STATUS_INVALID);
        }
        return result;
    }
    
    private McpServerImportResponse toLegacyExecuteResponse(
        AiResourceImportExecuteResponse response) {
        McpServerImportResponse result = new McpServerImportResponse();
        result.setSuccess(response.isSuccess());
        result.setTotalCount(response.getTotalCount());
        result.setSuccessCount(response.getSuccessCount());
        result.setFailedCount(response.getFailedCount());
        result.setSkippedCount(response.getSkippedCount());
        List<McpServerImportResult> results = new ArrayList<>();
        for (AiResourceImportResultItem each : response.getResults()) {
            results.add(toLegacyImportResult(each));
        }
        result.setResults(results);
        return result;
    }
    
    private McpServerImportResult toLegacyImportResult(AiResourceImportResultItem item) {
        McpServerImportResult result = new McpServerImportResult();
        result.setServerId(item.getExternalId());
        result.setServerName(item.getResourceName());
        result.setErrorMessage(item.getErrorMessage());
        if (AiResourceImportResultStatus.SUCCESS == item.getStatus()) {
            result.setStatus("success");
        } else if (AiResourceImportResultStatus.SKIPPED == item.getStatus()) {
            result.setStatus("skipped");
        } else {
            result.setStatus("failed");
        }
        return result;
    }
    
    private McpServerImportValidationResult rejectedValidation() {
        return failedValidation(
            "Legacy URL import is disabled. Please use a configured source id.");
    }
    
    private McpServerImportResponse rejectedResponse() {
        return failedResponse("Legacy URL import is disabled. Please use a configured source id.");
    }
    
    private McpServerImportValidationResult deprecatedValidation() {
        return failedValidation("Legacy MCP import API is disabled. Please use the unified "
            + "AI resource import API or enable nacos.ai.resource.import.legacy-mcp-api-enabled "
            + "for a compatibility window.");
    }
    
    private McpServerImportResponse deprecatedResponse() {
        return failedResponse("Legacy MCP import API is disabled. Please use the unified "
            + "AI resource import API or enable nacos.ai.resource.import.legacy-mcp-api-enabled "
            + "for a compatibility window.");
    }
    
    private McpServerImportValidationResult failedValidation(String errorMessage) {
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        result.setValid(false);
        result.setErrors(Collections.singletonList(errorMessage));
        return result;
    }
    
    private McpServerImportResponse failedResponse(String errorMessage) {
        McpServerImportResponse result = new McpServerImportResponse();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
