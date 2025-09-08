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
 *
 */

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.List;
import java.util.Objects;

/**
 * AgentSkill.
 *
 * @author KiteSoar
 */
public class AgentSkill {
    
    private String id;
    
    private String name;
    
    private String description;
    
    private List<String> tags;
    
    private List<String> examples;
    
    private List<String> inputModes;
    
    private List<String> outputModes;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public List<String> getExamples() {
        return examples;
    }
    
    public void setExamples(List<String> examples) {
        this.examples = examples;
    }
    
    public List<String> getInputModes() {
        return inputModes;
    }
    
    public void setInputModes(List<String> inputModes) {
        this.inputModes = inputModes;
    }
    
    public List<String> getOutputModes() {
        return outputModes;
    }
    
    public void setOutputModes(List<String> outputModes) {
        this.outputModes = outputModes;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentSkill that = (AgentSkill) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(description,
                that.description) && Objects.equals(tags, that.tags) && Objects.equals(examples, that.examples)
                && Objects.equals(inputModes, that.inputModes) && Objects.equals(outputModes, that.outputModes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, tags, examples, inputModes, outputModes);
    }
}
