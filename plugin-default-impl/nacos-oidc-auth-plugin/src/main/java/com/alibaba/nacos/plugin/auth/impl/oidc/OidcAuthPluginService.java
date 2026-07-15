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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.OidcAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.oidc.authorization.AuthorizationClient;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadataProvider;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwksProvider;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * OIDC authentication plugin service implementation.
 *
 * @author WangzJi
 */
@SuppressWarnings({"PMD", "deprecation"})
public class OidcAuthPluginService implements AuthPluginService, PluginConfigSpec {
    
    private static final List<String> IDENTITY_NAMES = Arrays.asList(
        OidcProtocolConstants.AUTHORIZATION_HEADER, OidcProtocolConstants.ACCESS_TOKEN_PARAM);
    
    private static final List<ConfigItemDefinition> CONFIG_DEFINITIONS =
        buildConfigDefinitions();
    
    private volatile RuntimeContext runtime = new RuntimeContext(OidcAuthPluginConfig.defaults());
    
    private static List<ConfigItemDefinition> buildConfigDefinitions() {
        ConfigItemDefinition issuerUri = restartDefinition(OidcAuthPluginConfig.ISSUER_URI,
            "OIDC issuer URI", ConfigItemType.STRING, "",
            "OIDC issuer URI used for Provider discovery; required when OIDC auth is selected",
            OidcConstants.CONFIG_ISSUER_URI);
        ConfigItemDefinition clientId = restartDefinition(OidcAuthPluginConfig.CLIENT_ID,
            "OIDC client ID", ConfigItemType.STRING, "",
            "OAuth2 client ID; required when OIDC auth is selected",
            OidcConstants.CONFIG_CLIENT_ID);
        ConfigItemDefinition clientSecret = restartDefinition(
            OidcAuthPluginConfig.CLIENT_SECRET, "OIDC client secret", ConfigItemType.STRING, "",
            "OAuth2 client secret used by authorization code login and signed state",
            OidcConstants.CONFIG_CLIENT_SECRET);
        clientSecret.setSensitive(true);
        ConfigItemDefinition scope = restartDefinition(OidcAuthPluginConfig.SCOPE,
            "OIDC scopes", ConfigItemType.STRING, OidcAuthPluginConfig.DEFAULT_SCOPE,
            "Space-separated scopes requested during authorization code login",
            OidcConstants.CONFIG_SCOPE);
        ConfigItemDefinition tokenValidationMethod = restartDefinition(
            OidcAuthPluginConfig.TOKEN_VALIDATION_METHOD, "Token validation method",
            ConfigItemType.STRING, OidcAuthPluginConfig.DEFAULT_TOKEN_VALIDATION_METHOD,
            "Declared token validation mode; the current implementation supports jwt only",
            OidcConstants.CONFIG_TOKEN_VALIDATION_METHOD);
        ConfigItemDefinition jwksCacheTtl = restartDefinition(
            OidcAuthPluginConfig.JWKS_CACHE_TTL_SECONDS, "JWKS cache TTL",
            ConfigItemType.NUMBER,
            Long.toString(OidcAuthPluginConfig.DEFAULT_JWKS_CACHE_TTL_SECONDS),
            "JWKS cache lifetime in seconds", OidcConstants.CONFIG_JWKS_CACHE_TTL);
        ConfigItemDefinition usernameClaim = restartDefinition(
            OidcAuthPluginConfig.USERNAME_CLAIM, "Username claim", ConfigItemType.STRING,
            OidcAuthPluginConfig.DEFAULT_USERNAME_CLAIM,
            "JWT claim used as the Nacos username", OidcConstants.CONFIG_USERNAME_CLAIM);
        ConfigItemDefinition rolesClaim = restartDefinition(OidcAuthPluginConfig.ROLES_CLAIM,
            "Roles claim", ConfigItemType.STRING, OidcAuthPluginConfig.DEFAULT_ROLES_CLAIM,
            "JWT claim containing user roles", OidcConstants.CONFIG_ROLES_CLAIM);
        ConfigItemDefinition adminRole = restartDefinition(OidcAuthPluginConfig.ADMIN_ROLE,
            "Administrator role", ConfigItemType.STRING,
            OidcAuthPluginConfig.DEFAULT_ADMIN_ROLE,
            "OIDC role treated as the Nacos global administrator",
            OidcConstants.CONFIG_ADMIN_ROLE);
        ConfigItemDefinition autoCreateUser = restartDefinition(
            OidcAuthPluginConfig.AUTO_CREATE_USER, "Auto-create user", ConfigItemType.BOOLEAN,
            Boolean.toString(OidcAuthPluginConfig.DEFAULT_AUTO_CREATE_USER),
            "Reserved compatibility setting; it does not change current runtime behavior",
            OidcConstants.CONFIG_AUTO_CREATE_USER);
        ConfigItemDefinition authorizationEndpoint = restartDefinition(
            OidcAuthPluginConfig.AUTHORIZATION_ENDPOINT, "Authorization decision endpoint",
            ConfigItemType.STRING, "", "External endpoint used for authorization decisions",
            OidcConstants.CONFIG_AUTHORIZATION_ENDPOINT);
        ConfigItemDefinition authorizationTimeout = restartDefinition(
            OidcAuthPluginConfig.AUTHORIZATION_TIMEOUT_MS, "Authorization timeout",
            ConfigItemType.NUMBER,
            Long.toString(OidcAuthPluginConfig.DEFAULT_AUTHORIZATION_TIMEOUT_MS),
            "External authorization request timeout in milliseconds",
            OidcConstants.CONFIG_AUTHORIZATION_TIMEOUT_MS);
        ConfigItemDefinition strictNonce = restartDefinition(
            OidcAuthPluginConfig.STRICT_NONCE_VALIDATION, "Strict nonce validation",
            ConfigItemType.BOOLEAN,
            Boolean.toString(OidcAuthPluginConfig.DEFAULT_STRICT_NONCE_VALIDATION),
            "Require authorization code ID tokens to contain the expected nonce",
            OidcConstants.CONFIG_STRICT_NONCE_VALIDATION);
        ConfigItemDefinition strictAudience = restartDefinition(
            OidcAuthPluginConfig.STRICT_AUDIENCE_VALIDATION, "Strict audience validation",
            ConfigItemType.BOOLEAN,
            Boolean.toString(OidcAuthPluginConfig.DEFAULT_STRICT_AUDIENCE_VALIDATION),
            "Reject tokens whose audience and authorized party do not match the client ID",
            OidcConstants.CONFIG_STRICT_AUDIENCE_VALIDATION);
        return Collections.unmodifiableList(Arrays.asList(issuerUri, clientId, clientSecret,
            scope, tokenValidationMethod, jwksCacheTtl, usernameClaim, rolesClaim, adminRole,
            autoCreateUser, authorizationEndpoint, authorizationTimeout, strictNonce,
            strictAudience));
    }
    
