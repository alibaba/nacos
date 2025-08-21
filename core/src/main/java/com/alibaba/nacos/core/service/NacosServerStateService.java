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

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Nacos server state service.
 *
 * @author xiweng.yy
 */
@Service
public class NacosServerStateService {
    
    /**
     * Get current server states.
     *
     * @return server states key-value map.
     */
    public Map<String, String> getServerState() {
        Map<String, String> serverState = new HashMap<>(4);
        for (ModuleState each : ModuleStateHolder.getInstance().getAllModuleStates()) {
            each.getStates().forEach((s, o) -> serverState.put(s, null == o ? null : o.toString()));
        }
        return serverState;
    }
}
