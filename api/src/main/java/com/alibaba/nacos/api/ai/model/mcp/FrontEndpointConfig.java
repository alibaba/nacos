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

import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;

import java.util.List;

import com.alibaba.nacos.api.ai.constant.AiConstants;

/**
 * Specific endpoint information exposed to the outside.
 *
 * @author OmCheeLin
 */
public class FrontEndpointConfig {
    
    private String type;
    
    private String protocol;
    
    private String endpointType;
    
    /**
     * According To the {@link #endpointType}, the data type will be different.
     * <ul>
     *     <li>If {@link AiConstants.Mcp#MCP_ENDPOINT_TYPE_REF}, the data type is {@link McpServiceRef}</li>
     *     <li>If {@link AiConstants.Mcp#MCP_ENDPOINT_TYPE_DIRECT}, the data type is {@link String}</li>
     *     <li>If {@link AiConstants.Mcp#MCP_FRONT_ENDPOINT_TYPE_TO_BACK}, the data is {@code null}</li>
     * </ul>
     */
    private Object endpointData;
    
    private String path;

    private List<KeyValueInput> headers; 
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getEndpointType() {
        return endpointType;
    }
    
    public void setEndpointType(String endpointType) {
        this.endpointType = endpointType;
    }
    
    public Object getEndpointData() {
        return endpointData;
    }
    
    public void setEndpointData(Object endpointData) {
        this.endpointData = endpointData;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }

    public List<KeyValueInput> getHeaders() {
        return headers;
    }

    public void setHeaders(List<KeyValueInput> headers) {
        this.headers = headers;
    }
}
