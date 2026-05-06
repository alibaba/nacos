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
 * Prompt optimization form.
 *
 * @author nacos
 */
public class PromptOptimizationForm {
    
    /**
     * Original Prompt content (required).
     */
    private String prompt;
    
    /**
     * Optimization goal/requirement description (optional).
     * e.g., "Make response more concise", "Add more examples", "Support multi-language"
     */
    private String optimizationGoal;
    
    /**
     * Validate form data.
     */
    public void validate() {
        if (StringUtils.isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt is required");
        }
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getOptimizationGoal() {
        return optimizationGoal;
    }
    
    public void setOptimizationGoal(String optimizationGoal) {
        this.optimizationGoal = optimizationGoal;
    }
}
