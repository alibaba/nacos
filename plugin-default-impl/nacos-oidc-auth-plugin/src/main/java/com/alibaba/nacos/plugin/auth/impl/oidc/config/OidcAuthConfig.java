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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OIDC authentication plugin configuration.
 *
 * @author WangzJi
 */
@SuppressWarnings("PMD")
public class OidcAuthConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthConfig.class);
    
    private static volatile OidcAuthConfig instance;
    
    private String issuerUri;
    
    private String clientId;
    
    private String clientSecret;
    
    private String scope;
    
    private String tokenValidationMethod;
    
    private long jwksCacheTtlSeconds;
    
    private String usernameClaim;
    
    private String rolesClaim;
    
    private String adminRole;
    
    private boolean autoCreateUser;
    
    /**
     * Whether to enforce strict nonce validation (default true).
     */
    private boolean strictNonceValidation;
    
    /**
     * Whether to enforce strict audience validation (default true).
     */
    private boolean strictAudienceValidation;
    
    /**
     * External authorization endpoint (IdP handles all authorization).
     */
    private String authorizationEvaluateEndpoint;
    
    private long authorizationTimeoutMs;
    
    /**
     * Discovered JWKS URI from OIDC well-known configuration.
     */
    private String jwksUri;
    
    private String authorizationEndpoint;
    
    private String tokenEndpoint;
    
    private String userinfoEndpoint;
    
    private String endSessionEndpoint;
    
    private OidcAuthConfig() {
        loadConfig();
    }
    
    /**
     * Get singleton instance.
     *
     * @return OidcAuthConfig instance
     */
    public static OidcAuthConfig getInstance() {
        if (instance == null) {
            synchronized (OidcAuthConfig.class) {
                if (instance == null) {
                    instance = new OidcAuthConfig();
                }
            }
        }
        return instance;
    }
    
    /**
     * Reload configuration from environment.
     */
    public void reload() {
        loadConfig();
    }
    
    private void loadConfig() {
        this.issuerUri = getProperty(OidcConstants.CONFIG_ISSUER_URI, "");
        this.clientId = getProperty(OidcConstants.CONFIG_CLIENT_ID, "");
        this.clientSecret = getProperty(OidcConstants.CONFIG_CLIENT_SECRET, "");
        this.scope = getProperty(OidcConstants.CONFIG_SCOPE, OidcConstants.DEFAULT_SCOPE);
        this.tokenValidationMethod = getProperty(OidcConstants.CONFIG_TOKEN_VALIDATION_METHOD,
            OidcConstants.DEFAULT_TOKEN_VALIDATION_METHOD);
        this.jwksCacheTtlSeconds = Long.parseLong(
            getProperty(OidcConstants.CONFIG_JWKS_CACHE_TTL,
                String.valueOf(OidcConstants.DEFAULT_JWKS_CACHE_TTL_SECONDS)));
        this.usernameClaim = getProperty(OidcConstants.CONFIG_USERNAME_CLAIM,
            OidcConstants.DEFAULT_USERNAME_CLAIM);
        this.rolesClaim =
            getProperty(OidcConstants.CONFIG_ROLES_CLAIM, OidcConstants.DEFAULT_ROLES_CLAIM);
        this.adminRole =
            getProperty(OidcConstants.CONFIG_ADMIN_ROLE, OidcConstants.DEFAULT_ADMIN_ROLE);
        this.autoCreateUser = Boolean.parseBoolean(
            getProperty(OidcConstants.CONFIG_AUTO_CREATE_USER, "true"));
        
        // Security validation settings
        this.strictNonceValidation = Boolean.parseBoolean(
            getProperty(OidcConstants.CONFIG_STRICT_NONCE_VALIDATION, "true"));
        this.strictAudienceValidation = Boolean.parseBoolean(
            getProperty(OidcConstants.CONFIG_STRICT_AUDIENCE_VALIDATION, "true"));
        
        // External authorization endpoint (IdP handles all authorization)
        this.authorizationEvaluateEndpoint =
            getProperty(OidcConstants.CONFIG_AUTHORIZATION_ENDPOINT, "");
        this.authorizationTimeoutMs = Long.parseLong(
            getProperty(OidcConstants.CONFIG_AUTHORIZATION_TIMEOUT_MS,
                String.valueOf(OidcConstants.DEFAULT_AUTHORIZATION_TIMEOUT_MS)));
        
        LOGGER.info("OIDC auth config loaded: issuerUri={}, clientId={}, tokenValidationMethod={}",
            issuerUri, clientId, tokenValidationMethod);
        
        // Perform OIDC Discovery
        if (StringUtils.isNotBlank(issuerUri)) {
            try {
                doOidcDiscovery(issuerUri);
            } catch (Exception e) {
                LOGGER.error("Failed to perform OIDC discovery for issuer: {}", issuerUri, e);
            }
        }
    }
    
    private void doOidcDiscovery(String issuer) {
        String discoveryUrl = issuer.replaceAll("/$", "") + OidcProtocolConstants.WELL_KNOWN_PATH;
        LOGGER.info("Fetching OIDC configuration from: {}", discoveryUrl);
        
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(5000))
                .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(discoveryUrl))
                .GET()
                .timeout(Duration.ofMillis(5000))
                .build();
            
            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode configNode = JacksonUtils.toObj(response.body());
                
                if (configNode != null) {
                    if (configNode.has(OidcProtocolConstants.DISCOVERY_AUTHORIZATION_ENDPOINT)) {
                        this.authorizationEndpoint = configNode
                            .get(OidcProtocolConstants.DISCOVERY_AUTHORIZATION_ENDPOINT).asText();
                    }
                    if (configNode.has(OidcProtocolConstants.DISCOVERY_TOKEN_ENDPOINT)) {
                        this.tokenEndpoint =
                            configNode.get(OidcProtocolConstants.DISCOVERY_TOKEN_ENDPOINT).asText();
                    }
                    if (configNode.has(OidcProtocolConstants.DISCOVERY_USERINFO_ENDPOINT)) {
                        this.userinfoEndpoint = configNode
                            .get(OidcProtocolConstants.DISCOVERY_USERINFO_ENDPOINT).asText();
                    }
                    if (configNode.has(OidcProtocolConstants.DISCOVERY_END_SESSION_ENDPOINT)) {
                        this.endSessionEndpoint = configNode
                            .get(OidcProtocolConstants.DISCOVERY_END_SESSION_ENDPOINT).asText();
                    }
                    if (configNode.has(OidcProtocolConstants.DISCOVERY_JWKS_URI)) {
                        this.jwksUri =
                            configNode.get(OidcProtocolConstants.DISCOVERY_JWKS_URI).asText();
                    }
                    
                    LOGGER.info("OIDC Discovery successful. Auth Endpoint: {}, Token Endpoint: {}",
                        authorizationEndpoint, tokenEndpoint);
                }
            } else {
                LOGGER.warn("OIDC Discovery failed with status: {}", response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.error("OIDC Discovery error", e);
        }
    }
    
    private String getProperty(String key, String defaultValue) {
        String value = EnvUtil.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
    
    /**
     * Check if the configuration is valid.
     *
     * @return true if configuration is valid
     */
    public boolean isValid() {
        return StringUtils.isNotBlank(issuerUri) && StringUtils.isNotBlank(clientId);
    }
    
    /**
     * Check if JWT validation method is used.
     *
     * @return true if JWT validation
     */
    public boolean isJwtValidation() {
        return "jwt".equalsIgnoreCase(tokenValidationMethod);
    }
    
    /**
     * Check if token introspection method is used.
     *
     * @return true if introspection validation
     */
    public boolean isIntrospectionValidation() {
        return "introspection".equalsIgnoreCase(tokenValidationMethod);
    }
    
    // ==================== Getters and Setters ====================
    
    public String getIssuerUri() {
        return issuerUri;
    }
    
    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public String getTokenValidationMethod() {
        return tokenValidationMethod;
    }
    
    public void setTokenValidationMethod(String tokenValidationMethod) {
        this.tokenValidationMethod = tokenValidationMethod;
    }
    
    public long getJwksCacheTtlSeconds() {
        return jwksCacheTtlSeconds;
    }
    
    public void setJwksCacheTtlSeconds(long jwksCacheTtlSeconds) {
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
    }
    
    public String getUsernameClaim() {
        return usernameClaim;
    }
    
    public void setUsernameClaim(String usernameClaim) {
        this.usernameClaim = usernameClaim;
    }
    
    public String getRolesClaim() {
        return rolesClaim;
    }
    
    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }
    
    public String getAdminRole() {
        return adminRole;
    }
    
    public void setAdminRole(String adminRole) {
        this.adminRole = adminRole;
    }
    
    public boolean isAutoCreateUser() {
        return autoCreateUser;
    }
    
    public void setAutoCreateUser(boolean autoCreateUser) {
        this.autoCreateUser = autoCreateUser;
    }
    
    public String getJwksUri() {
        return jwksUri;
    }
    
    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }
    
    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }
    
    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }
    
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }
    
    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }
    
    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }
    
    public void setUserinfoEndpoint(String userinfoEndpoint) {
        this.userinfoEndpoint = userinfoEndpoint;
    }
    
    public String getEndSessionEndpoint() {
        return endSessionEndpoint;
    }
    
    public void setEndSessionEndpoint(String endSessionEndpoint) {
        this.endSessionEndpoint = endSessionEndpoint;
    }
    
    public String getAuthorizationEvaluateEndpoint() {
        return authorizationEvaluateEndpoint;
    }
    
    public void setAuthorizationEvaluateEndpoint(String authorizationEvaluateEndpoint) {
        this.authorizationEvaluateEndpoint = authorizationEvaluateEndpoint;
    }
    
    public long getAuthorizationTimeoutMs() {
        return authorizationTimeoutMs;
    }
    
    public void setAuthorizationTimeoutMs(long authorizationTimeoutMs) {
        this.authorizationTimeoutMs = authorizationTimeoutMs;
    }
    
    public boolean isStrictNonceValidation() {
        return strictNonceValidation;
    }
    
    public void setStrictNonceValidation(boolean strictNonceValidation) {
        this.strictNonceValidation = strictNonceValidation;
    }
    
    public boolean isStrictAudienceValidation() {
        return strictAudienceValidation;
    }
    
    public void setStrictAudienceValidation(boolean strictAudienceValidation) {
        this.strictAudienceValidation = strictAudienceValidation;
    }
}
