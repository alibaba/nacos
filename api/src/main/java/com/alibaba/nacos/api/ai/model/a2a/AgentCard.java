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
import java.util.Map;
import java.util.Objects;

/**
 * AgentCard.
 *
 * @author KiteSoar
 */
public class AgentCard {
    
    private String protocolVersion;
    
    private String name;
    
    private String description;
    
    private String url;
    
    private String preferredTransport;
    
    private List<AgentInterface> additionalInterfaces;
    
    private String iconUrl;
    
    private AgentProvider provider;
    
    private String version;
    
    private String documentationUrl;
    
    private AgentCapabilities capabilities;
    
    private Map<String, SecurityScheme> securitySchemes;
    
    private List<Map<String, List<String>>> security;
    
    private List<String> defaultInputModes;
    
    private List<String> defaultOutputModes;
    
    private List<AgentSkill> skills;
    
    private Boolean supportsAuthenticatedExtendedCard;
    
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
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getPreferredTransport() {
        return preferredTransport;
    }
    
    public void setPreferredTransport(String preferredTransport) {
        this.preferredTransport = preferredTransport;
    }
    
    public List<AgentInterface> getAdditionalInterfaces() {
        return additionalInterfaces;
    }
    
    public void setAdditionalInterfaces(List<AgentInterface> additionalInterfaces) {
        this.additionalInterfaces = additionalInterfaces;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
    
    public AgentProvider getProvider() {
        return provider;
    }
    
    public void setProvider(AgentProvider provider) {
        this.provider = provider;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDocumentationUrl() {
        return documentationUrl;
    }
    
    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }
    
    public AgentCapabilities getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }
    
    public Map<String, SecurityScheme> getSecuritySchemes() {
        return securitySchemes;
    }
    
    public void setSecuritySchemes(Map<String, SecurityScheme> securitySchemes) {
        this.securitySchemes = securitySchemes;
    }
    
    public List<Map<String, List<String>>> getSecurity() {
        return security;
    }
    
    public void setSecurity(List<Map<String, List<String>>> security) {
        this.security = security;
    }
    
    public List<String> getDefaultInputModes() {
        return defaultInputModes;
    }
    
    public void setDefaultInputModes(List<String> defaultInputModes) {
        this.defaultInputModes = defaultInputModes;
    }
    
    public List<String> getDefaultOutputModes() {
        return defaultOutputModes;
    }
    
    public void setDefaultOutputModes(List<String> defaultOutputModes) {
        this.defaultOutputModes = defaultOutputModes;
    }
    
    public List<AgentSkill> getSkills() {
        return skills;
    }
    
    public void setSkills(List<AgentSkill> skills) {
        this.skills = skills;
    }
    
    public Boolean getSupportsAuthenticatedExtendedCard() {
        return supportsAuthenticatedExtendedCard;
    }
    
    public void setSupportsAuthenticatedExtendedCard(Boolean supportsAuthenticatedExtendedCard) {
        this.supportsAuthenticatedExtendedCard = supportsAuthenticatedExtendedCard;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentCard agentCard = (AgentCard) o;
        return Objects.equals(protocolVersion, agentCard.protocolVersion) && Objects.equals(name, agentCard.name)
                && Objects.equals(description, agentCard.description) && Objects.equals(url, agentCard.url)
                && Objects.equals(preferredTransport, agentCard.preferredTransport) && Objects.equals(
                additionalInterfaces, agentCard.additionalInterfaces) && Objects.equals(iconUrl, agentCard.iconUrl)
                && Objects.equals(provider, agentCard.provider) && Objects.equals(version, agentCard.version)
                && Objects.equals(documentationUrl, agentCard.documentationUrl) && Objects.equals(capabilities,
                agentCard.capabilities) && Objects.equals(securitySchemes, agentCard.securitySchemes) && Objects.equals(
                security, agentCard.security) && Objects.equals(defaultInputModes, agentCard.defaultInputModes)
                && Objects.equals(defaultOutputModes, agentCard.defaultOutputModes) && Objects.equals(skills,
                agentCard.skills) && Objects.equals(supportsAuthenticatedExtendedCard,
                agentCard.supportsAuthenticatedExtendedCard);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(protocolVersion, name, description, url, preferredTransport, additionalInterfaces, iconUrl,
                provider, version, documentationUrl, capabilities, securitySchemes, security, defaultInputModes,
                defaultOutputModes, skills, supportsAuthenticatedExtendedCard);
    }
}
