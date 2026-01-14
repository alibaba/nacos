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

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.List;
import java.util.Objects;

/**
 * Basic info of agent card.
 *
 * @author xiweng.yy
 */
public class AgentCardBasicInfo {
    
    private String protocolVersion;
    
    private String name;
    
    private String description;
    
    private String version;
    
    private String iconUrl;
    
    private AgentCapabilities capabilities;
    
    private List<AgentSkill> skills;
    
    public String getProtocolVersion() {
        return protocolVersion;
    }
    
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
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
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
    
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }
    
    public List<AgentSkill> getSkills() {
        return skills;
    }
    
    public void setSkills(List<AgentSkill> skills) {
        this.skills = skills;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentCardBasicInfo that = (AgentCardBasicInfo) o;
        return Objects.equals(protocolVersion, that.protocolVersion) && Objects.equals(name, that.name)
                && Objects.equals(description, that.description) && Objects.equals(version, that.version)
                && Objects.equals(iconUrl, that.iconUrl) && Objects.equals(capabilities, that.capabilities)
                && Objects.equals(skills, that.skills);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(protocolVersion, name, description, version, iconUrl, capabilities, skills);
    }
}
