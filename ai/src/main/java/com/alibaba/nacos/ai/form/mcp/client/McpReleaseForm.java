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

package com.alibaba.nacos.ai.form.mcp.client;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Form for MCP compatibility release and optional lifecycle draft creation.
 *
 * @author Nacos
 */
public class McpReleaseForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String mcpName;
    
    private String serverSpecification;
    
    private String toolSpecification;
    
    private String resourceSpecification;
    
    private String endpointSpecification;
    
    private String createDraft = Boolean.FALSE.toString();
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Parse complex JSON fields and build the release request.
     *
     * @return validated release request
     * @throws NacosApiException when Form JSON, identity, or boolean input is invalid
     */
    public ReleaseMcpServerRequest toRequest() throws NacosApiException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        McpServerBasicInfo server = McpClientFormJsonParser.parseOptional(
            "serverSpecification", serverSpecification,
            new NacosTypeReference<McpServerBasicInfo>() {
            });
        if (server == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Required parameter `serverSpecification` is not present.");
        }
        if (StringUtils.isNotBlank(mcpName) && !mcpName.equals(server.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Request parameter `mcpName` must match `serverSpecification.name`.");
        }
        ReleaseMcpServerRequest result = new ReleaseMcpServerRequest();
        result.setNamespaceId(namespaceId);
        result.setMcpName(server.getName());
        result.setServerSpecification(server);
        result.setToolSpecification(McpClientFormJsonParser.parseOptional("toolSpecification",
            toolSpecification, new NacosTypeReference<McpToolSpecification>() {
            }));
        result.setResourceSpecification(McpClientFormJsonParser.parseOptional(
            "resourceSpecification", resourceSpecification,
            new NacosTypeReference<McpResourceSpecification>() {
            }));
        result.setEndpointSpecification(McpClientFormJsonParser.parseOptional(
            "endpointSpecification", endpointSpecification,
            new NacosTypeReference<McpEndpointSpec>() {
            }));
        result.setCreateDraft(parseCreateDraft());
        return result;
    }
    
    private boolean parseCreateDraft() throws NacosApiException {
        if (Boolean.TRUE.toString().equalsIgnoreCase(createDraft)) {
            return true;
        }
        if (Boolean.FALSE.toString().equalsIgnoreCase(createDraft)) {
            return false;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Request parameter `createDraft` must be `true` or `false`.");
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getMcpName() {
        return mcpName;
    }
    
    public void setMcpName(String mcpName) {
        this.mcpName = mcpName;
    }
    
    public String getServerSpecification() {
        return serverSpecification;
    }
    
    public void setServerSpecification(String serverSpecification) {
        this.serverSpecification = serverSpecification;
    }
    
    public String getToolSpecification() {
        return toolSpecification;
    }
    
    public void setToolSpecification(String toolSpecification) {
        this.toolSpecification = toolSpecification;
    }
    
    public String getResourceSpecification() {
        return resourceSpecification;
    }
    
    public void setResourceSpecification(String resourceSpecification) {
        this.resourceSpecification = resourceSpecification;
    }
    
    public String getEndpointSpecification() {
        return endpointSpecification;
    }
    
    public void setEndpointSpecification(String endpointSpecification) {
        this.endpointSpecification = endpointSpecification;
    }
    
    public String getCreateDraft() {
        return createDraft;
    }
    
    public void setCreateDraft(String createDraft) {
        this.createDraft = createDraft;
    }
}
