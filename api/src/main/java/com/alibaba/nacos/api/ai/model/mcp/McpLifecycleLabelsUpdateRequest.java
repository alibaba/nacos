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

package com.alibaba.nacos.api.ai.model.mcp;

import java.io.Serializable;
import java.util.Map;

/**
 * Complete replacement request for one MCP resource's custom Version labels.
 *
 * @author Nacos
 */
public class McpLifecycleLabelsUpdateRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String mcpName;
    
    private Map<String, String> labels;
    
    public String getMcpName() {
        return mcpName;
    }
    
    public void setMcpName(String mcpName) {
        this.mcpName = mcpName;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
