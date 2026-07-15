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

/**
 * Skill generation response.
 *
 * @author nacos
 */
public class SkillGenerationResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Response type: thinking, tool_call, content, done.
     */
    private StreamResponseType type;
    
    /**
     * Content fragment (used in stream response).
     */
    private String chunk;
    
    /**
     * Generated Skill (included when type is done).
     */
    private Skill skill;
    
    /**
     * Generation explanation (included when type is done).
     */
    private String explanation;
    
    /**
     * Whether the response is complete.
     */
    private boolean done;
    
    public SkillGenerationResponse() {
    }
    
    public StreamResponseType getType() {
        return type;
    }
    
    public void setType(StreamResponseType type) {
        this.type = type;
    }
    
    public String getChunk() {
        return chunk;
    }
    
    public void setChunk(String chunk) {
        this.chunk = chunk;
    }
    
    public Skill getSkill() {
        return skill;
    }
    
    public void setSkill(Skill skill) {
        this.skill = skill;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public boolean isDone() {
        return done;
    }
    
    public void setDone(boolean done) {
        this.done = done;
    }
}
