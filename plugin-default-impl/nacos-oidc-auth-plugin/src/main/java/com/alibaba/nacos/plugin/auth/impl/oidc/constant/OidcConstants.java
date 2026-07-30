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

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;

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
     * Legacy configuration prefix for OIDC plugin.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.} instead. Planned for removal in Nacos
     *     4.0.0.
     */
    @Deprecated
    public static final String CONFIG_PREFIX = "nacos.core.auth.plugin.oidc.";
    
    /**
     * Legacy OIDC issuer URI key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.issuer-uri} instead. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_ISSUER_URI = CONFIG_PREFIX + "issuer-uri";
    
    /**
     * Legacy OIDC client ID key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.client-id} instead. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_CLIENT_ID = CONFIG_PREFIX + "client-id";
    
    /**
     * Legacy OIDC client secret key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.client-secret} instead. Planned for removal
     *     in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_CLIENT_SECRET = CONFIG_PREFIX + "client-secret";
    
    /**
     * Legacy OIDC scopes key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.scope} instead. Planned for removal in Nacos
     *     4.0.0.
     */
    @Deprecated
    public static final String CONFIG_SCOPE = CONFIG_PREFIX + "scope";
    
    /**
     * Legacy token validation method key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.token-validation-method} instead. Planned
     *     for removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_TOKEN_VALIDATION_METHOD =
        CONFIG_PREFIX + "token-validation-method";
    
    /**
     * Legacy JWKS cache TTL key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.jwks-cache-ttl-seconds} instead. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_JWKS_CACHE_TTL = CONFIG_PREFIX + "jwks-cache-ttl-seconds";
    
    /**
     * Legacy username claim key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.username-claim} instead. Planned for removal
     *     in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_USERNAME_CLAIM = CONFIG_PREFIX + "username-claim";
    
    /**
     * Legacy roles claim key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.roles-claim} instead. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_ROLES_CLAIM = CONFIG_PREFIX + "roles-claim";
    
    /**
     * Legacy admin role key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.admin-role} instead. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_ADMIN_ROLE = CONFIG_PREFIX + "admin-role";
    
    /**
     * Legacy auto create user key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.auto-create-user} instead. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_AUTO_CREATE_USER = CONFIG_PREFIX + "auto-create-user";
    
    /**
     * Legacy external authorization endpoint key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.authorization-endpoint} instead. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_AUTHORIZATION_ENDPOINT =
        CONFIG_PREFIX + "authorization-endpoint";
    
    /**
     * Legacy authorization request timeout key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.authorization-timeout-ms} instead. Planned
     *     for removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_AUTHORIZATION_TIMEOUT_MS =
        CONFIG_PREFIX + "authorization-timeout-ms";
    
    /**
     * Legacy strict nonce validation key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.strict-nonce-validation} instead. Planned
     *     for removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_STRICT_NONCE_VALIDATION =
        CONFIG_PREFIX + "strict-nonce-validation";
    
    /**
     * Legacy strict audience validation key.
     *
     * @deprecated Use {@code nacos.plugin.auth.oidc.strict-audience-validation} instead.
     *     Planned for removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String CONFIG_STRICT_AUDIENCE_VALIDATION =
        CONFIG_PREFIX + "strict-audience-validation";
    
    // ==================== Default Values ====================
    
    /**
     * Default token validation method.
     */
    public static final String DEFAULT_TOKEN_VALIDATION_METHOD =
        OidcAuthPluginConfig.DEFAULT_TOKEN_VALIDATION_METHOD;
    
    /**
     * Default JWKS cache TTL: 1 hour.
     */
    public static final long DEFAULT_JWKS_CACHE_TTL_SECONDS =
        OidcAuthPluginConfig.DEFAULT_JWKS_CACHE_TTL_SECONDS;
    
    /**
     * Default username claim.
     */
    public static final String DEFAULT_USERNAME_CLAIM =
        OidcAuthPluginConfig.DEFAULT_USERNAME_CLAIM;
    
    /**
     * Default roles claim.
     */
    public static final String DEFAULT_ROLES_CLAIM = OidcAuthPluginConfig.DEFAULT_ROLES_CLAIM;
    
    /**
     * Default admin role.
     */
    public static final String DEFAULT_ADMIN_ROLE = OidcAuthPluginConfig.DEFAULT_ADMIN_ROLE;
    
    /**
     * Default scope.
     */
    public static final String DEFAULT_SCOPE = OidcAuthPluginConfig.DEFAULT_SCOPE;
    
    /**
     * Default authorization timeout in milliseconds: 5 seconds.
     */
    public static final long DEFAULT_AUTHORIZATION_TIMEOUT_MS =
        OidcAuthPluginConfig.DEFAULT_AUTHORIZATION_TIMEOUT_MS;
    
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
