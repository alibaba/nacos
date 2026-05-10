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

package com.alibaba.nacos.plugin.auth.impl.oidc.authenticate;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationClient;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationRequest;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationResponse;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OIDC authentication manager.
 * Handles token validation and user authentication.
 *
 * @author WangzJi
 */
@SuppressWarnings("PMD")
public class OidcAuthenticationManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthenticationManager.class);
    
    private static volatile OidcAuthenticationManager instance;
    
    private final OidcAuthConfig config;
    
    private final JwtTokenValidator tokenValidator;
    
    private final OidcUserMapper userMapper;
    
    private OidcAuthenticationManager() {
        this.config = OidcAuthConfig.getInstance();
        this.tokenValidator = JwtTokenValidator.getInstance();
        this.userMapper = OidcUserMapper.getInstance();
    }
    
    /**
     * Get singleton instance.
     *
     * @return OidcAuthenticationManager instance
     */
    public static OidcAuthenticationManager getInstance() {
        if (instance == null) {
            synchronized (OidcAuthenticationManager.class) {
                if (instance == null) {
                    instance = new OidcAuthenticationManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Authenticate user by JWT token.
     *
     * @param token JWT token (Access Token or ID Token)
     * @return authenticated OidcUser
     * @throws AccessException if authentication fails
     */
    public OidcUser authenticate(String token) throws AccessException {
        if (StringUtils.isBlank(token)) {
            throw new AccessException("Token is required");
        }
        
        // Validate the token
        JWTClaimsSet claims = tokenValidator.validate(token);
        
        // Map claims to user
        OidcUser user = userMapper.mapToUser(claims);
        user.setToken(token);
        
        LOGGER.debug("User authenticated: {}", user.getUsername());
        return user;
    }
    
    /**
     * Authenticate user from identity context.
     *
     * @param identityContext identity context containing credentials
     * @return authenticated OidcUser
     * @throws AccessException if authentication fails
     */
    public OidcUser authenticate(IdentityContext identityContext) throws AccessException {
        // Try to extract Bearer token from Authorization header
        String token = extractBearerToken(identityContext);
        
        if (StringUtils.isBlank(token)) {
            // Try accessToken parameter
            token = identityContext.getParameter(OidcProtocolConstants.ACCESS_TOKEN_PARAM, "");
        }
        
        if (StringUtils.isBlank(token)) {
            throw new AccessException("No valid OIDC token found");
        }
        
        return authenticate(token);
    }
    
    /**
     * Extract Bearer token from identity context.
     *
     * @param identityContext identity context
     * @return token string or null
     */
    private String extractBearerToken(IdentityContext identityContext) {
        String authHeader =
            identityContext.getParameter(OidcProtocolConstants.AUTHORIZATION_HEADER, "");
        if (StringUtils.isNotBlank(authHeader)
            && authHeader.startsWith(OidcProtocolConstants.BEARER_PREFIX)) {
            return authHeader.substring(OidcProtocolConstants.BEARER_PREFIX.length());
        }
        return null;
    }
    
    /**
     * Check if user has permission to access resource.
     * Delegates authorization decision to external IdP - Nacos does NOT make the decision.
     *
     * @param user       OidcUser
     * @param permission permission to check
     * @return true if user has permission (as determined by IdP)
     */
    public boolean hasPermission(OidcUser user, Permission permission) {
        if (user == null) {
            return false;
        }
        
        // Build authorization request
        AuthorizationRequest request = AuthorizationRequest.builder()
            .token(user.getToken())
            .resourceType(permission.getResource().getType())
            .namespace(permission.getResource().getNamespaceId())
            .group(permission.getResource().getGroup())
            .resourceName(permission.getResource().getName())
            .action(permission.getAction())
            .build();
        
        // Call IdP authorization endpoint - Nacos does NOT make the decision
        AuthorizationClient authzClient = AuthorizationClient.getInstance();
        AuthorizationResponse response = authzClient.authorize(request);
        
        if (response.isAllowed()) {
            LOGGER.debug("IdP authorized user {} for {}:{}", user.getUsername(),
                request.buildResourceUri(), permission.getAction());
            return true;
        } else {
            LOGGER.debug("IdP denied user {} access to {}:{}, reason: {}", user.getUsername(),
                request.buildResourceUri(), permission.getAction(), response.getReason());
            return false;
        }
    }
    
    /**
     * Check if user is a global administrator.
     *
     * @param user OidcUser
     * @return true if admin
     */
    public boolean isGlobalAdmin(OidcUser user) {
        if (user == null) {
            return false;
        }
        return user.isGlobalAdmin();
    }
    
    /**
     * Get user from identity context (if already authenticated).
     *
     * @param identityContext identity context
     * @return OidcUser or null
     */
    public OidcUser getUserFromContext(IdentityContext identityContext) {
        Object user = identityContext.getParameter(OidcConstants.OAUTH2_USER_KEY);
        if (user instanceof OidcUser) {
            return (OidcUser) user;
        }
        return null;
    }
    
    /**
     * Store user in identity context.
     *
     * @param identityContext identity context
     * @param user            OidcUser to store
     */
    public void setUserInContext(IdentityContext identityContext, OidcUser user) {
        identityContext.setParameter(OidcConstants.OAUTH2_USER_KEY, user);
    }
}
