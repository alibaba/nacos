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

import com.alibaba.nacos.core.remote.grpc.GrpcServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private GrpcServer grpcServer;
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @GetMapping("/max")
    public ResponseEntity updateMaxClients(@RequestParam Integer count) {
        Map<String, String> responseMap = new HashMap<>(3);
        grpcServer.setMaxClientCount(count);
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
        grpcServer.reloadClient(count);
        return ResponseEntity.ok().body("success");
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @GetMapping("/current")
    public ResponseEntity currentCount() {
        Map<String, String> responseMap = new HashMap<>(3);
        int count = grpcServer.currentClients();
        return ResponseEntity.ok().body(count);
    }
    
    
}
