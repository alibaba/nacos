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

package com.alibaba.nacos.copilot.connection;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelRegistry;
import org.springframework.stereotype.Component;

/**
 * Bridges a Nacos connection to an AgentScope model.
 *
 * @author nacos
 */
@Component
public class CopilotModelResolver {
    
    /**
     * Check whether a provider implementation for the connection's model id is on the classpath.
     *
     * @param connection model connection
     * @return true when the provider prefix can be handled
     */
    public boolean canResolve(CopilotModelConnection connection) {
        return ModelRegistry.canResolve(connection.getModelId());
    }
    
    /**
     * Resolve the connection through AgentScope's registry.
     *
     * @param connection model connection
     * @return AgentScope model
     */
    public Model resolve(CopilotModelConnection connection) {
        return ModelRegistry.resolve(connection.getModelId(), connection.toCreationContext());
    }
}
