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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves canonical MCP Resources from a name and the deprecated MCP compatibility alias.
 *
 * <p>This resolver queries only {@code ai_resource}. It never falls back to Search, serving
 * Manifest, Config, or the historical MCP in-memory index.</p>
 *
 * @author Nacos
 */
@Service
public class McpResourceLocator {
    
    private static final int ID_SCAN_PAGE_SIZE = 100;
    
    private static final String RESOURCE_NAME_FIELD = "name";
    
    private final AiResourcePersistService resourcePersistService;
    
    public McpResourceLocator(AiResourcePersistService resourcePersistService) {
        this.resourcePersistService = resourcePersistService;
    }
    
    /**
     * Resolve exactly one canonical MCP Resource.
     *
     * @param namespaceId namespace identifier; blank uses the MCP default namespace
     * @param mcpName canonical MCP name, optional only for a legacy ID-only request
     * @param mcpId deprecated MCP compatibility alias, optional when name is present
     * @return the uniquely resolved MCP Resource row
     * @throws NacosApiException for invalid input, missing resources, or stored identity conflicts
     */
    public AiResource locate(String namespaceId, String mcpName, String mcpId)
        throws NacosApiException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        boolean hasName = StringUtils.isNotBlank(mcpName);
        boolean hasId = StringUtils.isNotBlank(mcpId);
        if (!hasName && !hasId) {
            throw invalidParameter("Either mcpName or mcpId must be provided", null);
        }
        if (hasId) {
            validateInputMcpId(mcpId);
        }
        if (hasName) {
            AiResource resource = findUniqueByName(normalizedNamespace, mcpName);
            McpResourceExt resourceExt = requireResourceExt(resource);
            if (hasId && !mcpId.equals(resourceExt.getMcpId())) {
                throw invalidParameter("mcpName and mcpId identify different MCP resources", null);
            }
            return resource;
        }
        return findUniqueById(normalizedNamespace, mcpId);
    }
    
    private AiResource findUniqueByName(String namespaceId, String mcpName)
        throws NacosApiException {
        QueryCondition condition = baseCondition(namespaceId);
        condition.putOrGroup(RESOURCE_NAME_FIELD, mcpName);
        Page<AiResource> page = resourcePersistService.list(condition, 1, 2);
        List<AiResource> items = page == null ? null : page.getPageItems();
        if (page == null || CollectionUtils.isEmpty(items)) {
            throw notFound(namespaceId, mcpName, null);
        }
        if (page.getTotalCount() > 1 || items.size() > 1) {
            throw integrityFailure("Multiple MCP Resource rows exist for name " + mcpName, null);
        }
        AiResource result = items.get(0);
        validateReturnedRow(result, namespaceId, mcpName);
        return result;
    }
    
    private AiResource findUniqueById(String namespaceId, String mcpId)
        throws NacosApiException {
        int pageNumber = 1;
        int pagesAvailable = 1;
        AiResource match = null;
        while (pageNumber <= pagesAvailable) {
            Page<AiResource> page = resourcePersistService.list(baseCondition(namespaceId),
                pageNumber, ID_SCAN_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw integrityFailure("Unable to page MCP Resource rows for legacy ID lookup",
                    null);
            }
            pagesAvailable = resolvePagesAvailable(page, pageNumber);
            for (AiResource resource : page.getPageItems()) {
                validateReturnedRow(resource, namespaceId, null);
                McpResourceExt resourceExt = requireResourceExt(resource);
                if (mcpId.equals(resourceExt.getMcpId())) {
                    if (match != null) {
                        throw integrityFailure(
                            "Multiple MCP Resource rows use compatibility mcpId " + mcpId, null);
                    }
                    match = resource;
                }
            }
            pageNumber++;
        }
        if (match == null) {
            throw notFound(namespaceId, null, mcpId);
        }
        return match;
    }
    
    private int resolvePagesAvailable(Page<AiResource> page, int pageNumber) {
        if (page.getPagesAvailable() > 0) {
            return Math.max(pageNumber, page.getPagesAvailable());
        }
        int calculated = (page.getTotalCount() + ID_SCAN_PAGE_SIZE - 1) / ID_SCAN_PAGE_SIZE;
        return Math.max(pageNumber, calculated);
    }
    
    private McpResourceExt requireResourceExt(AiResource resource) throws NacosApiException {
        try {
            return McpResourceExtSerializer.deserialize(resource.getExt());
        } catch (IllegalArgumentException e) {
            throw integrityFailure(
                "MCP Resource " + resource.getName() + " has invalid compatibility identity", e);
        }
    }
    
    private void validateReturnedRow(AiResource resource, String namespaceId, String mcpName)
        throws NacosApiException {
        boolean valid = resource != null
            && namespaceId.equals(resource.getNamespaceId())
            && AiResourceConstants.RESOURCE_TYPE_MCP.equals(resource.getType())
            && (mcpName == null || mcpName.equals(resource.getName()));
        if (!valid) {
            throw integrityFailure("MCP Resource query returned an inconsistent row", null);
        }
    }
    
    private QueryCondition baseCondition(String namespaceId) {
        QueryCondition result = new QueryCondition();
        result.setNamespaceId(namespaceId);
        result.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        return result;
    }
    
    private String normalizeNamespace(String namespaceId) throws NacosApiException {
        String result = StringUtils.isBlank(namespaceId)
            ? AiConstants.Mcp.MCP_DEFAULT_NAMESPACE : namespaceId;
        try {
            AgentValidationUtils.validateNamespaceId(result);
        } catch (IllegalArgumentException e) {
            throw invalidParameter("Invalid MCP namespaceId: " + namespaceId, e);
        }
        return result;
    }
    
    private void validateInputMcpId(String mcpId) throws NacosApiException {
        try {
            McpResourceExtSerializer.validateMcpId(mcpId);
        } catch (IllegalArgumentException e) {
            throw invalidParameter("Invalid MCP compatibility mcpId", e);
        }
    }
    
    private NacosApiException invalidParameter(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, message)
            : new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, cause, message);
    }
    
    private NacosApiException notFound(String namespaceId, String mcpName, String mcpId) {
        String identity = mcpName == null ? "mcpId=" + mcpId : "mcpName=" + mcpName;
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.MCP_SERVER_NOT_FOUND,
            "MCP Resource not found in namespace " + namespaceId + ": " + identity);
    }
    
    private NacosApiException integrityFailure(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message)
            : new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, cause,
                message);
    }
}
