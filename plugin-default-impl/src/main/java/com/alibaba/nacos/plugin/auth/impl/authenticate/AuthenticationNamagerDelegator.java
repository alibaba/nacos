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

package com.alibaba.nacos.plugin.auth.impl.authenticate;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.springframework.beans.factory.ObjectProvider;

import javax.servlet.http.HttpServletRequest;

/**
 * Authentication Proxy.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/12 23:31
 */
public class AuthenticationNamagerDelegator implements IAuthenticationManager {
    
    private ObjectProvider<DefaultAuthenticationManager> defaultAuthenticationManager;
    
    private ObjectProvider<LdapAuthenticationManager> ldapAuthenticatoinManager;
    
    private AuthConfigs authConfigs;
    
    public AuthenticationNamagerDelegator(ObjectProvider<DefaultAuthenticationManager> nacosAuthManager,
            ObjectProvider<LdapAuthenticationManager> ldapAuthenticationProvider, AuthConfigs authConfigs) {
        this.defaultAuthenticationManager = nacosAuthManager;
        this.ldapAuthenticatoinManager = ldapAuthenticationProvider;
        this.authConfigs = authConfigs;
    }
    
    @Override
    public NacosUser authenticate(String username, String password) throws AccessException {
        return getManager().authenticate(username, password);
    }
    
    @Override
    public NacosUser authenticate(String jwtToken) throws AccessException {
        return getManager().authenticate(jwtToken);
    }
    
    @Override
    public NacosUser authenticate(HttpServletRequest httpServletRequest) throws AccessException {
        return getManager().authenticate(httpServletRequest);
    }
    
    @Override
    public void authorize(Permission permission, NacosUser nacosUser) throws AccessException {
        getManager().authorize(permission, nacosUser);
    }
    
    @Override
    public boolean hasGlobalAdminRole(String username) {
        return getManager().hasGlobalAdminRole(username);
    }
    
    @Override
    public boolean hasGlobalAdminRole(NacosUser nacosUser) {
        return getManager().hasGlobalAdminRole(nacosUser);
    }
    
    private IAuthenticationManager getManager() {
        if (AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            return ldapAuthenticatoinManager.getIfAvailable();
        }
        
        return defaultAuthenticationManager.getIfAvailable();
    }
}
