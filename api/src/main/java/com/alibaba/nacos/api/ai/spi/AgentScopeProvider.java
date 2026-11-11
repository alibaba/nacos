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

package com.alibaba.nacos.api.ai.spi;

import java.util.Set;

/**
 * Supplies canonical scope names to Agent model validation through Java SPI.
 *
 * <p>Implementations provide names only; they do not authorize resource access. The API
 * always accepts PUBLIC/PRIVATE and has no dependency on a server or visibility plugin.
 */
public interface AgentScopeProvider {
    
    /**
     * Resolve currently supported scope names. Called on every model validation.
     *
     * @return non-null set of canonical supported scope names
     */
    Set<String> getScopeNames();
}
