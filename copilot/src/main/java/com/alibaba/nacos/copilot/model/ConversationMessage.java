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

package com.alibaba.nacos.copilot.model;

import java.io.Serializable;
import java.util.Map;

/**
 * Conversation message in the conversation history.
 * Represents a single message in a conversation, which can be:
 * - User input
 * - Tool call
 * - Model response
 *
 * @author nacos
 */
public class ConversationMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Message type: "user", "tool_call", "model", etc.
     */
    private String type;
    
    /**
     * Message content.
     */
    private String content;
    
    /**
     * Tool name (if type is "tool_call").
     */
    private String toolName;
    
    /**
     * Tool input parameters (if type is "tool_call").
     */
    private Map<String, Object> toolInput;
    
    /**
     * Tool output/result (if type is "tool_call").
     */
    private Object toolOutput;
    
    /**
     * Timestamp of the message (optional).
     */
    private Long timestamp;
    
    /**
     * Additional metadata (optional).
     */
    private Map<String, Object> metadata;
    
    public ConversationMessage() {
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }
    
    public Map<String, Object> getToolInput() {
        return toolInput;
    }
    
    public void setToolInput(Map<String, Object> toolInput) {
        this.toolInput = toolInput;
    }
    
    public Object getToolOutput() {
        return toolOutput;
    }
    
    public void setToolOutput(Object toolOutput) {
        this.toolOutput = toolOutput;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
