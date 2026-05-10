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

package com.alibaba.nacos.plugin.auth.impl.oidc.constant;

/**
 * Server-specific OIDC authentication plugin constants.
 *
 * <p>Protocol-level constants (Discovery fields, OAuth2 parameters, HTTP headers, etc.)
 * are defined in {@link com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants}.
 *
 * @author WangzJi
 */
@SuppressWarnings("PMD")
public final class OidcConstants {
    
    private OidcConstants() {
    }
    
    /**
     * OIDC user key in identity context.
     */
    public static final String OAUTH2_USER_KEY = "oidc_user";
    
    // ==================== Configuration Keys ====================
    
    /**
     * Configuration prefix for OIDC plugin.
     */
    public static final String CONFIG_PREFIX = "nacos.core.auth.plugin.oidc.";
    
    /**
     * OIDC issuer URI (for auto-discovery).
     */
    public static final String CONFIG_ISSUER_URI = CONFIG_PREFIX + "issuer-uri";
    
    /**
     * OIDC client ID.
     */
    public static final String CONFIG_CLIENT_ID = CONFIG_PREFIX + "client-id";
    
    /**
     * OIDC client secret.
     */
    public static final String CONFIG_CLIENT_SECRET = CONFIG_PREFIX + "client-secret";
    
    /**
     * OIDC scopes.
     */
    public static final String CONFIG_SCOPE = CONFIG_PREFIX + "scope";
    
    /**
     * Token validation method: jwt or introspection.
     */
    public static final String CONFIG_TOKEN_VALIDATION_METHOD =
        CONFIG_PREFIX + "token-validation-method";
    
    /**
     * JWKS cache TTL in seconds.
     */
    public static final String CONFIG_JWKS_CACHE_TTL = CONFIG_PREFIX + "jwks-cache-ttl-seconds";
    
    /**
     * Username claim in ID token.
     */
    public static final String CONFIG_USERNAME_CLAIM = CONFIG_PREFIX + "username-claim";
    
    /**
     * Roles claim in ID token.
     */
    public static final String CONFIG_ROLES_CLAIM = CONFIG_PREFIX + "roles-claim";
    
    /**
     * Admin role name in OIDC claims.
     */
    public static final String CONFIG_ADMIN_ROLE = CONFIG_PREFIX + "admin-role";
    
    /**
     * Auto create user on first login.
     */
    public static final String CONFIG_AUTO_CREATE_USER = CONFIG_PREFIX + "auto-create-user";
    
    /**
     * External authorization endpoint (IdP handles all authorization).
     */
    public static final String CONFIG_AUTHORIZATION_ENDPOINT =
        CONFIG_PREFIX + "authorization-endpoint";
    
    /**
     * Authorization request timeout in milliseconds.
     */
    public static final String CONFIG_AUTHORIZATION_TIMEOUT_MS =
        CONFIG_PREFIX + "authorization-timeout-ms";
    
    /**
     * Whether to enforce strict nonce validation.
     */
    public static final String CONFIG_STRICT_NONCE_VALIDATION =
        CONFIG_PREFIX + "strict-nonce-validation";
    
    /**
     * Whether to enforce strict audience validation.
     */
    public static final String CONFIG_STRICT_AUDIENCE_VALIDATION =
        CONFIG_PREFIX + "strict-audience-validation";
    
    // ==================== Default Values ====================
    
    /**
     * Default token validation method.
     */
    public static final String DEFAULT_TOKEN_VALIDATION_METHOD = "jwt";
    
    /**
     * Default JWKS cache TTL: 1 hour.
     */
    public static final long DEFAULT_JWKS_CACHE_TTL_SECONDS = 3600L;
    
    /**
     * Default username claim.
     */
    public static final String DEFAULT_USERNAME_CLAIM = "preferred_username";
    
    /**
     * Default roles claim.
     */
    public static final String DEFAULT_ROLES_CLAIM = "roles";
    
    /**
     * Default admin role.
     */
    public static final String DEFAULT_ADMIN_ROLE = "nacos-admin";
    
    /**
     * Default scope.
     */
    public static final String DEFAULT_SCOPE = "openid profile email";
    
    /**
     * Default authorization timeout in milliseconds: 5 seconds.
     */
    public static final long DEFAULT_AUTHORIZATION_TIMEOUT_MS = 5000L;
    
    // ==================== HTTP Status Codes (server-specific) ====================
    
    /**
     * HTTP 401 Unauthorized status code.
     */
    public static final int HTTP_STATUS_UNAUTHORIZED = 401;
    
    /**
     * HTTP 403 Forbidden status code.
     */
    public static final int HTTP_STATUS_FORBIDDEN = 403;
    
    // ==================== HTTP Constants (server-specific) ====================
    
    /**
     * HTTP protocol prefix.
     */
    public static final String HTTP_PROTOCOL = "http";
    
    /**
     * HTTPS protocol prefix.
     */
    public static final String HTTPS_PROTOCOL = "https";
    
    /**
     * Default HTTP port.
     */
    public static final int DEFAULT_HTTP_PORT = 80;
    
    /**
     * Default HTTPS port.
     */
    public static final int DEFAULT_HTTPS_PORT = 443;
    
    /**
     * Question mark for URL query string.
     */
    public static final String QUERY_STRING_SEPARATOR = "?";
    
    // ==================== JSON Field Names ====================
    
    /**
     * JSON field name for allowed.
     */
    public static final String JSON_FIELD_ALLOWED = "\"allowed\"";
    
    /**
     * JSON field name for result.
     */
    public static final String JSON_FIELD_RESULT = "\"result\"";
    
    /**
     * JSON field name for decision.
     */
    public static final String JSON_FIELD_DECISION = "\"decision\"";
}
