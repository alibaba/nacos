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
public class Skill extends SkillBase {
    
    /**
     * Full SKILL.md content.
     */
    private String skillMd;
    
    /**
     * Resource map (note: singular resource, key is resource name).
     */
    private Map<String, SkillResource> resource;
    
    public String getSkillMd() {
        return skillMd;
    }
    
    public void setSkillMd(String skillMd) {
        this.skillMd = skillMd;
    }
    
    public Map<String, SkillResource> getResource() {
        return resource;
    }
    
    public void setResource(Map<String, SkillResource> resource) {
        this.resource = resource;
    }
}
