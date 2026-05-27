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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.api.ai.model.skills.Skill;

/**
 * Result wrapper returned by the skill listener path. It carries the resolved {@link Skill}, its
 * published content MD5 and the resolved version string so that the controller can populate the
 * listener-related response headers without re-parsing the manifest.
 *
 * @author nacos
 * @since 3.2.0
 */
public class SkillQueryResult {
    
    private final Skill skill;
    
    private final String md5;
    
    private final String resolvedVersion;
    
    public SkillQueryResult(Skill skill, String md5, String resolvedVersion) {
        this.skill = skill;
        this.md5 = md5;
        this.resolvedVersion = resolvedVersion;
    }
    
    public Skill getSkill() {
        return skill;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public String getResolvedVersion() {
        return resolvedVersion;
    }
}
