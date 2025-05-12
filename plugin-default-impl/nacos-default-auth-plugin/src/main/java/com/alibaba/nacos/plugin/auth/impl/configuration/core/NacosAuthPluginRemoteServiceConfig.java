/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration.core;

import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnRemoteDatasource;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceRemoteImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserServiceRemoteImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;

/**
 * Nacos auth plugin remote bean config, working on nacos deployment type is `console`.
 *
 * @author xiweng.yy
 */
@Import({AuthConfigs.class})
@Conditional(ConditionOnRemoteDatasource.class)
public class NacosAuthPluginRemoteServiceConfig {
    
    @Bean
    public NacosRoleService nacosRoleService(AuthConfigs authConfigs) {
        return new NacosRoleServiceRemoteImpl(authConfigs);
    }
    
    @Bean
    public NacosUserService nacosUserService(AuthConfigs authConfigs) {
        return new NacosUserServiceRemoteImpl(authConfigs);
    }
}
