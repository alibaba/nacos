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
 * Conversation history containing a sequence of messages.
 * This represents a complete conversation that may be suitable for
 * skill generation or skill optimization.
 *
 * @author nacos
 */
public class ConversationHistory implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * List of conversation messages in chronological order.
     */
    private List<ConversationMessage> messages;
    
    /**
     * Conversation context or summary (optional).
     */
    private String context;
    
    /**
     * Conversation title or topic (optional).
     */
    private String title;
    
    public ConversationHistory() {
    }
    
    public List<ConversationMessage> getMessages() {
        return messages;
    }
    
    public void setMessages(List<ConversationMessage> messages) {
        this.messages = messages;
    }
    
    public String getContext() {
        return context;
    }
    
    public void setContext(String context) {
        this.context = context;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
}
