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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.form.UpdateCapacityForm;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.config.server.constant.Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH;

/**
 * Capacity Management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(CAPACITY_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
@Tag(name = "nacos.admin.config.capacity.api.controller.name", description = "nacos.admin.config.capacity.api.controller.description")
public class CapacityControllerV3 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityControllerV3.class);
    
    private final CapacityService capacityService;
    
    public CapacityControllerV3(CapacityService capacityService) {
        this.capacityService = capacityService;
    }
    
    /**
     * Get capacity information.
     */
    @GetMapping
    @Secured(resource = Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.config.capacity.api.get.summary", description = "nacos.admin.config.capacity.api.get.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.config.capacity.api.get.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", required = true)})
    public Result<Capacity> getCapacity(@RequestParam(required = false) String groupName,
            @RequestParam(required = false) String namespaceId) throws NacosApiException {
        if (StringUtils.isBlank(groupName) && StringUtils.isBlank(namespaceId)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "At least one of the parameters (groupName or namespaceId) must be provided");
        }
        
        try {
            Capacity capacity = capacityService.getCapacityWithDefault(groupName, namespaceId);
            if (capacity == null) {
                LOGGER.warn("[getCapacity] capacity not exist，need init groupName: {}, namespaceId: {}", groupName, namespaceId);
                capacityService.initCapacity(groupName, namespaceId);
                capacity = capacityService.getCapacityWithDefault(groupName, namespaceId);
            }
            return Result.success(capacity);
        } catch (Exception e) {
            LOGGER.error("[getCapacity] Failed to fetch capacity for groupName: {}, namespaceId: {}", groupName, namespaceId, e);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), e.getMessage(), null);
        }
    }
    
    /**
     * Modify group or capacity of namespaceId, and init record when capacity information are still initial.
     */
    @PostMapping
    @Secured(resource = Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.config.capacity.api.update.summary", description = "nacos.admin.config.capacity.api.update.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.config.capacity.api.update.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", required = true), @Parameter(name = "quota", example = "100"),
            @Parameter(name = "maxSize", example = "102400"), @Parameter(name = "updateCapacityForm", hidden = true)})
    public Result<Boolean> updateCapacity(UpdateCapacityForm updateCapacityForm) throws NacosApiException {
        updateCapacityForm.checkNamespaceIdAndGroupName(capacityService);
        updateCapacityForm.validate();
        
        String groupName = updateCapacityForm.getGroupName();
        String namespaceId = updateCapacityForm.getNamespaceId();
        Integer quota = updateCapacityForm.getQuota();
        Integer maxSize = updateCapacityForm.getMaxSize();
        Integer maxAggrCount = updateCapacityForm.getMaxAggrCount();
        Integer maxAggrSize = updateCapacityForm.getMaxAggrSize();
        
        try {
            boolean isSuccess = capacityService.insertOrUpdateCapacity(groupName, namespaceId, quota, maxSize,
                    maxAggrCount, maxAggrSize);
            if (isSuccess) {
                return Result.success(true);
            } else {
                return Result.failure(ErrorCode.SERVER_ERROR.getCode(),
                        String.format("Failed to update the capacity for groupName: %s, namespaceId: %s", groupName, namespaceId), null);
            }
        } catch (Exception e) {
            LOGGER.error("[updateCapacity] Failed to update the capacity for groupName: {}, namespaceId: {}", groupName, namespaceId, e);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), e.getMessage(), null);
        }
    }
}