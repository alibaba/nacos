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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Load Beans for {@link com.alibaba.nacos.sys.env.DeploymentType#CONSOLE} type.
 *
 * @author xiweng.yy
 */
@Configuration
@EnabledRemoteHandler
public class ConsoleDeploymentConfig {
    
    @Bean
    public ControllerMethodsCache controllerMethodsCache() {
        return new ControllerMethodsCache();
    }
    
    @Bean
    public SelectorManager selectorManager() {
        return new SelectorManager();
    }
    
    @Bean
    public IAuthenticationManager authenticationManager() {
        // TODO implement remote authentication manager.
        return new IAuthenticationManager() {
            @Override
            public NacosUser authenticate(String username, String rawPassword) throws AccessException {
                return new NacosUser("nacos");
            }
            
            @Override
            public NacosUser authenticate(String jwtToken) throws AccessException {
                return new NacosUser("nacos");
            }
            
            @Override
            public NacosUser authenticate(HttpServletRequest httpServletRequest) throws AccessException {
                return new NacosUser("nacos");
            }
            
            @Override
            public void authorize(Permission permission, NacosUser nacosUser) throws AccessException {
            }
            
            @Override
            public boolean hasGlobalAdminRole(String username) {
                return true;
            }
            
            @Override
            public boolean hasGlobalAdminRole() {
                return true;
            }
            
            @Override
            public boolean hasGlobalAdminRole(NacosUser nacosUser) {
                return true;
            }
        };
    }
}
