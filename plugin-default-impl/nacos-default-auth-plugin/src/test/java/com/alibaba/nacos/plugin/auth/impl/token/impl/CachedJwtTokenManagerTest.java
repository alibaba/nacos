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
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
        cachedJwtTokenManager = new CachedJwtTokenManager(jwtTokenManager);
        when(jwtTokenManager.getTokenValidityInSeconds()).thenReturn(100L);
        when(jwtTokenManager.getTokenTtlInSeconds(anyString())).thenReturn(100L);
        when(jwtTokenManager.getExpiredTimeInSeconds(anyString()))
            .thenReturn(System.currentTimeMillis());
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
    
    @Test
    void testCreateTokenReturnsCachedWhenNotExpired() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        when(jwtTokenManager.getTokenValidityInSeconds()).thenReturn(10000L);
        String secondToken = cachedJwtTokenManager.createToken("nacos");
        assertEquals("token", secondToken);
    }
    
    @Test
    void testGetAuthenticationFallsBackWhenNotCached() throws AccessException {
        Authentication result = cachedJwtTokenManager.getAuthentication("uncached-token");
        assertNotNull(result);
    }
    
    @Test
    void testGetAuthenticationReturnsCachedEntry() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        Authentication result = cachedJwtTokenManager.getAuthentication("token");
        assertNotNull(result);
    }
    
    @Test
    void testParseTokenFallsBackWhenNotCached() throws AccessException {
        NacosUser result = cachedJwtTokenManager.parseToken("uncached-token");
        assertNotNull(result);
    }
    
    @Test
    void testGetTokenTtlFallsBackWhenNotCached() throws AccessException {
        long ttl = cachedJwtTokenManager.getTokenTtlInSeconds("uncached-token");
        assertTrue(ttl > 0);
    }
    
    @Test
    void testGetTokenTtlReturnsCachedValue() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        long ttl = cachedJwtTokenManager.getTokenTtlInSeconds("token");
        assertTrue(ttl > 0);
    }
    
    @Test
    void testValidateTokenSkipsWhenCached() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        assertDoesNotThrow(() -> cachedJwtTokenManager.validateToken("token"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testValidateTokenSafeWhenCacheEvictedConcurrently() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        Field tokenMapField = CachedJwtTokenManager.class.getDeclaredField("tokenMap");
        tokenMapField.setAccessible(true);
        Map<String, ?> tokenMap = (Map<String, ?>) tokenMapField.get(cachedJwtTokenManager);
        tokenMap.clear();
        assertDoesNotThrow(() -> cachedJwtTokenManager.validateToken("token"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testCreateTokenSafeWhenCacheEvictedConcurrently() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        Field userMapField = CachedJwtTokenManager.class.getDeclaredField("userMap");
        userMapField.setAccessible(true);
        Map<String, ?> userMap = (Map<String, ?>) userMapField.get(cachedJwtTokenManager);
        userMap.clear();
        assertDoesNotThrow(() -> cachedJwtTokenManager.createToken("nacos"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testGetAuthenticationSafeWhenCacheEvictedConcurrently() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        Field tokenMapField = CachedJwtTokenManager.class.getDeclaredField("tokenMap");
        tokenMapField.setAccessible(true);
        Map<String, ?> tokenMap = (Map<String, ?>) tokenMapField.get(cachedJwtTokenManager);
        tokenMap.clear();
        assertDoesNotThrow(() -> cachedJwtTokenManager.getAuthentication("token"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testParseTokenSafeWhenCacheEvictedConcurrently() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        Field tokenMapField = CachedJwtTokenManager.class.getDeclaredField("tokenMap");
        tokenMapField.setAccessible(true);
        Map<String, ?> tokenMap = (Map<String, ?>) tokenMapField.get(cachedJwtTokenManager);
        tokenMap.clear();
        assertDoesNotThrow(() -> cachedJwtTokenManager.parseToken("token"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testGetTokenTtlSafeWhenCacheEvictedConcurrently() throws Exception {
        cachedJwtTokenManager.createToken("nacos");
        Field tokenMapField = CachedJwtTokenManager.class.getDeclaredField("tokenMap");
        tokenMapField.setAccessible(true);
        Map<String, ?> tokenMap = (Map<String, ?>) tokenMapField.get(cachedJwtTokenManager);
        tokenMap.clear();
        assertDoesNotThrow(() -> cachedJwtTokenManager.getTokenTtlInSeconds("token"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testCleanExpiredTokenRemovesExpiredEntriesOnly() throws Exception {
        Field tokenMapField = CachedJwtTokenManager.class.getDeclaredField("tokenMap");
        tokenMapField.setAccessible(true);
        Field userMapField = CachedJwtTokenManager.class.getDeclaredField("userMap");
        userMapField.setAccessible(true);
        Map<String, CachedJwtTokenManager.TokenEntity> tokenMap =
            (Map<String, CachedJwtTokenManager.TokenEntity>) tokenMapField.get(
                cachedJwtTokenManager);
        Map<String, CachedJwtTokenManager.TokenEntity> userMap =
            (Map<String, CachedJwtTokenManager.TokenEntity>) userMapField.get(
                cachedJwtTokenManager);
        CachedJwtTokenManager.TokenEntity expired =
            new CachedJwtTokenManager.TokenEntity("expired", "expiredUser",
                System.currentTimeMillis() - 1_000L, authentication, user);
        CachedJwtTokenManager.TokenEntity active =
            new CachedJwtTokenManager.TokenEntity("active", "activeUser",
                System.currentTimeMillis() + 60_000L, authentication, user);
        tokenMap.put("expired", expired);
        tokenMap.put("active", active);
        userMap.put("expiredUser", expired);
        userMap.put("activeUser", active);
        Method cleanExpiredToken =
            CachedJwtTokenManager.class.getDeclaredMethod("cleanExpiredToken");
        cleanExpiredToken.setAccessible(true);
        
        cleanExpiredToken.invoke(cachedJwtTokenManager);
        
        assertFalse(tokenMap.containsKey("expired"));
        assertTrue(tokenMap.containsKey("active"));
        assertFalse(userMap.containsKey("expiredUser"));
        assertTrue(userMap.containsKey("activeUser"));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testValidateTokenDoesNotCacheEmptyOrExpiredToken() throws Exception {
        Authentication emptyAuthentication = mock(Authentication.class);
        when(emptyAuthentication.getName()).thenReturn("");
        when(jwtTokenManager.getAuthentication("empty")).thenReturn(emptyAuthentication);
        cachedJwtTokenManager.validateToken("empty");
        
        Authentication expiredAuthentication = mock(Authentication.class);
        when(expiredAuthentication.getName()).thenReturn("expiredUser");
        when(jwtTokenManager.getAuthentication("expired")).thenReturn(expiredAuthentication);
        when(jwtTokenManager.getExpiredTimeInSeconds("expired")).thenReturn(1L);
        cachedJwtTokenManager.validateToken("expired");
        
        Field tokenMapField = CachedJwtTokenManager.class.getDeclaredField("tokenMap");
        tokenMapField.setAccessible(true);
        Map<String, ?> tokenMap = (Map<String, ?>) tokenMapField.get(cachedJwtTokenManager);
        assertFalse(tokenMap.containsKey("empty"));
        assertFalse(tokenMap.containsKey("expired"));
    }
    
    @Test
    void testParseTokenRejectsEmptyOrExpiredToken() throws Exception {
        Authentication emptyAuthentication = mock(Authentication.class);
        when(emptyAuthentication.getName()).thenReturn("");
        when(jwtTokenManager.getAuthentication("empty")).thenReturn(emptyAuthentication);
        
        assertThrows(AccessException.class, () -> cachedJwtTokenManager.parseToken("empty"));
        
        Authentication expiredAuthentication = mock(Authentication.class);
        when(expiredAuthentication.getName()).thenReturn("expiredUser");
        when(jwtTokenManager.getAuthentication("expired")).thenReturn(expiredAuthentication);
        when(jwtTokenManager.getExpiredTimeInSeconds("expired")).thenReturn(1L);
        
        assertThrows(AccessException.class, () -> cachedJwtTokenManager.parseToken("expired"));
    }
    
    @Test
    void testTokenEntityAccessorsAndToString() {
        CachedJwtTokenManager.TokenEntity entity =
            new CachedJwtTokenManager.TokenEntity("token", "user",
                System.currentTimeMillis() + 60_000L, authentication, user);
        entity.setToken("token2");
        entity.setUserName("user2");
        entity.setExpiredTimeMills(123L);
        entity.setAuthentication(authentication);
        entity.setNacosUser(user);
        
        assertEquals("token2", entity.getToken());
        assertEquals("user2", entity.getUserName());
        assertEquals(123L, entity.getExpiredTimeMills());
        assertEquals(authentication, entity.getAuthentication());
        assertEquals(user, entity.getNacosUser());
        assertTrue(entity.toString().contains("TokenEntity"));
    }
    
}
