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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Mcp Tool specification.
 *
 * @author xiweng.yy
 */
public class McpToolSpecification {

    private List<McpTool> tools = new LinkedList<>();
    
    private Map<String, McpToolMeta> toolsMeta = new HashMap<>(1);
    
    public List<McpTool> getTools() {
        return tools;
    }
    
    public void setTools(List<McpTool> tools) {
        this.tools = tools;
    }
    
    public Map<String, McpToolMeta> getToolsMeta() {
        return toolsMeta;
    }
    
    public void setToolsMeta(Map<String, McpToolMeta> toolsMeta) {
        this.toolsMeta = toolsMeta;
    }
}
