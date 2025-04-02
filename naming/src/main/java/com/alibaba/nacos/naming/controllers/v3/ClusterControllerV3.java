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
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.UpdateClusterForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cluster controller.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.CLUSTER_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
@Tag(name = "nacos.admin.naming.service.api.controller.name", description = "nacos.admin.naming.service.api.controller.description")
public class ClusterControllerV3 {
    
    private final ClusterOperatorV2Impl clusterOperatorV2;
    
    public ClusterControllerV3(ClusterOperatorV2Impl clusterOperatorV2) {
        this.clusterOperatorV2 = clusterOperatorV2;
    }
    
    /**
     * Update cluster.
     */
    @PutMapping
    @Secured(resource = UtilsAndCommons.CLUSTER_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.service.api.update.cluster.summary",
            description = "nacos.admin.naming.service.api.update.cluster.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.update.cluster.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", required = true, example = "DEFAULT"),
            @Parameter(name = "checkPort", example = "8080"), @Parameter(name = "useInstancePort4Check", example = "false"),
            @Parameter(name = "healthChecker", example = "{\"type\":\"none\"}"),
            @Parameter(name = "metadata", example = "{\"version\":\"1.0\"}"),
            @Parameter(name = "updateClusterForm", hidden = true)})
    public Result<String> update(UpdateClusterForm updateClusterForm) throws Exception {
        updateClusterForm.validate();
        
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(updateClusterForm.getCheckPort());
        clusterMetadata.setUseInstancePortForCheck(updateClusterForm.isUseInstancePort4Check());
        AbstractHealthChecker healthChecker = HealthCheckerFactory.deserialize(updateClusterForm.getHealthChecker());
        clusterMetadata.setHealthChecker(healthChecker);
        clusterMetadata.setHealthyCheckType(healthChecker.getType());
        clusterMetadata.setExtendData(UtilsAndCommons.parseMetadata(updateClusterForm.getMetadata()));
        
        clusterOperatorV2.updateClusterMetadata(updateClusterForm.getNamespaceId(), updateClusterForm.getGroupName(),
                updateClusterForm.getServiceName(), updateClusterForm.getClusterName(), clusterMetadata);
        
        return Result.success("ok");
    }
}