    private static ConfigItemDefinition restartDefinition(String key, String name,
        ConfigItemType type, String defaultValue, String description, String alias) {
        return new ConfigItemDefinition.Builder(key, name, type).description(description)
            .defaultValue(defaultValue).aliases(Collections.singletonList(alias))
            .effectMode(ConfigItemEffectMode.RESTART).build();
    }
    
    @Override
    public Collection<String> identityNames() {
        return IDENTITY_NAMES;
    }
    
    @Override
    public boolean enableAuth(ActionTypes action, String type) {
        return true;
    }
    
    @Override
    public AuthResult validateIdentity(IdentityContext identityContext, Resource resource) {
        return runtime.identityProvider.validateIdentity(identityContext, resource);
    }
    
    @Override
    public AuthResult validateAuthority(IdentityContext identityContext, Permission permission) {
        return runtime.authorityProvider.validateAuthority(identityContext, permission);
    }
    
    @Override
    public String getAuthServiceName() {
        return OidcProtocolConstants.AUTH_PLUGIN_TYPE;
    }
    
    @Override
    public boolean isLoginEnabled() {
        return true;
    }
    
    @Override
    public boolean isAdminRequest() {
        return false;
    }
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return CONFIG_DEFINITIONS;
    }
    
    @Override
    public synchronized void applyConfig(Map<String, String> effectiveConfig) {
        runtime = new RuntimeContext(OidcAuthPluginConfig.from(effectiveConfig));
    }
    
    @Override
    public Map<String, String> getCurrentConfig() {
        return runtime.config.toMap();
    }
    
    public OidcAuthPluginConfig getConfig() {
        return runtime.config;
    }
    
    public boolean isConfigurationValid() {
        return runtime.config.isValid();
    }
    
    public String buildAuthorizationUrl(String redirectUri) throws AccessException {
        return runtime.authorizationCodeHandler.buildAuthorizationUrl(redirectUri);
    }
    
    public OidcUser exchangeCodeForUser(String code, String state, String redirectUri)
        throws AccessException {
        return runtime.authorizationCodeHandler.exchangeCodeForUser(code, state, redirectUri);
    }
    
    public String buildLogoutUrl(String idToken, String redirectUri) {
        return runtime.authorizationCodeHandler.buildLogoutUrl(idToken, redirectUri);
    }
    
    private static final class RuntimeContext {
        
        private final OidcAuthPluginConfig config;
        
        private final OidcIdentityProvider identityProvider;
        
        private final OidcAuthorityProvider authorityProvider;
        
        private final AuthorizationCodeHandler authorizationCodeHandler;
        
        private RuntimeContext(OidcAuthPluginConfig config) {
            this.config = config;
            OidcProviderMetadataProvider metadataProvider =
                new OidcProviderMetadataProvider(config);
            JwksProvider jwksProvider = new JwksProvider(config, metadataProvider);
            JwtTokenValidator tokenValidator = new JwtTokenValidator(config, jwksProvider);
            OidcUserMapper userMapper = new OidcUserMapper(tokenValidator);
            AuthorizationClient authorizationClient = new AuthorizationClient(config);
            OidcAuthenticationManager authenticationManager = new OidcAuthenticationManager(
                tokenValidator, userMapper, authorizationClient);
            this.identityProvider = new OidcIdentityProvider(authenticationManager);
            this.authorityProvider = new OidcAuthorityProvider(authenticationManager);
            this.authorizationCodeHandler = new AuthorizationCodeHandler(config,
                metadataProvider, tokenValidator, userMapper);
        }
    }
}
