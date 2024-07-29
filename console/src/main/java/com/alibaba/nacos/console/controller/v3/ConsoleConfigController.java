/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.ConfigProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Controller for handling HTTP requests related to configuration operations.
 *
 * @author zhangyukun
 */
@RestController
@RequestMapping("/v3/console/cs/configs")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
public class ConsoleConfigController {
    
    private final ConfigProxy configProxy;
    
    @Autowired
    public ConsoleConfigController(ConfigProxy configProxy) {
        this.configProxy = configProxy;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException ServletException.
     * @throws IOException      IOException.
     * @throws NacosException   NacosException.
     */
    @GetMapping
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant,
            @RequestParam(value = "tag", required = false) String tag)
            throws IOException, ServletException, NacosException {
        configProxy.getConfig(request, response, dataId, group, tenant, tag);
    }
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosException NacosException.
     */
    @PostMapping
    public ResponseEntity<?> publishConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant,
            @RequestParam("content") String content, @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "desc", required = false) String desc,
            @RequestParam(value = "use", required = false) String use,
            @RequestParam(value = "effect", required = false) String effect,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(required = false) String encryptedDataKey) {
        try {
            boolean result = configProxy.publishConfig(request, response, dataId, group, tenant, content, tag, appName,
                    srcUser, configTags, desc, use, effect, type, schema, encryptedDataKey);
            return ResponseEntity.ok(result);
        } catch (NacosException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getErrMsg());
        }
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * @throws NacosException NacosException.
     */
    @DeleteMapping
    public ResponseEntity<?> deleteConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant,
            @RequestParam(value = "tag", required = false) String tag) {
        try {
            boolean result = configProxy.deleteConfig(request, response, dataId, group, tenant, tag);
            return ResponseEntity.ok(result);
        } catch (NacosException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getErrMsg());
        }
    }
    
    /**
     * Get the specific configuration information that the console USES.
     *
     * @throws NacosException NacosException.
     */
    @GetMapping("/detail")
    public ResponseEntity<?> detailConfigInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant) {
        try {
            ConfigAllInfo configAllInfo = configProxy.detailConfigInfo(dataId, group, tenant);
            return ResponseEntity.ok(configAllInfo);
        } catch (NacosException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getErrMsg());
        }
    }
}


