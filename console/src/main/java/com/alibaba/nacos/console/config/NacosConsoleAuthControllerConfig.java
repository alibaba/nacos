/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.controller.v3.PermissionControllerV3;
import com.alibaba.nacos.plugin.auth.impl.controller.v3.RoleControllerV3;
import com.alibaba.nacos.plugin.auth.impl.controller.v3.UserControllerV3;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration of console auth controller.
 * TODO use {@link Import} to dynamic load auth plugin controller like Mybatis.
 *
 * @author xiweng.yy
 */
@Configuration
public class NacosConsoleAuthControllerConfig {
    
    @Bean
    public UserControllerV3 consoleUserControllerV3(NacosUserService userDetailsService, NacosRoleService roleService,
            AuthConfigs authConfigs, IAuthenticationManager iAuthenticationManager,
            TokenManagerDelegate jwtTokenManager) {
        return new UserControllerV3(userDetailsService, roleService, authConfigs, iAuthenticationManager,
                jwtTokenManager);
    }
    
    @Bean
    public RoleControllerV3 consoleRoleControllerV3(NacosRoleService roleService) {
        return new RoleControllerV3(roleService);
    }
    
    @Bean
    public PermissionControllerV3 permissionControllerV3(NacosRoleService roleService) {
        return new PermissionControllerV3(roleService);
    }
}
