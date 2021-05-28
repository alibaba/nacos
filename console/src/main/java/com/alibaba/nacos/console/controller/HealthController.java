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

import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.naming.controllers.OperatorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Health Controller.
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 */
@RestController("consoleHealth")
@RequestMapping("/v1/console/health")
public class HealthController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthController.class);
    
    private final PersistService persistService;
    
    private final OperatorController apiCommands;
    
    @Autowired
    public HealthController(PersistService persistService, OperatorController apiCommands) {
        this.persistService = persistService;
        this.apiCommands = apiCommands;
    }
    
    /**
     * Whether the Nacos is in broken states or not, and cannot recover except by being restarted.
     *
     * @return HTTP code equal to 200 indicates that Nacos is in right states. HTTP code equal to 500 indicates that
     * Nacos is in broken states.
     */
    @GetMapping("/liveness")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok().body("OK");
    }
    
    /**
     * Ready to receive the request or not.
     *
     * @return HTTP code equal to 200 indicates that Nacos is ready. HTTP code equal to 500 indicates that Nacos is not
     * ready.
     */
    @GetMapping("/readiness")
    public ResponseEntity<String> readiness(HttpServletRequest request) {
        boolean isConfigReadiness = isConfigReadiness();
        boolean isNamingReadiness = isNamingReadiness(request);
        
        if (isConfigReadiness && isNamingReadiness) {
            return ResponseEntity.ok().body("OK");
        }
        
        if (!isConfigReadiness && !isNamingReadiness) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Config and Naming are not in readiness");
        }
        
        if (!isConfigReadiness) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Config is not in readiness");
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Naming is not in readiness");
    }
    
    private boolean isConfigReadiness() {
        // check db
        try {
            persistService.configInfoCount("");
            return true;
        } catch (Exception e) {
            LOGGER.error("Config health check fail.", e);
        }
        return false;
    }
    
    private boolean isNamingReadiness(HttpServletRequest request) {
        try {
            apiCommands.metrics(request);
            return true;
        } catch (Exception e) {
            LOGGER.error("Naming health check fail.", e);
        }
        return false;
    }
}
