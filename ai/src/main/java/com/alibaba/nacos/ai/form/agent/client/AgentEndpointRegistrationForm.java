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

package com.alibaba.nacos.ai.form.agent.client;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.utils.NamespaceUtil;

import java.io.Serial;
import java.util.List;

/**
 * HTTP form for a complete Agent Endpoint registration batch.
 *
 * @author Nacos
 */
public class AgentEndpointRegistrationForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String agentName;
    
    private String runtimeVersion;
    
    private String versionRange;
    
    private String protocol;
    
    private String endpoints;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Normalize the namespace, parse the Endpoint JSON field, validate this form, and build the
     * RAD registration batch.
     *
     * <p>This method invokes all validation. Callers must not invoke {@link #validate()} before
     * calling it.</p>
     *
     * @return validated complete registration batch
     */
    public AgentEndpointRegistrationBatch toRequest() throws NacosApiException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setNamespaceId(namespaceId);
        result.setAgentName(agentName);
        result.setRuntimeVersion(runtimeVersion);
        result.setVersionRange(versionRange);
        result.setProtocol(protocol);
        result.setEndpoints(AgentClientFormJsonParser.parseOptional("endpoints", endpoints,
            new NacosTypeReference<List<Endpoint>>() {
            }));
        RadModelValidator.validate(result);
        return result;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public String getRuntimeVersion() {
        return runtimeVersion;
    }
    
    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }
    
    public String getVersionRange() {
        return versionRange;
    }
    
    public void setVersionRange(String versionRange) {
        this.versionRange = versionRange;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }
}
