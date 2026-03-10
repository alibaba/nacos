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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.common.notify.Event;

/**
 * Prompt changed event for internal notification.
 *
 * @author nacos
 */
public class PromptChangedEvent extends Event {
    
    private static final long serialVersionUID = 1L;
    
    private final String promptKey;
    
    private final String cacheKey;
    
    private final Prompt prompt;
    
    public PromptChangedEvent(String promptKey, String cacheKey, Prompt prompt) {
        this.promptKey = promptKey;
        this.cacheKey = cacheKey;
        this.prompt = prompt;
    }
    
    public String getPromptKey() {
        return promptKey;
    }
    
    public Prompt getPrompt() {
        return prompt;
    }
    
    public String getCacheKey() {
        return cacheKey;
    }
}
