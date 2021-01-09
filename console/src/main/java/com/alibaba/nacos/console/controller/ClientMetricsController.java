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

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

/**
 * ClientMetricsController.
 *
 * @author zunfei.lzf
 */
@RequestMapping("/v1/console/client")
public class ClientMetricsController {
    
    @Autowired
    private ServerMemberManager serverMemberManager;
    
    /**
     * get client metric.
     * @param clientIp
     * @return
     */
    @GetMapping("/metrics")
    public ResponseEntity metric(@RequestParam String clientIp) {
        Map<String, String> responseMap = new HashMap<>(3);
        
        return ResponseEntity.ok().body(responseMap);
    }
    
    private Map<String, String> geMetrics(String clientIp) {
        return null;
    }
    
}
