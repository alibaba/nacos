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
import org.springframework.beans.factory.annotation.Autowired;
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
public class HealthControllerV3 {
    
    @Autowired
    private HealthOperatorV2Impl healthOperatorV2;
    
    /**
     * Update health check for instance.
     */
    @CanDistro
    @PutMapping(value = "/instance")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
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
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<Map<String, AbstractHealthChecker>> checkers() {
        return Result.success(healthOperatorV2.checkers());
    }
}