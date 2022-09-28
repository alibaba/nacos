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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.naming.core.HealthOperatorV2Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.UpdateHealthForm;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthControllerV2.
 * @author dongyafei
 * @date 2022/9/15
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_HEALTH_CONTEXT)
public class HealthControllerV2 {
    
    @Autowired
    private HealthOperatorV2Impl healthOperatorV2;
    
    /**
     * Update health check for instance.
     *
     * @param updateHealthForm updateHealthForm
     * @return 'ok' if success
     */
    @CanDistro
    @PutMapping(value = {"", "/instance"})
    @Secured(action = ActionTypes.WRITE)
    public Result<String> update(UpdateHealthForm updateHealthForm) throws NacosException {
        updateHealthForm.validate();
        healthOperatorV2.updateHealthStatusForPersistentInstance(updateHealthForm.getNamespaceId(), buildCompositeServiceName(updateHealthForm),
                updateHealthForm.getClusterName(), updateHealthForm.getIp(), updateHealthForm.getPort(),
                updateHealthForm.getHealthy());
        return Result.success("ok");
    }
    
    private String buildCompositeServiceName(UpdateHealthForm updateHealthForm) {
        return NamingUtils.getGroupedName(updateHealthForm.getServiceName(), updateHealthForm.getGroupName());
    }
}
