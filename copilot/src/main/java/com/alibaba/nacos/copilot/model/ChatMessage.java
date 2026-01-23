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

/**
 * Chat message.
 *
 * @author nacos
 */
public class ChatMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Message role: system, user, assistant.
     */
    private String role;
    
    /**
     * Message content.
     */
    private String content;
    
    public ChatMessage() {
    }
    
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
}
