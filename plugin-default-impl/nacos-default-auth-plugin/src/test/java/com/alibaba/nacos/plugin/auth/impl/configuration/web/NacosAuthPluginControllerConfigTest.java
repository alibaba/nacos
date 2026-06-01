/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.controller.UserController;
import com.alibaba.nacos.plugin.auth.impl.controller.v3.PermissionControllerV3;
import com.alibaba.nacos.plugin.auth.impl.controller.v3.RoleControllerV3;
import com.alibaba.nacos.plugin.auth.impl.controller.v3.UserControllerV3;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NacosAuthPluginControllerConfigTest {
    
    @Test
    void testV3ControllerBeans() {
        NacosAuthPluginControllerConfig config = new NacosAuthPluginControllerConfig();
        NacosUserService userService = mock(NacosUserService.class);
        NacosRoleService roleService = mock(NacosRoleService.class);
        AuthConfigs authConfigs = mock(AuthConfigs.class);
        IAuthenticationManager authenticationManager = mock(IAuthenticationManager.class);
        TokenManagerDelegate tokenManagerDelegate = mock(TokenManagerDelegate.class);
        
        assertTrue(config.userControllerV3(userService, roleService, authConfigs,
            authenticationManager, tokenManagerDelegate) instanceof UserControllerV3);
        assertTrue(config.roleControllerV3(roleService) instanceof RoleControllerV3);
        assertTrue(config.permissionControllerV3(roleService) instanceof PermissionControllerV3);
    }
    
    @Test
    void testOldControllerBean() {
        NacosAuthPluginOldControllerConfig config = new NacosAuthPluginOldControllerConfig();
        
        assertTrue(config.userController(mock(AuthConfigs.class),
            mock(IAuthenticationManager.class), mock(TokenManagerDelegate.class),
            mock(AuthenticationManager.class)) instanceof UserController);
    }
}
