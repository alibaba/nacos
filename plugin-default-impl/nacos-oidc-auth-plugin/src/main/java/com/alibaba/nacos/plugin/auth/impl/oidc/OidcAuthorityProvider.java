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
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.OidcAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * OIDC implementation of AuthorityProvider.
 *
 * @author WangzJi
 */
public class OidcAuthorityProvider implements AuthorityProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthorityProvider.class);
    
    private volatile OidcAuthenticationManager authManager;
    private volatile OidcAuthConfig config;
    
    @Override
    public AuthResult validateAuthority(IdentityContext identityContext, Permission permission) {
        try {
            initializeIfNeeded();
            
            // Get user from context
            OidcUser user = authManager.getUserFromContext(identityContext);
            if (user == null) {
                LOGGER.warn("No OIDC user found in context for authorization");
                return AuthResult.failureResult(HttpStatus.FORBIDDEN.value(),
                    "User not authenticated");
            }
            
            // Check if user is global admin
            if (authManager.isGlobalAdmin(user)) {
                LOGGER.debug("User {} is global admin, authorization granted", user.getUsername());
                return AuthResult.successResult(user);
            }
            
            // Check permission
            if (authManager.hasPermission(user, permission)) {
                LOGGER.debug("User {} authorized for {}:{}", user.getUsername(),
                    permission.getResource().getName(), permission.getAction());
                return AuthResult.successResult(user);
            }
            
            LOGGER.warn("User {} denied access to {}:{}", user.getUsername(),
                permission.getResource().getName(), permission.getAction());
            return AuthResult.failureResult(HttpStatus.FORBIDDEN.value(), "Access denied");
            
        } catch (Exception e) {
            LOGGER.error("OIDC authorization error", e);
            return AuthResult.failureResult(HttpStatus.FORBIDDEN.value(), "Authorization failed");
        }
    }
    
    private void initializeIfNeeded() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    config = OidcAuthConfig.getInstance();
                    authManager = OidcAuthenticationManager.getInstance();
                    LOGGER.info("OidcAuthorityProvider initialized");
                }
            }
        }
    }
}
