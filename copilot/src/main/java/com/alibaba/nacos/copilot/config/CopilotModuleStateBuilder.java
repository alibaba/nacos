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

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.AbstractConsoleModuleStateBuilder;
import com.alibaba.nacos.sys.module.ModuleState;

/**
 * Copilot module state builder. Exposes {@code copilot_enabled} in the server state so that the console UI can
 * conditionally render copilot-related features.
 *
 * @author nacos
 */
public class CopilotModuleStateBuilder extends AbstractConsoleModuleStateBuilder {
    
    public static final String COPILOT_MODULE = "copilot";
    
    public static final String COPILOT_ENABLED = "copilot_enabled";
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState(COPILOT_MODULE);
        boolean enabled = EnvUtil.getProperty("nacos.copilot.enabled", Boolean.class, true);
        result.newState(COPILOT_ENABLED, enabled);
        return result;
    }
}
