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

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Dynamic entry switch for the AI publish pipeline capability.
 *
 * @author Nacos
 */
public class AiPipelineModuleConfig extends AbstractDynamicConfig {
    
    public static final String ENABLED_PROPERTY = "nacos.plugin.ai-pipeline.enabled";
    
    private volatile boolean enabled;
    
    public AiPipelineModuleConfig() {
        super("AiPipelineModuleConfig");
        resetConfig();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    protected void getConfigFromEnv() {
        enabled = EnvUtil.getProperty(ENABLED_PROPERTY, Boolean.class, true);
    }
    
    @Override
    protected String printConfig() {
        return "AiPipelineModuleConfig{enabled=" + enabled + '}';
    }
}
