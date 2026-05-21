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

package com.alibaba.nacos.ai.importer.manager;

import com.alibaba.nacos.ai.importer.operator.AiResourceOperator;
import com.alibaba.nacos.ai.importer.operator.AiResourceOperatorRegistry;
import com.alibaba.nacos.ai.importer.security.AiResourceImportSecurityGuard;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportCandidateItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates AI resource import search, validation, and execution.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportManager {
    
    private final AiResourceImportSourceManager sourceManager;
    
    private final AiResourceImportPluginManager pluginManager;
    
    private final AiResourceOperatorRegistry operatorRegistry;
    
    private final AiResourceImportSecurityGuard securityGuard;
    
    public AiResourceImportManager(AiResourceImportSourceManager sourceManager,
        AiResourceImportPluginManager pluginManager, AiResourceOperatorRegistry operatorRegistry,
        AiResourceImportSecurityGuard securityGuard) {
        this.sourceManager = sourceManager;
        this.pluginManager = pluginManager;
        this.operatorRegistry = operatorRegistry;
        this.securityGuard = securityGuard;
    }
    
    /**
     * List enabled import sources.
     *
     * @param resourceType optional resource type
     * @return source info list
     * @throws NacosException if source configuration is invalid
     */
    public List<AiResourceImportSourceInfo> listSources(String resourceType)
        throws NacosException {
        return sourceManager.listSourceInfos(resourceType);
    }
    
    /**
     * Search external candidates from an operator-configured source.
     *
     * @param request search request
     * @return search response
     * @throws NacosException if source or plugin cannot handle the request
     */
    public AiResourceImportSearchResponse search(AiResourceImportSearchRequest request)
        throws NacosException {
        requireRequest(request);
        AiResourceImportSource source =
            sourceManager.resolveSource(request.getSourceId(), request.getResourceType());
        AiResourceImportService importer =
            pluginManager.resolveImporter(source, request.getResourceType());
        AiResourceImportCandidatePage page = importer.search(buildSearchContext(source, request));
        AiResourceImportSearchResponse response = new AiResourceImportSearchResponse();
        response.setSourceId(source.getSourceId());
        response.setResourceType(request.getResourceType());
        response.setItems(toCandidateItems(page == null ? null : page.getItems()));
        if (page != null) {
            response.setNextCursor(page.getNextCursor());
            response.setHasMore(page.isHasMore());
        }
        return response;
    }
    
    /**
     * Validate selected external candidates.
     *
     * @param request validate request
     * @return validate response
     * @throws NacosException if source or plugin cannot be resolved
     */
    public AiResourceImportValidateResponse validate(AiResourceImportValidateRequest request)
        throws NacosException {
        requireRequest(request);
        requireSelectedItems(request.getSelectedItems());
        AiResourceImportSource source =
            sourceManager.resolveSource(request.getSourceId(), request.getResourceType());
        AiResourceImportService importer =
            pluginManager.resolveImporter(source, request.getResourceType());
        AiResourceImportContext context = buildItemContext(source, request.getNamespaceId(),
            request.getResourceType(), request.getOptions());
        List<AiResourceImportValidationItem> items = new ArrayList<>();
        for (AiResourceImportItem each : request.getSelectedItems()) {
            items.add(validateItem(source, importer, context, each, request.isOverwriteExisting()));
        }
        AiResourceImportValidateResponse response = new AiResourceImportValidateResponse();
        response.setSourceId(source.getSourceId());
        response.setResourceType(request.getResourceType());
        response.setItems(items);
        return response;
    }
    
    /**
     * Execute import for selected external candidates.
     *
     * @param request execute request
     * @return execute response
     * @throws NacosException if source or plugin cannot be resolved
     */
    public AiResourceImportExecuteResponse execute(AiResourceImportExecuteRequest request)
        throws NacosException {
        requireRequest(request);
        requireSelectedItems(request.getSelectedItems());
        AiResourceImportSource source =
            sourceManager.resolveSource(request.getSourceId(), request.getResourceType());
        AiResourceImportService importer =
            pluginManager.resolveImporter(source, request.getResourceType());
        AiResourceImportContext context = buildItemContext(source, request.getNamespaceId(),
            request.getResourceType(), request.getOptions());
        List<AiResourceImportResultItem> results = new ArrayList<>();
        for (AiResourceImportItem each : request.getSelectedItems()) {
            results.add(executeItem(source, importer, context, each, request.isOverwriteExisting(),
                request.isSkipInvalid()));
        }
        return buildExecuteResponse(results);
    }
    
    private void requireRequest(AiResourceImportSearchRequest request) throws NacosException {
        if (request == null) {
            throw invalid("AI resource import request must not be null.");
        }
        if (StringUtils.isBlank(request.getResourceType())) {
            throw invalid("AI resource import resource type must not be empty.");
        }
        if (StringUtils.isBlank(request.getSourceId())) {
            throw invalid("AI resource import source id must not be empty.");
        }
    }
    
    private void requireRequest(AiResourceImportValidateRequest request) throws NacosException {
        if (request == null) {
            throw invalid("AI resource import request must not be null.");
        }
        if (StringUtils.isBlank(request.getResourceType())) {
            throw invalid("AI resource import resource type must not be empty.");
        }
        if (StringUtils.isBlank(request.getSourceId())) {
            throw invalid("AI resource import source id must not be empty.");
        }
    }
    
    private void requireRequest(AiResourceImportExecuteRequest request) throws NacosException {
        if (request == null) {
            throw invalid("AI resource import request must not be null.");
        }
        if (StringUtils.isBlank(request.getResourceType())) {
            throw invalid("AI resource import resource type must not be empty.");
        }
        if (StringUtils.isBlank(request.getSourceId())) {
            throw invalid("AI resource import source id must not be empty.");
        }
    }
    
    private void requireSelectedItems(List<AiResourceImportItem> selectedItems)
        throws NacosException {
        if (CollectionUtils.isEmpty(selectedItems)) {
            throw invalid("AI resource import selected items must not be empty.");
        }
    }
    
    private AiResourceImportContext buildSearchContext(AiResourceImportSource source,
        AiResourceImportSearchRequest request) {
        AiResourceImportContext context = buildItemContext(source, request.getNamespaceId(),
            request.getResourceType(), request.getOptions());
        context.setQuery(request.getQuery());
        context.setCursor(request.getCursor());
        context.setLimit(resolveLimit(source, request.getLimit()));
        return context;
    }
    
    private AiResourceImportContext buildItemContext(AiResourceImportSource source,
        String namespaceId, String resourceType, java.util.Map<String, String> options) {
        AiResourceImportContext context = new AiResourceImportContext();
        context.setNamespaceId(StringUtils.isBlank(namespaceId)
            ? com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID : namespaceId);
        context.setResourceType(resourceType);
        context.setSource(source);
        context.setOptions(options);
        return context;
    }
    
    private int resolveLimit(AiResourceImportSource source, Integer requestedLimit) {
        int defaultLimit = source.getMaxItemCount() > 0 ? source.getMaxItemCount() : 100;
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, defaultLimit);
    }
    
    private List<AiResourceImportCandidateItem> toCandidateItems(
        List<AiResourceImportCandidate> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return Collections.emptyList();
        }
        List<AiResourceImportCandidateItem> result = new ArrayList<>(candidates.size());
        for (AiResourceImportCandidate each : candidates) {
            AiResourceImportCandidateItem item = new AiResourceImportCandidateItem();
            item.setExternalId(each.getExternalId());
            item.setName(each.getName());
            item.setVersion(each.getVersion());
            item.setDescription(each.getDescription());
            item.setMetadata(each.getMetadata());
            result.add(item);
        }
        return result;
    }
    
    private AiResourceImportValidationItem validateItem(AiResourceImportSource source,
        AiResourceImportService importer, AiResourceImportContext context,
        AiResourceImportItem item,
        boolean overwriteExisting) {
        try {
            AiResourceImportArtifact artifact = importer.fetch(context, toPluginItem(item));
            securityGuard.checkArtifact(source, context.getResourceType(), artifact);
            AiResourceOperator operator = operatorRegistry.getOperator(artifact.getResourceType());
            AiResourceImportValidationItem result =
                operator.validate(context.getNamespaceId(), artifact, overwriteExisting);
            return result == null ? defaultValidationItem(artifact) : result;
        } catch (NacosException e) {
            return invalidValidationItem(item, e.getErrMsg());
        }
    }
    
    private AiResourceImportResultItem executeItem(AiResourceImportSource source,
        AiResourceImportService importer, AiResourceImportContext context,
        AiResourceImportItem item,
        boolean overwriteExisting, boolean skipInvalid) {
        try {
            AiResourceImportArtifact artifact = importer.fetch(context, toPluginItem(item));
            securityGuard.checkArtifact(source, context.getResourceType(), artifact);
            AiResourceOperator operator = operatorRegistry.getOperator(artifact.getResourceType());
            AiResourceImportResultItem result =
                operator.importResource(context.getNamespaceId(), artifact, overwriteExisting);
            return result == null ? defaultResultItem(artifact) : result;
        } catch (NacosException e) {
            return failedResultItem(item, e.getErrMsg(), skipInvalid);
        }
    }
    
    private com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem toPluginItem(
        AiResourceImportItem item) {
        com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem result =
            new com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem();
        result.setExternalId(item.getExternalId());
        result.setName(item.getName());
        result.setVersion(item.getVersion());
        result.setMetadata(item.getMetadata());
        return result;
    }
    
    private AiResourceImportValidationItem defaultValidationItem(
        AiResourceImportArtifact artifact) {
        AiResourceImportValidationItem result = new AiResourceImportValidationItem();
        result.setExternalId(artifact.getExternalId());
        result.setName(artifact.getName());
        result.setVersion(artifact.getVersion());
        result.setStatus(AiResourceImportValidationStatus.VALID);
        return result;
    }
    
    private AiResourceImportValidationItem invalidValidationItem(AiResourceImportItem item,
        String errorMessage) {
        AiResourceImportValidationItem result = new AiResourceImportValidationItem();
        result.setExternalId(item.getExternalId());
        result.setName(item.getName());
        result.setVersion(item.getVersion());
        result.setStatus(AiResourceImportValidationStatus.INVALID);
        result.setErrors(Collections.singletonList(errorMessage));
        return result;
    }
    
    private AiResourceImportResultItem defaultResultItem(AiResourceImportArtifact artifact) {
        AiResourceImportResultItem result = new AiResourceImportResultItem();
        result.setExternalId(artifact.getExternalId());
        result.setResourceName(artifact.getName());
        result.setVersion(artifact.getVersion());
        result.setStatus(AiResourceImportResultStatus.SUCCESS);
        return result;
    }
    
    private AiResourceImportResultItem failedResultItem(AiResourceImportItem item,
        String errorMessage, boolean skipInvalid) {
        AiResourceImportResultItem result = new AiResourceImportResultItem();
        result.setExternalId(item.getExternalId());
        result.setResourceName(item.getName());
        result.setVersion(item.getVersion());
        result.setStatus(skipInvalid ? AiResourceImportResultStatus.SKIPPED
            : AiResourceImportResultStatus.FAILED);
        result.setErrorMessage(errorMessage);
        return result;
    }
    
    private AiResourceImportExecuteResponse buildExecuteResponse(
        List<AiResourceImportResultItem> results) {
        AiResourceImportExecuteResponse response = new AiResourceImportExecuteResponse();
        response.setResults(results);
        response.setTotalCount(results.size());
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (AiResourceImportResultItem each : results) {
            if (AiResourceImportResultStatus.SUCCESS == each.getStatus()) {
                successCount++;
            } else if (AiResourceImportResultStatus.SKIPPED == each.getStatus()) {
                skippedCount++;
            } else {
                failedCount++;
            }
        }
        response.setSuccessCount(successCount);
        response.setFailedCount(failedCount);
        response.setSkippedCount(skippedCount);
        response.setSuccess(failedCount == 0);
        return response;
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
