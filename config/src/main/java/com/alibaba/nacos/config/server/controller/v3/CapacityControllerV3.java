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

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RestController
@RequestMapping(CAPACITY_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class CapacityControllerV3 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityControllerV3.class);
    
    private final CapacityService capacityService;
    
    private static final int STATUS400 = 400;
    
    private static final int STATUS500 = 500;
    
    public CapacityControllerV3(CapacityService capacityService) {
        this.capacityService = capacityService;
    }
    
    /**
     * Get capacity information.
     */
    @GetMapping
    @Secured(resource = Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Capacity> getCapacity(@RequestParam(required = false) String group, @RequestParam(required = false) String tenant) {
        if (StringUtils.isBlank(group) && StringUtils.isBlank(tenant)) {
            return Result.failure(STATUS400, "At least one of the parameters (group or tenant) must be provided", null);
        }
        
        try {
            Capacity capacity = capacityService.getCapacityWithDefault(group, tenant);
            if (capacity == null) {
                LOGGER.warn("[getCapacity] capacity not exist，need init group: {}, tenant: {}", group, tenant);
                capacityService.initCapacity(group, tenant);
                capacity = capacityService.getCapacityWithDefault(group, tenant);
            }
            return Result.success(capacity);
        } catch (Exception e) {
            LOGGER.error("[getCapacity] ", e);
            return Result.failure(STATUS500, e.getMessage(), null);
        }
    }
    
    /**
     * Modify group or capacity of tenant, and init record when capacity information are still initial.
     */
    @PostMapping
    @Secured(resource = Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> updateCapacity(@RequestParam(required = false) String group, @RequestParam(required = false) String tenant,
            @RequestParam(required = false) Integer quota, @RequestParam(required = false) Integer maxSize,
            @RequestParam(required = false) Integer maxAggrCount, @RequestParam(required = false) Integer maxAggrSize) {
        if (StringUtils.isBlank(group) && StringUtils.isBlank(tenant)) {
            capacityService.initAllCapacity();
            return Result.failure(STATUS400, "The parameter group and tenant cannot be empty at the same time", null);
        }
        
        if (quota == null && maxSize == null && maxAggrCount == null && maxAggrSize == null) {
            return Result.failure(STATUS400, "The parameters quota, maxSize, maxAggrCount, maxAggrSize cannot be empty at the same time", null);
        }
        
        String targetFieldName;
        String targetFieldValue;
        if (tenant == null) {
            targetFieldName = "group";
            targetFieldValue = group;
        } else {
            targetFieldName = "tenant";
            targetFieldValue = tenant;
        }
        if (StringUtils.isBlank(targetFieldValue)) {
            return Result.failure(STATUS400, String.format("parameter %s is empty.", targetFieldName), null);
        }
        
        try {
            boolean insertOrUpdateResult = capacityService
                    .insertOrUpdateCapacity(group, tenant, quota, maxSize, maxAggrCount, maxAggrSize);
            if (insertOrUpdateResult) {
                return Result.success(true);
            }
            return Result.failure(STATUS500, String.format("failed updated the capacity information configuration of %s to %s", targetFieldName,
                    targetFieldValue), null);
        } catch (Exception e) {
            LOGGER.error("[updateCapacity] ", e);
            return Result.failure(STATUS500, e.getMessage(), null);
        }
    }
}
