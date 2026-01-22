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
import java.util.List;
import java.util.Map;

/**
 * Skill generation request.
 *
 * @author nacos
 */
public class SkillGenerationRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Background information provided by user (required).
     */
    private String backgroundInfo;
    
    /**
     * Selected MCP tools (optional).
     */
    private List<Map<String, Object>> selectedMcpTools;
    
    /**
     * Conversation history (optional).
     * Contains user inputs, tool calls, and model responses.
     * The system will analyze this history to determine if it's suitable
     * for skill generation or optimization.
     */
    private ConversationHistory conversationHistory;
    
    /**
     * Additional parameters.
     */
    private Map<String, Object> params;
    
    public SkillGenerationRequest() {
    }
    
    public String getBackgroundInfo() {
        return backgroundInfo;
    }
    
    public void setBackgroundInfo(String backgroundInfo) {
        this.backgroundInfo = backgroundInfo;
    }
    
    public List<Map<String, Object>> getSelectedMcpTools() {
        return selectedMcpTools;
    }
    
    public void setSelectedMcpTools(List<Map<String, Object>> selectedMcpTools) {
        this.selectedMcpTools = selectedMcpTools;
    }
    
    public Map<String, Object> getParams() {
        return params;
    }
    
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
    
    public ConversationHistory getConversationHistory() {
        return conversationHistory;
    }
    
    public void setConversationHistory(ConversationHistory conversationHistory) {
        this.conversationHistory = conversationHistory;
    }
}
