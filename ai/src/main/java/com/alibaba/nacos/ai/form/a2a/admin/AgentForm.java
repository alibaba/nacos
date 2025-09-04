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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;

import java.io.Serial;
import java.util.Objects;

import static com.alibaba.nacos.api.ai.constant.AiConstants.A2a.A2A_DEFAULT_NAMESPACE;
import static com.alibaba.nacos.api.ai.constant.AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;

/**
 * Agent form.
 *
 * @author KiteSoar
 **/
public class AgentForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = -73912927386186928L;
    
    private String namespaceId;
    
    private String name;
    
    private String version;
    
    private String registrationType = A2A_ENDPOINT_TYPE_URL;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
    }
    
    protected void fillDefaultNamespaceId() {
        if (namespaceId == null) {
            namespaceId = A2A_DEFAULT_NAMESPACE;
        }
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
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
        AgentForm agentForm = (AgentForm) o;
        return Objects.equals(namespaceId, agentForm.namespaceId) && Objects.equals(name, agentForm.name)
                && Objects.equals(version, agentForm.version) && Objects.equals(registrationType,
                agentForm.registrationType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, name, version, registrationType);
    }
}
