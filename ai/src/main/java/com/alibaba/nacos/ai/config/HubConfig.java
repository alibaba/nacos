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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Effective Hub feature configuration shared by service gates and module state.
 *
 * @author nacos
 */
public final class HubConfig {
    
    public static final String ENABLED_PROPERTY = "nacos.ai.hub.enabled";
    
    public static final String ENABLED_STATE = "ai_hub_enabled";
    
    private HubConfig() {
    }
    
    /**
     * Read the effective environment, including reloaded application properties.
     *
     * @return whether Hub operations are enabled; defaults to true for compatibility
     */
    public static boolean isEnabled() {
        return EnvUtil.getEnvironment() == null
            || EnvUtil.getProperty(ENABLED_PROPERTY, Boolean.class, true);
    }
    
    /**
     * Reject non-list Hub operations when the feature is disabled.
     *
     * @throws NacosApiException when Hub is disabled
     */
    public static void requireEnabled() throws NacosApiException {
        if (!isEnabled()) {
            throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
                ErrorCode.API_FUNCTION_DISABLED, "Nacos AI Hub is disabled.");
        }
    }
}
