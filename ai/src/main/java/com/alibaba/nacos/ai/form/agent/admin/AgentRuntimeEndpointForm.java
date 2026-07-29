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

package com.alibaba.nacos.ai.form.agent.admin;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.api.NacosApiException;

/**
 * Query for one protocol's Runtime Endpoint snapshot.
 *
 * @author Nacos
 */
public class AgentRuntimeEndpointForm extends AgentAdminForm {
    
    private static final long serialVersionUID = 1L;
    
    private String protocol;
    
    private String version;
    
    @Override
    public void validate() throws NacosApiException {
        super.validate();
        AgentValidationUtils.validateProtocol(protocol);
        if (version != null) {
            AgentValidationUtils.validateVersion(version);
        }
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
}
