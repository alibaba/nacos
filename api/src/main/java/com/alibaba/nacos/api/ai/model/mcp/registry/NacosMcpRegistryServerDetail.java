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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;

/**
 * NacosMcpRegistryServerDetail.
 * @author xinluo
 */
public class NacosMcpRegistryServerDetail extends McpRegistryServerDetail {

    private McpEndpointSpec nacosMcpEndpointSpec;

    private McpToolSpecification mcpToolSpecification;

    private String nacosNamespaceId;

    public McpEndpointSpec getNacosMcpEndpointSpec() {
        return nacosMcpEndpointSpec;
    }

    public void setNacosMcpEndpointSpec(McpEndpointSpec nacosMcpEndpointSpec) {
        this.nacosMcpEndpointSpec = nacosMcpEndpointSpec;
    }

    public String getNacosNamespaceId() {
        return nacosNamespaceId;
    }

    public void setNacosNamespaceId(String nacosNamespaceId) {
        this.nacosNamespaceId = nacosNamespaceId;
    }

    public McpToolSpecification getMcpToolSpecification() {
        return mcpToolSpecification;
    }

    public void setMcpToolSpecification(McpToolSpecification mcpToolSpecification) {
        this.mcpToolSpecification = mcpToolSpecification;
    }
}
