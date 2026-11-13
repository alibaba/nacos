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

import com.alibaba.nacos.sys.module.AbstractServerModuleStateBuilder;
import com.alibaba.nacos.sys.module.ModuleState;

/**
 * Publishes the server's effective Hub switch through existing state endpoints.
 *
 * @author nacos
 */
public class HubModuleStateBuilder extends AbstractServerModuleStateBuilder {
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState("ai_hub");
        result.newState(HubConfig.ENABLED_STATE, HubConfig.isEnabled());
        return result;
    }
    
    @Override
    public boolean isCacheable() {
        return false;
    }
}
