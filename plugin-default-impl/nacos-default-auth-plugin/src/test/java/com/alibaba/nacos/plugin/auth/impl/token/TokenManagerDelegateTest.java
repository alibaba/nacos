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

package com.alibaba.nacos.plugin.auth.impl.token;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenManagerDelegateTest.
 *
 * @author majorhe
 */
@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenManagerDelegateTest {
    
    private static final String RAW_SECRET =
        "SecretKey0123$567890$234567890123456789012345678901234567890123456789";
    
    private TokenManagerDelegate tokenManagerDelegate;
    
    @Mock
    private CachedJwtTokenManager cachedJwtTokenManager;
    
    @Mock
    private JwtTokenManager jwtTokenManager;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private NacosUser user;
    
    private final AtomicReference<NacosAuthPluginConfig> config = new AtomicReference<>();
    
    @BeforeEach
    void setUp() throws Exception {
        setConfig(true, 100L, RAW_SECRET);
        tokenManagerDelegate = new TokenManagerDelegate(config::get);
        tokenManagerDelegate.applyTokenConfig();
        ReflectionTestUtils.setField(tokenManagerDelegate, "tokenManager", jwtTokenManager);
        ReflectionTestUtils.setField(tokenManagerDelegate, "cachedTokenManager",
            cachedJwtTokenManager);
        when(cachedJwtTokenManager.getTokenValidityInSeconds()).thenReturn(100L);
        when(cachedJwtTokenManager.getTokenTtlInSeconds(anyString())).thenReturn(100L);
        when(cachedJwtTokenManager.getAuthentication(anyString())).thenReturn(authentication);
        when(cachedJwtTokenManager.parseToken(anyString())).thenReturn(user);
        when(cachedJwtTokenManager.createToken(anyString())).thenReturn("token");
        when(cachedJwtTokenManager.createToken(authentication)).thenReturn("token");
        when(jwtTokenManager.createToken("nacos")).thenReturn("direct-token");
    }
    
    @Test
    void testCreateToken1() throws AccessException {
        assertEquals("token", tokenManagerDelegate.createToken(authentication));
    }
    
    @Test
    void testCreateToken2() throws AccessException {
        assertEquals("token", tokenManagerDelegate.createToken("nacos"));
    }
    
    @Test
    void testGetAuthentication() throws AccessException {
        assertNotNull(tokenManagerDelegate.getAuthentication("token"));
    }
    
    @Test
    void testValidateToken() throws AccessException {
        tokenManagerDelegate.validateToken("token");
    }
    
    @Test
    void testParseToken() throws AccessException {
        assertNotNull(tokenManagerDelegate.parseToken("token"));
    }
    
    @Test
    void testGetTokenTtlInSeconds() throws AccessException {
        assertTrue(tokenManagerDelegate.getTokenTtlInSeconds("token") > 0);
    }
    
    @Test
    void testGetTokenValidityInSeconds() throws AccessException {
        assertTrue(tokenManagerDelegate.getTokenValidityInSeconds() > 0);
    }
    
    @Test
    void testDelegateRequiresInitialization() {
        TokenManagerDelegate delegate = new TokenManagerDelegate(config::get);
        assertThrows(IllegalStateException.class, () -> delegate.createToken("nacos"));
        delegate.cleanExpiredToken();
    }
    
    @Test
    void testSelectDirectTokenManager() throws AccessException {
        setConfig(false, 100L, RAW_SECRET);
        assertEquals("direct-token", tokenManagerDelegate.createToken("nacos"));
    }
    
    @Test
    void testCleanExpiredTokenWhenCacheEnabled() {
        tokenManagerDelegate.cleanExpiredToken();
        setConfig(false, 100L, RAW_SECRET);
        tokenManagerDelegate.cleanExpiredToken();
        verify(cachedJwtTokenManager, times(1)).cleanExpiredToken();
    }
    
    @Test
    void testApplyTokenConfigClearsCacheWithoutRecreatingManagers() {
        tokenManagerDelegate.applyTokenConfig();
        setConfig(true, 200L, RAW_SECRET);
        tokenManagerDelegate.applyTokenConfig();
        verify(cachedJwtTokenManager).clear();
        setConfig(false, 200L, RAW_SECRET);
        tokenManagerDelegate.applyTokenConfig();
        verify(cachedJwtTokenManager, times(2)).clear();
        setConfig(false, 200L, RAW_SECRET + "changed");
        assertThrows(IllegalArgumentException.class,
            tokenManagerDelegate::applyTokenConfig);
        assertSame(jwtTokenManager,
            ReflectionTestUtils.getField(tokenManagerDelegate, "tokenManager"));
        assertSame(cachedJwtTokenManager,
            ReflectionTestUtils.getField(tokenManagerDelegate, "cachedTokenManager"));
    }
    
    private void setConfig(boolean tokenCacheEnabled, long tokenValiditySeconds,
        String rawSecret) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY,
            Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8)));
        values.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS,
            Long.toString(tokenValiditySeconds));
        values.put(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE,
            Boolean.toString(tokenCacheEnabled));
        config.set(NacosAuthPluginConfig.from(values, true));
    }
}
