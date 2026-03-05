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

package com.alibaba.nacos.copilot.form;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Prompt debug form.
 *
 * @author nacos
 */
public class PromptDebugForm {
    
    /**
     * System Prompt content (required).
     * This is the prompt template that defines the AI's behavior.
     */
    private String prompt;
    
    /**
     * User input content (required).
     * This is the user's message to test with the prompt.
     */
    private String userInput;
    
    /**
     * Validate form data.
     */
    public void validate() {
        if (StringUtils.isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt is required");
        }
        if (StringUtils.isBlank(userInput)) {
            throw new IllegalArgumentException("User input is required");
        }
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getUserInput() {
        return userInput;
    }
    
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }
}
