/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * OIDC authentication plugin service implementation.
 *
 * <p>This plugin delegates authentication and authorization to specific providers.
 *
 * @author WangzJi
 */
@SuppressWarnings("PMD")
public class OidcAuthPluginService implements AuthPluginService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthPluginService.class);
    
    /**
     * Identity names that this plugin looks for in requests.
     */
    private static final List<String> IDENTITY_NAMES = Arrays.asList(
        OidcProtocolConstants.AUTHORIZATION_HEADER,
        OidcProtocolConstants.ACCESS_TOKEN_PARAM);
    
    private volatile IdentityProvider identityProvider;
    private volatile AuthorityProvider authorityProvider;
    
    @Override
    public Collection<String> identityNames() {
        return IDENTITY_NAMES;
    }
    
    @Override
    public boolean enableAuth(ActionTypes action, String type) {
        // Enable authentication for all actions and types
        return true;
    }
    
    @Override
    public AuthResult validateIdentity(IdentityContext identityContext, Resource resource) {
        initializeIfNeeded();
        return identityProvider.validateIdentity(identityContext, resource);
    }
    
    @Override
    public AuthResult validateAuthority(IdentityContext identityContext, Permission permission) {
        initializeIfNeeded();
        return authorityProvider.validateAuthority(identityContext, permission);
    }
    
    @Override
    public String getAuthServiceName() {
        return OidcProtocolConstants.AUTH_PLUGIN_TYPE;
    }
    
    @Override
    public boolean isLoginEnabled() {
        // Login is enabled - will be handled by OIDC login controller
        return true;
    }
    
    @Override
    public boolean isAdminRequest() {
        // Return false to indicate that we don't need to initialize a local admin user
        // The Identity Provider handles all user management
        return false;
    }
    
    /**
     * Initialize components lazily.
     */
    private void initializeIfNeeded() {
        if (identityProvider == null || authorityProvider == null) {
            synchronized (this) {
                if (identityProvider == null) {
                    identityProvider = new OidcIdentityProvider();
                }
                if (authorityProvider == null) {
                    authorityProvider = new OidcAuthorityProvider();
                }
            }
        }
    }
}
