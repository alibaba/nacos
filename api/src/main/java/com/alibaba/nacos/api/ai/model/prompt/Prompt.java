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

package com.alibaba.nacos.api.ai.model.prompt;

import java.io.Serializable;
import java.util.Map;

/**
 * Prompt entity for AI Prompt management.
 *
 * <p>Prompt is stored as a Nacos configuration with fixed group "nacos-ai-prompt"
 * and dataId "{promptKey}.json". The content is stored as JSON format.</p>
 *
 * @author nacos
 */
public class Prompt implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Prompt key (unique identifier within namespace).
     */
    private String promptKey;
    
    /**
     * Prompt version in format "major.minor.patch" (e.g., "1.0.0").
     */
    private String version;
    
    /**
     * Prompt template content.
     */
    private String template;
    
    /**
     * MD5 hash of the prompt content (for CAS operations).
     */
    private String md5;
    
    public Prompt() {
    }
    
    public Prompt(String promptKey, String version, String template) {
        this.promptKey = promptKey;
        this.version = version;
        this.template = template;
    }
    
    public String getPromptKey() {
        return promptKey;
    }
    
    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getTemplate() {
        return template;
    }
    
    public void setTemplate(String template) {
        this.template = template;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    /**
     * Render the prompt template by replacing variables with provided values.
     *
     * <p>Variables in the template are specified using {{variableName}} syntax.
     * This method replaces each {{variableName}} with the corresponding value
     * from the provided map.</p>
     *
     * <p>Example:
     * <pre>
     * Prompt prompt = new Prompt("greeting", "1.0.0", "Hello {{name}}, welcome to {{place}}!");
     * Map&lt;String, String&gt; variables = new HashMap&lt;&gt;();
     * variables.put("name", "Alice");
     * variables.put("place", "Nacos");
     * String result = prompt.render(variables);
     * // Result: "Hello Alice, welcome to Nacos!"
     * </pre>
     * </p>
     *
     * @param variables map of variable names to their values (key: variable name, value: replacement value)
     * @return rendered prompt content with variables replaced, or the original template if variables is null
     */
    public String render(Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    @Override
    public String toString() {
        return "Prompt{" + "promptKey='" + promptKey + '\'' + ", version='" + version + '\'' + '}';
    }
}
