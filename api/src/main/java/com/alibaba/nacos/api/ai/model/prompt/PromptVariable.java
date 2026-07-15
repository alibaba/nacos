/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.prompt;

import java.io.Serializable;

/**
 * Prompt variable definition with optional default value.
 *
 * <p>Represents a variable placeholder (e.g., {{variableName}}) in a prompt template,
 * along with its optional default value and description.</p>
 *
 * @author nacos
 */
public class PromptVariable implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Variable name (matches the placeholder name in template, e.g., "question" for {{question}}).
     */
    private String name;
    
    /**
     * Default value for this variable. Null means the variable has no default (considered required).
     */
    private String defaultValue;
    
    /**
     * Optional description explaining the purpose or expected content of this variable.
     */
    private String description;
    
    public PromptVariable() {
    }
    
    public PromptVariable(String name, String defaultValue, String description) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "PromptVariable{" + "name='" + name + '\'' + ", defaultValue='" + defaultValue + '\''
            + ", description='"
            + description + '\'' + '}';
    }
}
