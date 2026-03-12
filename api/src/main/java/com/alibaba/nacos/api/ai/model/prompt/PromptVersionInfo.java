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

import java.util.List;

/**
 * Prompt version information.
 *
 * @author nacos
 */
public class PromptVersionInfo extends PromptVersionSummary {
    
    private static final long serialVersionUID = 1L;
    
    private String template;
    
    private String md5;
    
    private List<PromptVariable> variables;
    
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
    
    public List<PromptVariable> getVariables() {
        return variables;
    }
    
    public void setVariables(List<PromptVariable> variables) {
        this.variables = variables;
    }
}
