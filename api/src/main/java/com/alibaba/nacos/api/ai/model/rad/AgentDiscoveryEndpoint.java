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

package com.alibaba.nacos.api.ai.model.rad;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Endpoint returned by Agent discovery with Runtime Version provenance when the source is
 * {@code RUNTIME}.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentDiscoveryEndpoint extends Endpoint {
    
    private static final long serialVersionUID = 1L;
    
    private List<RuntimeVersionBinding> bindings;
    
    public List<RuntimeVersionBinding> getBindings() {
        return bindings;
    }
    
    public void setBindings(List<RuntimeVersionBinding> bindings) {
        this.bindings = bindings;
    }
}
