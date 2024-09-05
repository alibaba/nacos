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

package com.alibaba.nacos.config.server.constant;

import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.persistence.utils.DatasourcePlatformUtil;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;

/**
 * config module state builder.
 * @author 985492783@qq.com
 * @date 2023/4/6 10:25
 */
public class ConfigModuleStateBuilder implements ModuleStateBuilder {
    
    @Override
    public ModuleState build() {
        ModuleState moduleState = new ModuleState(com.alibaba.nacos.api.common.Constants.Config.CONFIG_MODULE);
        
        moduleState.newState(Constants.DATASOURCE_PLATFORM_PROPERTY_STATE, DatasourcePlatformUtil.getDatasourcePlatform(""));
        moduleState.newState(Constants.NACOS_PLUGIN_DATASOURCE_LOG_STATE,
                EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false));
        
        moduleState.newState(PropertiesConstant.NOTIFY_CONNECT_TIMEOUT, PropertyUtil.getNotifyConnectTimeout());
        moduleState.newState(PropertiesConstant.NOTIFY_SOCKET_TIMEOUT, PropertyUtil.getNotifySocketTimeout());
        moduleState.newState(PropertiesConstant.IS_HEALTH_CHECK, PropertyUtil.isHealthCheck());
        moduleState.newState(PropertiesConstant.MAX_HEALTH_CHECK_FAIL_COUNT, PropertyUtil.getMaxHealthCheckFailCount());
        moduleState.newState(PropertiesConstant.MAX_CONTENT, PropertyUtil.getMaxContent());
        moduleState.newState(PropertiesConstant.IS_MANAGE_CAPACITY, PropertyUtil.isManageCapacity());
        moduleState.newState(PropertiesConstant.IS_CAPACITY_LIMIT_CHECK, PropertyUtil.isCapacityLimitCheck());
        moduleState.newState(PropertiesConstant.DEFAULT_CLUSTER_QUOTA, PropertyUtil.getDefaultClusterQuota());
        moduleState.newState(PropertiesConstant.DEFAULT_GROUP_QUOTA, PropertyUtil.getDefaultGroupQuota());
        moduleState.newState(PropertiesConstant.DEFAULT_MAX_SIZE, PropertyUtil.getDefaultMaxSize());
        moduleState.newState(PropertiesConstant.DEFAULT_MAX_AGGR_COUNT, PropertyUtil.getDefaultMaxAggrCount());
        moduleState.newState(PropertiesConstant.DEFAULT_MAX_AGGR_SIZE, PropertyUtil.getDefaultMaxAggrSize());
        moduleState.newState(Constants.CONFIG_RENTENTION_DAYS_PROPERTY_STATE, PropertyUtil.getConfigRententionDays());

        return moduleState;
    }
}
