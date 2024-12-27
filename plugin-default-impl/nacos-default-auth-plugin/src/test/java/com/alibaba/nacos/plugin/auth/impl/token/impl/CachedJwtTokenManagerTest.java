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

package com.alibaba.nacos.plugin.auth.impl.token.impl;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CachedJwtTokenManagerTest.
 *
 * @author Majorhe
 */
@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class CachedJwtTokenManagerTest {
    
    private CachedJwtTokenManager cachedJwtTokenManager;
    
    @Mock
    private JwtTokenManager jwtTokenManager;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private NacosUser user;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedJwtTokenManager = new CachedJwtTokenManager();
        injectObject("jwtTokenManager", jwtTokenManager);
        when(jwtTokenManager.getTokenValidityInSeconds()).thenReturn(100L);
        when(jwtTokenManager.getTokenTtlInSeconds(anyString())).thenReturn(100L);
        when(jwtTokenManager.getExpiredTimeInSeconds(anyString())).thenReturn(System.currentTimeMillis());
        when(jwtTokenManager.getAuthentication(anyString())).thenReturn(authentication);
        when(jwtTokenManager.parseToken(anyString())).thenReturn(user);
        when(jwtTokenManager.createToken(anyString())).thenReturn("token");
        when(authentication.getName()).thenReturn("nacos");
    }
    
    @Test
    void testCreateToken1() throws AccessException {
        assertEquals("token", cachedJwtTokenManager.createToken(authentication));
    }
    
    @Test
    void testCreateToken2() throws AccessException {
        assertEquals("token", cachedJwtTokenManager.createToken("nacos"));
    }
    
    @Test
    void testGetAuthentication() throws AccessException {
        assertNotNull(cachedJwtTokenManager.getAuthentication("token"));
    }
    
    @Test
    void testValidateToken() throws AccessException {
        cachedJwtTokenManager.validateToken("token");
    }
    
    @Test
    void testParseToken() throws AccessException {
        assertNotNull(cachedJwtTokenManager.parseToken("token"));
    }
    
    @Test
    void testGetTokenTtlInSeconds() throws AccessException {
        assertTrue(cachedJwtTokenManager.getTokenTtlInSeconds("token") > 0);
    }
    
    @Test
    void testGetTokenValidityInSeconds() {
        assertTrue(cachedJwtTokenManager.getTokenValidityInSeconds() > 0);
    }
    
    private void injectObject(String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = CachedJwtTokenManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(cachedJwtTokenManager, value);
    }
}
