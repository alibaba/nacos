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

package com.alibaba.nacos.api.ai.model.skills;

import java.util.Map;

/**
 * Claude Skill Resource - 资源结构
 *
 * @author nacos
 */
public class SkillResource {
    
    /**
     * 资源唯一ID
     */
    private String resourceId;
    
    /**
     * 资源名称（包含文件后缀，如：config_check_template.json）
     */
    private String name;
    
    /**
     * 资源类型：template, data, script, etc.
     */
    private String type;
    
    /**
     * 资源内容（字符串格式，从独立配置中读取）
     */
    private String content;
    
    /**
     * 资源元数据（可选）
     */
    private Map<String, Object> metadata;
    
    public String getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
