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

/**
 * Prompt optimization request.
 *
 * @author nacos
 */
public class PromptOptimizationRequest {
    
    /**
     * Original Prompt content.
     */
    private String prompt;
    
    /**
     * Optimization goal/requirement description.
     */
    private String optimizationGoal;
    
    public PromptOptimizationRequest() {
    }
    
    public PromptOptimizationRequest(String prompt, String optimizationGoal) {
        this.prompt = prompt;
        this.optimizationGoal = optimizationGoal;
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
