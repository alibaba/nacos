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
 * Claude Skill entity for independent Skills management.
 * Simplified structure with core fields only.
 *
 * @author nacos
 */
public class Skill {
    
    /**
     * 命名空间ID（Nacos 管理字段）
     */
    private String namespaceId;
    
    /**
     * Skill 唯一ID（系统生成）
     */
    private String skillId;
    
    /**
     * Skill 名称
     */
    private String name;
    
    /**
     * Skill 描述
     */
    private String description;
    
    /**
     * Claude 指令（注意：单数 instruction）
     */
    private String instruction;
    
    /**
     * 资源映射（注意：单数 resource，key 为 resource name）
     */
    private Map<String, SkillResource> resource;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getSkillId() {
        return skillId;
    }
    
    public void setSkillId(String skillId) {
        this.skillId = skillId;
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
    
    public String getInstruction() {
        return instruction;
    }
    
    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }
    
    public Map<String, SkillResource> getResource() {
        return resource;
    }
    
    public void setResource(Map<String, SkillResource> resource) {
        this.resource = resource;
    }
}
