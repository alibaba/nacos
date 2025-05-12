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

package com.alibaba.nacos.plugin.auth.impl.configuration.web;

import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.controller.PermissionController;
import com.alibaba.nacos.plugin.auth.impl.controller.RoleController;
import com.alibaba.nacos.plugin.auth.impl.controller.UserController;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * Nacos auth plugin controller config.
 *
 * @author xiweng.yy
 * @deprecated after v1 api not supported
 */
@Deprecated
public class NacosAuthPluginOldControllerConfig {
    
    @Bean
    public UserController userController(NacosUserService userDetailsService, NacosRoleService roleService,
            AuthConfigs authConfigs, IAuthenticationManager iAuthenticationManager,
            TokenManagerDelegate jwtTokenManager, AuthenticationManager authenticationManager) {
        return new UserController(jwtTokenManager, userDetailsService, roleService, authConfigs, iAuthenticationManager,
                authenticationManager);
    }
    
    @Bean
    public RoleController roleController(NacosRoleService roleService) {
        return new RoleController(roleService);
    }
    
    @Bean
    public PermissionController permissionController(NacosRoleService roleService) {
        return new PermissionController(roleService);
    }
}
