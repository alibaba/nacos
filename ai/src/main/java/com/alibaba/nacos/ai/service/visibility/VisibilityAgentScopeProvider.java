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

package com.alibaba.nacos.ai.service.visibility;

import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.api.ai.spi.AgentScopeProvider;

import java.util.Set;

/**
 * Bridges Agent model validation to the selected visibility plugin's scope catalog.
 */
public class VisibilityAgentScopeProvider implements AgentScopeProvider {
    
    @Override
    public Set<String> getScopeNames() {
        return VisibilityHelper.scopeRegistry().getScopeNames();
    }
}
