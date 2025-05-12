/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.Operator;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.UpdateSwitchForm;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OperatorControllerV2.
 *
 * @author dongyafei
 * @date 2022/9/8
 */
@Deprecated
@NacosApi
@RestController
@RequestMapping({UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_OPERATOR_CONTEXT,
        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + "/ops"})
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class OperatorControllerV2 {
    
    private final Operator operatorV2Impl;
    
    public OperatorControllerV2(Operator operatorV2Impl) {
        this.operatorV2Impl = operatorV2Impl;
    }
    
    /**
     * Get switch information.
     *
     * @return switchDomain
     */
    @GetMapping("/switches")
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET ${contextPath:nacos}/v3/admin/ns/ops/switches")
    public Result<SwitchDomain> switches() {
        return Result.success(operatorV2Impl.switches());
    }
    
    /**
     * Update switch information.
     *
     * @param updateSwitchForm debug, entry, value
     * @return 'ok' if success
     * @throws Exception exception
     */
    @Secured(resource = "naming/switches", action = ActionTypes.WRITE)
    @PutMapping("/switches")
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "PUT ${contextPath:nacos}/v3/admin/ns/ops/switches")
    public Result<String> updateSwitch(UpdateSwitchForm updateSwitchForm) throws Exception {
        updateSwitchForm.validate();
        try {
            operatorV2Impl.updateSwitch(updateSwitchForm.getEntry(), updateSwitchForm.getValue(), updateSwitchForm.getDebug());
            
            return Result.success("ok");
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.SERVER_ERROR,
                    e.getMessage());
        }
    }
    
    /**
     * Get metrics information.
     *
     * @param onlyStatus onlyStatus
     * @return metrics information
     */
    @GetMapping("/metrics")
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET ${contextPath:nacos}/v3/admin/ns/ops/metrics")
    public Result<MetricsInfoVo> metrics(
            @RequestParam(value = "onlyStatus", required = false, defaultValue = "true") Boolean onlyStatus) {
        return Result.success(operatorV2Impl.metrics(onlyStatus));
    }
}
