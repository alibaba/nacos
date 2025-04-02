/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.HealthOperatorV2Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.UpdateHealthForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health controller.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.HEALTH_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
@Tag(name = "nacos.admin.naming.health.api.controller.name", description = "nacos.admin.naming.health.api.controller.description")
public class HealthControllerV3 {
    
    @Autowired
    private HealthOperatorV2Impl healthOperatorV2;
    
    /**
     * Update health check for instance.
     */
    @CanDistro
    @PutMapping(value = "/instance")
    @Secured(resource = UtilsAndCommons.HEALTH_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.health.api.update.summary", description = "nacos.admin.naming.health.api.update.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.health.api.update.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"),
            @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"),
            @Parameter(name = "healthy", example = "true"), @Parameter(name = "updateHealthForm", hidden = true)})
    public Result<String> update(UpdateHealthForm updateHealthForm) throws NacosException {
        updateHealthForm.validate();
        healthOperatorV2.updateHealthStatusForPersistentInstance(updateHealthForm.getNamespaceId(),
                updateHealthForm.getGroupName(), updateHealthForm.getServiceName(), updateHealthForm.getClusterName(),
                updateHealthForm.getIp(), updateHealthForm.getPort(), updateHealthForm.getHealthy());
        
        return Result.success("ok");
    }
    
    /**
     * Get all health checkers.
     */
    @GetMapping("/checkers")
    @Secured(resource = UtilsAndCommons.HEALTH_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.health.api.checkers.summary", description = "nacos.admin.naming.health.api.checkers.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.health.api.checkers.example")))
    public Result<Map<String, AbstractHealthChecker>> checkers() {
        return Result.success(healthOperatorV2.checkers());
    }
}