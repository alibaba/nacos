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

package com.alibaba.nacos.ai.form.a2a.admin;

import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentProvider;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.ai.model.a2a.SecurityScheme;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.StringUtils;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.api.ai.constant.AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;

/**
 * AgentDetailForm.
 *
 * @author KiteSoar
 */
public class AgentDetailForm extends AgentForm {
    
    @Serial
    private static final long serialVersionUID = -5976517179565329425L;
    
    private String description;
    
    private String protocolVersion;
    
    private String url;
    
    private String preferredTransport;
    
    private List<AgentInterface> additionalInterfaces;
    
    private String iconUrl;
    
    private AgentProvider provider;
    
    private String documentationUrl;
    
    private AgentCapabilities capabilities;
    
    private Map<String, SecurityScheme> securitySchemes;
    
    private List<Map<String, List<String>>> security;
    
    private List<String> defaultInputModes;
    
    private List<String> defaultOutputModes;
    
    private List<AgentSkill> skills;
    
    private Boolean supportsAuthenticatedExtendedCard;
    
    private String registrationType = A2A_ENDPOINT_TYPE_URL;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        
        if (StringUtils.isEmpty(super.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'name' type String is not present");
        }
    }
    
    public String getProtocolVersion() {
        return protocolVersion;
    }
    
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getRegistrationType() {
        return registrationType;
    }
    
    public void setRegistrationType(String registrationType) {
        this.registrationType = registrationType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AgentDetailForm that = (AgentDetailForm) o;
        return Objects.equals(description, that.description) && Objects.equals(protocolVersion, that.protocolVersion)
                && Objects.equals(url, that.url) && Objects.equals(preferredTransport, that.preferredTransport)
                && Objects.equals(additionalInterfaces, that.additionalInterfaces) && Objects.equals(iconUrl,
                that.iconUrl) && Objects.equals(provider, that.provider) && Objects.equals(documentationUrl,
                that.documentationUrl) && Objects.equals(capabilities, that.capabilities) && Objects.equals(
                securitySchemes, that.securitySchemes) && Objects.equals(security, that.security) && Objects.equals(
                defaultInputModes, that.defaultInputModes) && Objects.equals(defaultOutputModes,
                that.defaultOutputModes) && Objects.equals(skills, that.skills) && Objects.equals(
                supportsAuthenticatedExtendedCard, that.supportsAuthenticatedExtendedCard) && Objects.equals(
                registrationType, that.registrationType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, protocolVersion, url, preferredTransport,
                additionalInterfaces, iconUrl, provider, documentationUrl, capabilities, securitySchemes, security,
                defaultInputModes, defaultOutputModes, skills, supportsAuthenticatedExtendedCard,
                registrationType);
    }
}
