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

import java.util.List;

import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;

/**
 * AI MCP backend endpoint info.
 *
 * @author xiweng.yy
 */
public class McpEndpointInfo {
    
    /**
     * Indicate the protocol of the endpoint (http / https).
     */
    private String protocol;

    private String address;
    
    private int port;
    
    private String path;

    private List<KeyValueInput> headers;
    
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public List<KeyValueInput> getHeaders() {
        return headers;
    }

    public void setHeaders(List<KeyValueInput> headers) {
        this.headers = headers;
    }
}
