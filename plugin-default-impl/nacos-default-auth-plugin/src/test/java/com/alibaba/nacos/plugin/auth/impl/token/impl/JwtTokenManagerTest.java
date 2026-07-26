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

package com.alibaba.nacos.plugin.auth.impl.token.impl;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtParser;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenManagerTest {
    
    private static final long TOKEN_VALIDITY_SECONDS = 18000L;
    
    private static final String RAW_SECRET =
        "SecretKey0123$567890$234567890123456789012345678901234567890123456789";
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    private final AtomicReference<NacosAuthPluginConfig> pluginConfig = new AtomicReference<>();
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "false");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_CONSOLE_ENABLED, "false");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        EnvUtil.setEnvironment(environment);
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        setAuthEnabled(true);
        setPluginConfig(encodedSecret(RAW_SECRET), TOKEN_VALIDITY_SECONDS);
    }
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cachedConfigMap);
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testCreateParseAndValidateToken() throws AccessException {
        JwtTokenManager manager = newTokenManager();
        String token = manager.createToken("nacos");
        assertNotNull(token);
        manager.validateToken(token);
        assertEquals("nacos", manager.parseToken(token).getUserName());
        assertTrue(manager.getTokenTtlInSeconds(token) > 0);
        assertTrue(manager.getExpiredTimeInSeconds(token) > 0);
        assertEquals(TOKEN_VALIDITY_SECONDS, manager.getTokenValidityInSeconds());
    }
    
    @Test
    void testDeprecatedAuthenticationMethods() throws AccessException {
        JwtTokenManager manager = newTokenManager();
        Authentication input = mock(Authentication.class);
        when(input.getName()).thenReturn("nacos");
        String token = manager.createToken(input);
        Authentication output = manager.getAuthentication(token);
        assertEquals("nacos", output.getName());
    }
    
    @Test
    void testRuntimeTokenValidityUpdate() throws AccessException {
        setPluginConfig(encodedSecret(RAW_SECRET), 1L);
        JwtTokenManager manager = newTokenManager();
        setPluginConfig(encodedSecret(RAW_SECRET), 123L);
        assertEquals(123L, manager.getTokenValidityInSeconds());
        assertTrue(manager.getTokenTtlInSeconds(manager.createToken("nacos")) <= 123L);
    }
    
    @Test
    void testAuthDisabledWithBlankSecret() throws AccessException {
        setAuthEnabled(false);
        setPluginConfig("", TOKEN_VALIDITY_SECONDS);
        JwtTokenManager manager = newTokenManager();
        assertEquals("AUTH_DISABLED", manager.createToken("nacos"));
        assertEquals(TOKEN_VALIDITY_SECONDS, manager.getTokenTtlInSeconds("ignored"));
        assertEquals(TOKEN_VALIDITY_SECONDS, manager.getExpiredTimeInSeconds("ignored"));
        assertThrows(NacosRuntimeException.class, () -> manager.parseToken("ignored"));
    }
    
    @Test
    void testAuthDisabledWithConfiguredSecret() throws AccessException {
        setAuthEnabled(false);
        JwtTokenManager manager = newTokenManager();
        String token = manager.createToken("nacos");
        assertNotEquals("AUTH_DISABLED", token);
        manager.validateToken(token);
    }
    
    @Test
    void testAuthEnabledRequiresSecret() {
        setPluginConfig("", TOKEN_VALIDITY_SECONDS);
        JwtTokenManager manager = newTokenManager();
        assertThrows(NacosRuntimeException.class, () -> manager.createToken("nacos"));
        assertThrows(NacosRuntimeException.class,
            () -> manager.getTokenTtlInSeconds("ignored"));
        assertThrows(NacosRuntimeException.class,
            () -> manager.getExpiredTimeInSeconds("ignored"));
    }
    
    @Test
    void testCompatibleWithNacosJwtParser() throws AccessException {
        String encodedSecret = encodedSecret(RAW_SECRET);
        JwtTokenManager manager = newTokenManager();
        NacosJwtParser parser = new NacosJwtParser(encodedSecret);
        parser.parse(manager.createToken("nacos"));
        String token = parser.jwtBuilder().setUserName("nacos")
            .setExpiredTime(TimeUnit.DAYS.toSeconds(10L)).compact();
        manager.validateToken(token);
    }
    
    private void setAuthEnabled(boolean enabled) {
        NacosAuthConfig config = mock(NacosAuthConfig.class);
        when(config.isAuthEnabled()).thenReturn(enabled);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            Collections.singletonMap("test", config));
    }
    
    private JwtTokenManager newTokenManager() {
        return new JwtTokenManager(pluginConfig::get);
    }
    
    private void setPluginConfig(String secret, long tokenValiditySeconds) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY, secret);
        values.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS,
            Long.toString(tokenValiditySeconds));
        pluginConfig.set(NacosAuthPluginConfig.from(values, false));
    }
    
    private String encodedSecret(String secret) {
        return Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }
}
