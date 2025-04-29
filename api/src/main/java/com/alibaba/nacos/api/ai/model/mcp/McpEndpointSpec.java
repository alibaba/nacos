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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.constant.AiConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * AI MCP Server Endpoint Specification.
 *
 * @author xiweng.yy
 */
public class McpEndpointSpec {
    
    /**
     * Endpoint type. Should be {@link AiConstants.Mcp#MCP_ENDPOINT_TYPE_DIRECT} or
     * {@link AiConstants.Mcp#MCP_ENDPOINT_TYPE_REF}.
     */
    private String type;
    
    /**
     * Endpoint data. Depend on the `type`, the data should be different.
     * <p>
     *  If `type` is {@link AiConstants.Mcp#MCP_ENDPOINT_TYPE_DIRECT}, the data should be include `address` and `port` to
     *  spec mcp server endpoint.
     * </p>
     * <p>
     *  If `type` is {@link AiConstants.Mcp#MCP_ENDPOINT_TYPE_REF}, the data should be include `namespaceId`, `groupName` and `serviceName`
     *  to spec the ref server which already register into Nacos.
     * </p>
     */
    private Map<String, String> data = new HashMap<>();
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Map<String, String> getData() {
        return data;
    }
    
    public void setData(Map<String, String> data) {
        this.data = data;
    }
}
