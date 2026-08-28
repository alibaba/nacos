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

package com.alibaba.nacos.ai.form.mcp.client;

import com.alibaba.nacos.ai.form.search.client.AiResourcePageSearchForm;
import com.alibaba.nacos.api.exception.api.NacosApiException;

import java.util.List;

/**
 * MCP resource-specific Search form.
 *
 * @author Nacos
 */
public class McpSearchForm extends AiResourcePageSearchForm {
    
    private static final long serialVersionUID = 1L;
    
    private List<String> protocolsAny;
    
    private List<String> capabilitiesAny;
    
    @Override
    public void validate() throws NacosApiException {
        super.validate();
        protocolsAny = normalize(protocolsAny, MAX_FILTER_VALUES, "protocolsAny");
        capabilitiesAny = normalize(capabilitiesAny, MAX_FILTER_VALUES, "capabilitiesAny");
    }
    
    public List<String> getProtocolsAny() {
        return protocolsAny;
    }
    
    public void setProtocolsAny(List<String> protocolsAny) {
        this.protocolsAny = protocolsAny;
    }
    
    public List<String> getCapabilitiesAny() {
        return capabilitiesAny;
    }
    
    public void setCapabilitiesAny(List<String> capabilitiesAny) {
        this.capabilitiesAny = capabilitiesAny;
    }
}
