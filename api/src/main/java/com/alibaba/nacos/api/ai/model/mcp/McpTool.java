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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * AI MCP Tool.
 *
 * @author xiweng.yy
 */
public class McpTool {

    private String name;
    
    private String description;
    
    private Map<String, Object> inputSchema;

    private Map<String, Object> outputSchema;

    /**
     * MCP protocol meta field. See MCP specification for `_meta` usage.
     */
    @JsonProperty("_meta")
    private Map<String, Object> meta;

    /**
     * MCP Tool annotations - additional properties describing a Tool to clients.
     */
    private McpToolAnnotations annotations;

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }
    
    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
    }
    
    public Map<String, Object> getMeta() {
        return meta;
    }
    
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
    
    public McpToolAnnotations getAnnotations() {
        return annotations;
    }
    
    public void setAnnotations(McpToolAnnotations annotations) {
        this.annotations = annotations;
    }
    
}
