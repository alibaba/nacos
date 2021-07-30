/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Server state controller.
 *
 * @author xingxuechao on:2019/2/27 11:17 AM
 */
@RestController
@RequestMapping("/v1/console/server")
public class ServerStateController {
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, String>> serverState() {
        Map<String, String> serverState = new HashMap<>(4);
        serverState.put("standalone_mode",
                EnvUtil.getStandaloneMode() ? EnvUtil.STANDALONE_MODE_ALONE : EnvUtil.STANDALONE_MODE_CLUSTER);
        
        serverState.put("function_mode", EnvUtil.getFunctionMode());
        serverState.put("version", VersionUtils.version);
        
        return ResponseEntity.ok().body(serverState);
    }
    
}
