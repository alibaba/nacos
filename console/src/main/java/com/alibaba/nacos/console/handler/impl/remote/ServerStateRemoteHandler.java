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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.console.handler.impl.AbstractServerStateHandler;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote Implementation of ServerStateHandler that performs server state operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class ServerStateRemoteHandler extends AbstractServerStateHandler {
    
    public Map<String, String> getServerState() {
        Map<String, String> serverState = new HashMap<>(4);
        // TODO get state from nacos servers
        // Mock first
        serverState.put(Constants.STARTUP_MODE_STATE, EnvUtil.STANDALONE_MODE_ALONE);
        serverState.put(Constants.FUNCTION_MODE_STATE, EnvUtil.getFunctionMode());
        serverState.put(Constants.NACOS_VERSION, VersionUtils.version);
        serverState.put(Constants.SERVER_PORT_STATE, EnvUtil.getProperty("nacos.console.port", "8080"));
        // Add current console states
        for (ModuleState each : ModuleStateHolder.getInstance().getAllModuleStates()) {
            each.getStates().forEach((s, o) -> serverState.put(s, null == o ? null : o.toString()));
        }
        return serverState;
    }
}

