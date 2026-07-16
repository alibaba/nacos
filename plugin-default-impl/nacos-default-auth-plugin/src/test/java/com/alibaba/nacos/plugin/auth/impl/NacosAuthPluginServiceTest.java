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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants.Identity;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAuthPluginServiceTest {
    
    private static final String RAW_SECRET =
        "SecretKey0123$567890$234567890123456789012345678901234567890123456789";
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    private NacosAuthPluginService authPluginService;
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        setAuthEnabled(true, true);
        authPluginService = new NacosAuthPluginService();
        ReflectionTestUtils.setField(authPluginService, "authenticationManager",
            authenticationManager);
    }
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cachedConfigMap);
    }
    
    @Test
    void testMetadataMethods() {
        assertTrue(authPluginService.identityNames().contains(AuthConstants.AUTHORIZATION_HEADER));
        assertTrue(authPluginService.identityNames().contains(Constants.ACCESS_TOKEN));
        assertTrue(authPluginService.enableAuth(ActionTypes.READ, "naming"));
        assertEquals(AuthConstants.AUTH_PLUGIN_TYPE, authPluginService.getAuthServiceName());
        List<ConfigItemDefinition> definitions = authPluginService.getConfigDefinitions();
        assertEquals(5, definitions.size());
        assertDefinition(definitions.get(0), NacosAuthPluginConfig.TOKEN_SECRET_KEY,
            AuthConstants.TOKEN_SECRET_KEY, ConfigItemEffectMode.RESTART, true);
        assertDefinition(definitions.get(1), NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS,
            AuthConstants.TOKEN_EXPIRE_SECONDS, ConfigItemEffectMode.RUNTIME, false);
        assertDefinition(definitions.get(2), NacosAuthPluginConfig.TOKEN_CACHE_ENABLE,
            AuthConstants.TOKEN_CACHE_ENABLE, ConfigItemEffectMode.RUNTIME, false);
        assertDefinition(definitions.get(3), NacosAuthPluginConfig.CACHING_ENABLED,
            AuthConstants.NACOS_CORE_AUTH_CACHING_ENABLED, ConfigItemEffectMode.RUNTIME, false);
        assertDefinition(definitions.get(4), NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED,
            AuthConstants.NACOS_CORE_AUTH_NACOS_ANONYMOUS_AI_ENABLED,
            ConfigItemEffectMode.RUNTIME, false);
        assertThrows(UnsupportedOperationException.class,
            () -> definitions.add(new ConfigItemDefinition()));
    }
    
    @Test
    void testApplyConfigAndRuntimeTokenChanges() throws AccessException {
        Map<String, String> config = validConfig(false, false);
        authPluginService.applyConfig(config);
        JwtTokenManager firstManager = (JwtTokenManager) ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), "tokenManager");
        CachedJwtTokenManager firstCache = (CachedJwtTokenManager) ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), "cachedTokenManager");
        assertSame(firstManager, activeTokenManager());
        assertEquals(config, authPluginService.getCurrentConfig());
        assertEquals(config, authPluginService.getConfig().toMap());
        assertTrue(authPluginService.getTokenManagerDelegate().createToken("nacos").length() > 0);
        
        config.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "123");
        authPluginService.applyConfig(config);
        assertSame(firstManager,
            ReflectionTestUtils.getField(authPluginService.getTokenManagerDelegate(),
                "tokenManager"));
        assertSame(firstCache,
            ReflectionTestUtils.getField(authPluginService.getTokenManagerDelegate(),
                "cachedTokenManager"));
        assertEquals(123L, firstManager.getTokenValidityInSeconds());
        
        config.put(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE, "true");
        authPluginService.applyConfig(config);
        assertSame(firstCache, activeTokenManager());
        authPluginService.getTokenManagerDelegate().createToken("cached-user");
        assertCacheNotEmpty(firstCache);
        authPluginService.applyConfig(config);
        assertSame(firstCache,
            ReflectionTestUtils.getField(authPluginService.getTokenManagerDelegate(),
                "cachedTokenManager"));
        assertCacheNotEmpty(firstCache);
        
        config.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "456");
        authPluginService.applyConfig(config);
        assertCacheEmpty(firstCache);
        authPluginService.getTokenManagerDelegate().createToken("ttl-cached-user");
        
        config.put(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE, "false");
        authPluginService.applyConfig(config);
        assertInstanceOf(JwtTokenManager.class, activeTokenManager());
        assertSame(firstManager,
            ReflectionTestUtils.getField(authPluginService.getTokenManagerDelegate(),
                "tokenManager"));
        assertSame(firstCache,
            ReflectionTestUtils.getField(authPluginService.getTokenManagerDelegate(),
                "cachedTokenManager"));
        assertCacheEmpty(firstCache);
    }
    
    @Test
    void testRestartTokenSecretChangeRejectedAfterInitialization() {
        Map<String, String> config = validConfig(false, false);
        authPluginService.applyConfig(config);
        Map<String, String> accepted = authPluginService.getCurrentConfig();
        Object firstManager = ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), "tokenManager");
        Object firstCache = ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), "cachedTokenManager");
        config.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY,
            encodedSecret(RAW_SECRET + "changed"));
        assertThrows(IllegalArgumentException.class,
            () -> authPluginService.applyConfig(config));
        assertEquals(accepted, authPluginService.getCurrentConfig());
        assertSame(firstManager, ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), "tokenManager"));
        assertSame(firstCache, ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), "cachedTokenManager"));
    }
    
    @Test
    void testApplyConfigValidationDoesNotReplaceAcceptedConfig() {
        Map<String, String> valid = validConfig(false, false);
        authPluginService.applyConfig(valid);
        Map<String, String> accepted = authPluginService.getCurrentConfig();
        Map<String, String> invalid = new LinkedHashMap<>(valid);
        invalid.put(NacosAuthPluginConfig.CACHING_ENABLED, "invalid");
        assertThrows(IllegalArgumentException.class,
            () -> authPluginService.applyConfig(invalid));
        assertEquals(accepted, authPluginService.getCurrentConfig());
        invalid.put(NacosAuthPluginConfig.CACHING_ENABLED, "true");
        invalid.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY, "invalid");
        assertThrows(IllegalArgumentException.class,
            () -> authPluginService.applyConfig(invalid));
        assertEquals(accepted, authPluginService.getCurrentConfig());
    }
    
    @Test
    void testBlankSecretAllowedOnlyWhenAllAuthDisabled() throws AccessException {
        setAuthEnabled(false, false);
        authPluginService.applyConfig(NacosAuthPluginConfig.defaults().toMap());
        assertEquals("AUTH_DISABLED",
            authPluginService.getTokenManagerDelegate().createToken("nacos"));
        setAuthEnabled(true, false);
        assertThrows(IllegalArgumentException.class,
            () -> authPluginService.applyConfig(NacosAuthPluginConfig.defaults().toMap()));
    }
    
    @Test
    void testAnonymousReconcileRequestedOnlyWhenEnabled() {
        AnonymousAccessInitializer initializer = mock(AnonymousAccessInitializer.class);
        authPluginService.setAnonymousAccessInitializer(initializer);
        verify(initializer, times(0)).requestReconcile();
        authPluginService.applyConfig(validConfig(false, true));
        verify(initializer).requestReconcile();
        authPluginService.applyConfig(validConfig(false, true));
        verify(initializer, times(2)).requestReconcile();
        NacosAuthPluginService enabledBeforeAttach = new NacosAuthPluginService();
        enabledBeforeAttach.applyConfig(validConfig(false, true));
        enabledBeforeAttach.setAnonymousAccessInitializer(initializer);
        verify(initializer, times(3)).requestReconcile();
    }
    
    @Test
    void testValidateIdentityAnonymousAllowedAndDenied() throws AccessException {
        when(authenticationManager.authenticate(any(), any()))
            .thenThrow(new AccessException("no credentials"));
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TAG_ALLOW_ANONYMOUS, "true");
        Resource resource = new Resource("ns", "g", "name", "type", properties);
        AuthResult<?> disabled =
            authPluginService.validateIdentity(new IdentityContext(), resource);
        assertFalse(disabled.isSuccess());
        authPluginService.applyConfig(validConfig(false, true));
        IdentityContext identityContext = new IdentityContext();
        AuthResult<?> enabled = authPluginService.validateIdentity(identityContext, resource);
        assertTrue(enabled.isSuccess());
        assertEquals(AuthConstants.ANONYMOUS_USER,
            ((NacosUser) enabled.getData()).getUserName());
        assertEquals(AuthConstants.ANONYMOUS_USER,
            ((NacosUser) identityContext.getParameter(AuthConstants.NACOS_USER_KEY)).getUserName());
        assertEquals(AuthConstants.ANONYMOUS_USER,
            identityContext.getParameter(Identity.IDENTITY_ID, ""));

        IdentityContext invalidBearerContext = new IdentityContext();
        invalidBearerContext.setParameter(AuthConstants.AUTHORIZATION_HEADER,
            AuthConstants.TOKEN_PREFIX + "invalid-token");
        when(authenticationManager.authenticate("invalid-token"))
            .thenThrow(new AccessException("invalid token"));
        AuthResult<?> invalidBearer = authPluginService.validateIdentity(invalidBearerContext,
            resource);
        assertFalse(invalidBearer.isSuccess());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), invalidBearer.getErrorCode());

        assertFalse(authPluginService.validateIdentity(new IdentityContext(), null).isSuccess());
        Resource noProperties = new Resource("ns", "g", "name", "type", null);
        assertFalse(authPluginService.validateIdentity(new IdentityContext(), noProperties)
            .isSuccess());
        AbstractNacosAuthPluginService nonConfigurableService =
            new AbstractNacosAuthPluginService() {
                
                @Override
                public String getAuthServiceName() {
                    return "test";
                }
            };
        ReflectionTestUtils.setField(nonConfigurableService, "authenticationManager",
            authenticationManager);
        assertFalse(nonConfigurableService.validateIdentity(new IdentityContext(), resource)
            .isSuccess());
    }
    
    @Test
    void testValidateIdentityWithTokenAndPassword() throws AccessException {
        NacosUser tokenUser = new NacosUser("token-user");
        when(authenticationManager.authenticate("jwt-token")).thenReturn(tokenUser);
        IdentityContext accessTokenContext = new IdentityContext();
        accessTokenContext.setParameter(Constants.ACCESS_TOKEN, "jwt-token");
        assertEquals(tokenUser, authPluginService.validateIdentity(accessTokenContext,
            Resource.EMPTY_RESOURCE).getData());
        IdentityContext bearerContext = new IdentityContext();
        bearerContext.setParameter(AuthConstants.AUTHORIZATION_HEADER,
            AuthConstants.TOKEN_PREFIX + "jwt-token");
        assertEquals(tokenUser, authPluginService.validateIdentity(bearerContext,
            Resource.EMPTY_RESOURCE).getData());
        NacosUser passwordUser = new NacosUser("password-user");
        when(authenticationManager.authenticate("nacos", "password")).thenReturn(passwordUser);
        IdentityContext passwordContext = new IdentityContext();
        passwordContext.setParameter(AuthConstants.PARAM_USERNAME, "nacos");
        passwordContext.setParameter(AuthConstants.PARAM_PASSWORD, "password");
        assertEquals(passwordUser, authPluginService.validateIdentity(passwordContext,
            Resource.EMPTY_RESOURCE).getData());
    }
    
    @Test
    void testValidateAuthoritySuccessAndFailure() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        NacosUser user = new NacosUser("nacos");
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, user);
        Permission permission = new Permission();
        assertTrue(authPluginService.validateAuthority(identityContext, permission).isSuccess());
        verify(authenticationManager).authorize(permission, user);
        doThrow(new AccessException("forbidden")).when(authenticationManager)
            .authorize(permission, user);
        AuthResult<?> failure = authPluginService.validateAuthority(identityContext, permission);
        assertFalse(failure.isSuccess());
        assertEquals(HttpStatus.FORBIDDEN.value(), failure.getErrorCode());
    }
    
    @Test
    void testAuthenticationManagerLazyLookup() throws AccessException {
        ReflectionTestUtils.setField(authPluginService, "authenticationManager", null);
        NacosUser user = new NacosUser("nacos");
        when(authenticationManager.authenticate("token")).thenReturn(user);
        try (MockedStatic<ApplicationUtils> applicationUtils = mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(() -> ApplicationUtils.getBean(IAuthenticationManager.class))
                .thenReturn(authenticationManager);
            IdentityContext context = new IdentityContext();
            context.setParameter(Constants.ACCESS_TOKEN, "token");
            assertTrue(authPluginService.validateIdentity(context, Resource.EMPTY_RESOURCE)
                .isSuccess());
        }
    }
    
    @Test
    void testLoginEnabledAndAdminRequest() {
        when(authenticationManager.hasGlobalAdminRole()).thenReturn(false, true);
        assertTrue(authPluginService.isLoginEnabled());
        assertTrue(authPluginService.isAdminRequest());
        assertFalse(authPluginService.isAdminRequest());
        setAuthEnabled(false, false);
        assertFalse(authPluginService.isLoginEnabled());
        assertFalse(authPluginService.isAdminRequest());
    }
    
    private void assertDefinition(ConfigItemDefinition definition, String key, String alias,
        ConfigItemEffectMode effectMode, boolean sensitive) {
        assertEquals(key, definition.getKey());
        assertEquals(alias, definition.getAliases().get(0));
        assertEquals(effectMode, definition.getEffectMode());
        assertEquals(sensitive, definition.isSensitive());
        assertTrue(definition.getDescription().length() > 0);
        assertTrue(definition.getName().length() > 0);
        assertTrue(definition.getDefaultValue() != null);
    }
    
    private Map<String, String> validConfig(boolean tokenCache, boolean anonymous) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY, encodedSecret(RAW_SECRET));
        result.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "18000");
        result.put(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE, Boolean.toString(tokenCache));
        result.put(NacosAuthPluginConfig.CACHING_ENABLED, "true");
        result.put(NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED, Boolean.toString(anonymous));
        return result;
    }
    
    private String encodedSecret(String secret) {
        return Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    private TokenManager activeTokenManager() {
        String fieldName = authPluginService.getConfig().isTokenCacheEnabled()
            ? "cachedTokenManager" : "tokenManager";
        return (TokenManager) ReflectionTestUtils.getField(
            authPluginService.getTokenManagerDelegate(), fieldName);
    }
    
    private void assertCacheEmpty(CachedJwtTokenManager tokenManager) {
        Map<?, ?> tokenMap = (Map<?, ?>) ReflectionTestUtils.getField(tokenManager, "tokenMap");
        Map<?, ?> userMap = (Map<?, ?>) ReflectionTestUtils.getField(tokenManager, "userMap");
        assertTrue(tokenMap.isEmpty());
        assertTrue(userMap.isEmpty());
    }
    
    private void assertCacheNotEmpty(CachedJwtTokenManager tokenManager) {
        Map<?, ?> tokenMap = (Map<?, ?>) ReflectionTestUtils.getField(tokenManager, "tokenMap");
        Map<?, ?> userMap = (Map<?, ?>) ReflectionTestUtils.getField(tokenManager, "userMap");
        assertFalse(tokenMap.isEmpty());
        assertFalse(userMap.isEmpty());
    }
    
    private void setAuthEnabled(boolean openApiEnabled, boolean consoleEnabled) {
        Map<String, NacosAuthConfig> configMap = new HashMap<>();
        configMap.put(ApiType.OPEN_API.name(), authConfig(openApiEnabled));
        configMap.put(ApiType.CONSOLE_API.name(), authConfig(consoleEnabled));
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            configMap);
    }
    
    private NacosAuthConfig authConfig(boolean enabled) {
        return new TestNacosAuthConfig(enabled);
    }
    
    private static final class TestNacosAuthConfig implements NacosAuthConfig {
        
        private final boolean enabled;
        
        private TestNacosAuthConfig(boolean enabled) {
            this.enabled = enabled;
        }
        
        @Override
        public String getAuthScope() {
            return "test";
        }
        
        @Override
        public boolean isAuthEnabled() {
            return enabled;
        }
        
        @Override
        public String getNacosAuthSystemType() {
            return "nacos";
        }
        
        @Override
        public boolean isSupportServerIdentity() {
            return false;
        }
        
        @Override
        public String getServerIdentityKey() {
            return "";
        }
        
        @Override
        public String getServerIdentityValue() {
            return "";
        }
    }
}
