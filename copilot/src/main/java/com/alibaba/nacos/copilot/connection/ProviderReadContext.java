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

import com.alibaba.nacos.copilot.config.CopilotProperties;

/**
 * Configuration snapshot shared by all readers during one refresh.
 *
 * <p>The effective Copilot configuration is read once per refresh, so the manager snapshot
 * and the connections describe the same configuration generation.
 *
 * @author nacos
 */
public final class ProviderReadContext {
    
    private final CopilotProperties legacyConfig;
    
    private final boolean legacyApiKeyFromDeployment;
    
    /**
     * Create a read context for configuration that does not track the deployment origin.
     *
     * @param legacyConfig effective Copilot configuration for this refresh
     */
    public ProviderReadContext(CopilotProperties legacyConfig) {
        this(legacyConfig, false);
    }
    
    /**
     * Create a read context.
     *
     * @param legacyConfig            effective Copilot configuration for this refresh
     * @param legacyApiKeyFromDeployment whether the effective api key is declared in the
     *                                 deployment configuration
     */
    public ProviderReadContext(CopilotProperties legacyConfig, boolean legacyApiKeyFromDeployment) {
        this.legacyConfig = legacyConfig;
        this.legacyApiKeyFromDeployment = legacyApiKeyFromDeployment;
    }
    
    /**
     * Effective Copilot configuration read once for this refresh.
     *
     * @return configuration
     */
    public CopilotProperties getLegacyConfig() {
        return legacyConfig;
    }
    
    /**
     * Whether the effective api key is declared in the deployment configuration rather than
     * stored by the console.
     *
     * @return {@code true} when the deployment configuration supplies the api key
     */
    public boolean isLegacyApiKeyFromDeployment() {
        return legacyApiKeyFromDeployment;
    }
}
