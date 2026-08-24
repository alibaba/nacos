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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Resolves the RAD Search read path for one request.
 *
 * @author Nacos
 */
@Component
public class AgentSearchModeResolver {
    
    private final Supplier<String> configuredModeSupplier;
    
    public AgentSearchModeResolver() {
        this(() -> EnvUtil.getProperty(Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY,
            AgentSearchMode.AUTO.name()));
    }
    
    AgentSearchModeResolver(Supplier<String> configuredModeSupplier) {
        this.configuredModeSupplier = configuredModeSupplier;
    }
    
    /**
     * Resolve AUTO to the shared index while retaining explicit SCAN compatibility.
     *
     * @return {@link AgentSearchMode#SCAN} or {@link AgentSearchMode#INDEX}
     */
    public AgentSearchMode resolve() throws NacosException {
        AgentSearchMode configured = AgentSearchMode.parse(configuredModeSupplier.get());
        return AgentSearchMode.SCAN == configured ? AgentSearchMode.SCAN
            : AgentSearchMode.INDEX;
    }
}
