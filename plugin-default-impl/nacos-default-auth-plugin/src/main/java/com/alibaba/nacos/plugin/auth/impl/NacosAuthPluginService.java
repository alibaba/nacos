/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID;

/**
 * Nacos default auth plugin service implementation.
 *
 * @author xiweng.yy
 */
public class NacosAuthPluginService implements AuthPluginService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosAuthPluginService.class);
    
    private static final List<String> IDENTITY_NAMES = new LinkedList<String>() {
        
        {
            add(AuthConstants.AUTHORIZATION_HEADER);
            add(Constants.ACCESS_TOKEN);
            add(AuthConstants.PARAM_USERNAME);
            add(AuthConstants.PARAM_PASSWORD);
        }
    };
    
    protected IAuthenticationManager authenticationManager;
    
    private volatile AuthConfigs authConfigs;
    
    @Override
    public Collection<String> identityNames() {
        return IDENTITY_NAMES;
    }
    
    @Override
    public boolean enableAuth(ActionTypes action, String type) {
        // enable all of action and type
        return true;
    }
    
    @Override
    public AuthResult validateIdentity(IdentityContext identityContext, Resource resource) {
        try {
            NacosUser nacosUser = validateUser(identityContext);
            return AuthResult.successResult(nacosUser);
        } catch (AccessException e) {
            if (isAnonymousAllowed(resource)) {
                LOGGER.debug("Anonymous access granted for resource: {}", resource);
                NacosUser anonymousUser = new NacosUser(AuthConstants.ANONYMOUS_USER);
                identityContext.setParameter(AuthConstants.NACOS_USER_KEY, anonymousUser);
                identityContext.setParameter(IDENTITY_ID, AuthConstants.ANONYMOUS_USER);
                return AuthResult.successResult(anonymousUser);
            }
            return AuthResult.failureResult(HttpStatus.UNAUTHORIZED.value(), e.getErrMsg());
        }
    }
    
    private boolean isAnonymousAllowed(Resource resource) {
        if (resource == null || resource.getProperties() == null) {
            return false;
        }
        Properties props = resource.getProperties();
        if (!props.containsKey(AuthConstants.TAG_ALLOW_ANONYMOUS)) {
            return false;
        }
        try {
            checkAuthConfigs();
            return authConfigs.isAiAnonymousEnabled();
        } catch (Exception e) {
            return false;
        }
    }
    
    private void checkAuthConfigs() {
        if (null == authConfigs) {
            authConfigs = ApplicationUtils.getBean(AuthConfigs.class);
        }
    }
    
    private NacosUser validateUser(IdentityContext identityContext) throws AccessException {
        checkNacosAuthManager();
        String token = resolveToken(identityContext);
        NacosUser nacosUser;
        if (StringUtils.isNotBlank(token)) {
            nacosUser = authenticationManager.authenticate(token);
        } else {
            String userName = (String) identityContext.getParameter(AuthConstants.PARAM_USERNAME);
            String password = (String) identityContext.getParameter(AuthConstants.PARAM_PASSWORD);
            nacosUser = authenticationManager.authenticate(userName, password);
        }
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, nacosUser);
        identityContext.setParameter(IDENTITY_ID, nacosUser.getUserName());
        return nacosUser;
    }
    
    private String resolveToken(IdentityContext identityContext) {
        String bearerToken =
            identityContext.getParameter(AuthConstants.AUTHORIZATION_HEADER, StringUtils.EMPTY);
        if (StringUtils.isNotBlank(bearerToken)
            && bearerToken.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(AuthConstants.TOKEN_PREFIX.length());
        }
        
        return identityContext.getParameter(Constants.ACCESS_TOKEN, StringUtils.EMPTY);
    }
    
    @Override
    public AuthResult validateAuthority(IdentityContext identityContext, Permission permission) {
        try {
            NacosUser user = (NacosUser) identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
            authenticationManager.authorize(permission, user);
            return AuthResult.successResult(user);
        } catch (AccessException e) {
            return AuthResult.failureResult(HttpStatus.FORBIDDEN.value(), e.getErrMsg());
        }
    }
    
    @Override
    public String getAuthServiceName() {
        return AuthConstants.AUTH_PLUGIN_TYPE;
    }
    
    @Override
    public boolean isLoginEnabled() {
        return NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(ApiType.CONSOLE_API.name())
            .isAuthEnabled();
    }
    
    /**
     * Only auth enabled and not global admin role existed.
     *
     * @return {@code true} when auth enabled and not global admin role existed, otherwise {@code false}
     */
    @Override
    public boolean isAdminRequest() {
        boolean authEnabled = false;
        for (NacosAuthConfig each : NacosAuthConfigHolder.getInstance().getAllNacosAuthConfig()) {
            authEnabled |= each.isAuthEnabled();
        }
        boolean hasGlobalAdminRole =
            ApplicationUtils.getBean(IAuthenticationManager.class).hasGlobalAdminRole();
        return authEnabled && !hasGlobalAdminRole;
    }
    
    protected void checkNacosAuthManager() {
        if (null == authenticationManager) {
            authenticationManager = ApplicationUtils.getBean(IAuthenticationManager.class);
        }
    }
}
