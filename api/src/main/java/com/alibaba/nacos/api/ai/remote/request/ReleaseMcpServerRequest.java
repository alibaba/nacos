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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;

/**
 * Nacos AI module release new mcp server or new version of exist mcp server request.
 *
 * <p>
 *     If mcp server is not exist, request will create an new mcp server with parameter specification.
 *     If mcp server is exist, but version in specification is new one, request will create a new version of mcp server.
 *     If mcp server is exist, and version in specification is exist, request will do nothing.
 * </p>
 *
 * @author xiweng.yy
 */
public class ReleaseMcpServerRequest extends AbstractMcpRequest {
    
    private McpServerBasicInfo serverSpecification;
    
    private McpToolSpecification toolSpecification;
    
    private McpEndpointSpec endpointSpecification;
    
    public McpServerBasicInfo getServerSpecification() {
        return serverSpecification;
    }
    
    public void setServerSpecification(McpServerBasicInfo serverSpecification) {
        this.serverSpecification = serverSpecification;
    }
    
    public McpToolSpecification getToolSpecification() {
        return toolSpecification;
    }
    
    public void setToolSpecification(McpToolSpecification toolSpecification) {
        this.toolSpecification = toolSpecification;
    }
    
    public McpEndpointSpec getEndpointSpecification() {
        return endpointSpecification;
    }
    
    public void setEndpointSpecification(McpEndpointSpec endpointSpecification) {
        this.endpointSpecification = endpointSpecification;
    }
}
