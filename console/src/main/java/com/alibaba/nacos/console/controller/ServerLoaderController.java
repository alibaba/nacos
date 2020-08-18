/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.JSONUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * controller to controll server loader.
 *
 * @author liuzunfei
 * @version $Id: ServerLoaderController.java, v 0.1 2020年07月22日 4:28 PM liuzunfei Exp $
 */
@RestController
@RequestMapping("/v1/console/loader")
public class ServerLoaderController {
    
    @Autowired
    private ConnectionManager connectionManager;
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @GetMapping("/max")
    public ResponseEntity updateMaxClients(@RequestParam Integer count) {
        Map<String, String> responseMap = new HashMap<>(3);
        connectionManager.coordinateMaxClientsSmoth(count);
        return ResponseEntity.ok().body("success");
    }
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @GetMapping("/reload")
    public ResponseEntity reloadClients(@RequestParam Integer count) {
        Map<String, String> responseMap = new HashMap<>(3);
        connectionManager.loadClientsSmoth(count);
        return ResponseEntity.ok().body("success");
    }
    
    /**
     * Get current clients count with specifiec labels.
     *
     * @return state json.
     */
    @GetMapping("/current")
    public ResponseEntity currentCount(@RequestParam(value = "filters", required = false) String filters) {
        Map<String, String> filterLabels = new HashMap<>(3);
        try {
            if (StringUtils.isNotBlank(filters)) {
                HashMap<String, String> filterMap = (HashMap<String, String>) JSONUtils
                        .deserializeObject(filters, HashMap.class);
                int count = connectionManager.currentClientsCount(filterMap);
                return ResponseEntity.ok().body(count);
            } else {
                int count = connectionManager.currentClientsCount();
                return ResponseEntity.ok().body(count);
            }
        
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        
        }
        
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @GetMapping("/all")
    public ResponseEntity currentClients() {
        Map<String, String> responseMap = new HashMap<>(3);
        Map<String, Connection> stringConnectionMap = connectionManager.currentClients();
        return ResponseEntity.ok().body(stringConnectionMap);
    }
}
