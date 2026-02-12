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

import com.alibaba.nacos.api.ai.model.skills.Skill;

import java.io.Serializable;
import java.util.Map;

/**
 * Skill optimization request.
 *
 * @author nacos
 */
public class SkillOptimizationRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Original Skill (required, frontend should get complete Skill first).
     */
    private Skill skill;
    
    /**
     * Optimization goal (optional, user input optimization direction or requirement).
     */
    private String optimizationGoal;
    
    /**
     * Conversation history (optional).
     * Contains user inputs, tool calls, and model responses.
     * The system will analyze this history to determine if it's suitable
     * for skill optimization and what optimizations should be made.
     */
    private ConversationHistory conversationHistory;
    
    /**
     * Target file name to optimize (optional).
     * If specified, only optimize the content of this specific file.
     * If not specified, optimize the entire Skill.
     */
    private String targetFileName;
    
    /**
     * Additional parameters.
     */
    private Map<String, Object> params;
    
    public SkillOptimizationRequest() {
    }
    
    public Skill getSkill() {
        return skill;
    }
    
    public void setSkill(Skill skill) {
        this.skill = skill;
    }
    
    public String getOptimizationGoal() {
        return optimizationGoal;
    }
    
    public void setOptimizationGoal(String optimizationGoal) {
        this.optimizationGoal = optimizationGoal;
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
    
    public String getTargetFileName() {
        return targetFileName;
    }
    
    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }
}
