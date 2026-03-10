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

/**
 * Chat request for LLM.
 *
 * @author nacos
 */
public class ChatRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Session ID.
     */
    private String sessionId;
    
    /**
     * User message.
     */
    private String message;
    
    /**
     * Context information (optional).
     */
    private String context;
    
    /**
     * Conversation history.
     */
    private List<ChatMessage> history;
    
    /**
     * Whether to use stream response.
     */
    private boolean stream;
    
    /**
     * System prompt (optional, used in optimization scenarios).
     */
    private String systemPrompt;
    
    public ChatRequest() {
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getContext() {
        return context;
    }
    
    public void setContext(String context) {
        this.context = context;
    }
    
    public List<ChatMessage> getHistory() {
        return history;
    }
    
    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }
    
    public boolean isStream() {
        return stream;
    }
    
    public void setStream(boolean stream) {
        this.stream = stream;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
