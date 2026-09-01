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

import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Form for MCP Runtime Endpoint registration and deregistration.
 *
 * @author Nacos
 */
public class McpEndpointForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String mcpName;
    
    private String address;
    
    private int port;
    
    private String version;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest(null);
    }
    
    /**
     * Convert this form to the transport-neutral MCP endpoint request.
     *
     * @param type endpoint operation type
     * @return MCP endpoint request
     * @throws NacosApiException if the form is invalid
     */
    public McpServerEndpointRequest toRequest(String type) throws NacosApiException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING, "parameters `mcpName` can't be empty or null");
        }
        McpServerEndpointRequest result = new McpServerEndpointRequest();
        result.setNamespaceId(namespaceId);
        result.setMcpName(mcpName);
        result.setAddress(address);
        result.setPort(port);
        result.setVersion(version);
        result.setType(type);
        return result;
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
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
}
