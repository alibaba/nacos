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

/**
 * AI MCP server remote service config.
 *
 * @author xiweng.yy
 */
public class McpServerRemoteServiceConfig {
    
    private McpServiceRef serviceRef;
    
    private String exportPath;
    
    private List<FrontEndpointConfig> frontEndpointConfigList;
    
    public McpServiceRef getServiceRef() {
        return serviceRef;
    }
    
    public void setServiceRef(McpServiceRef serviceRef) {
        this.serviceRef = serviceRef;
    }
    
    public String getExportPath() {
        return exportPath;
    }
    
    public void setExportPath(String exportPath) {
        this.exportPath = exportPath;
    }
    
    public List<FrontEndpointConfig> getFrontEndpointConfigList() {
        return frontEndpointConfigList;
    }
    
    public void setFrontEndpointConfigList(List<FrontEndpointConfig> frontEndpointConfigList) {
        this.frontEndpointConfigList = frontEndpointConfigList;
    }
}
