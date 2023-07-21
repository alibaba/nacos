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
import com.alibaba.nacos.plugin.auth.impl.token.impl.CachedJwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.token.impl.JwtTokenManager;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TokenManagerDelegateTest.
 *
 * @author majorhe
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenManagerDelegateTest {
    
    private TokenManagerDelegate tokenManagerDelegate;
    
    @Mock
    private CachedJwtTokenManager cachedJwtTokenManager;
    
    @Mock
    private JwtTokenManager jwtTokenManager;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private NacosUser user;
    
    @Before
    public void setUp() throws Exception {
        tokenManagerDelegate = new TokenManagerDelegate();
        injectObject("jwtTokenManager", jwtTokenManager);
        injectObject("cachedJwtTokenManager", cachedJwtTokenManager);
        injectObject("tokenCacheEnabled", Boolean.TRUE);
        when(cachedJwtTokenManager.getTokenValidityInSeconds()).thenReturn(100L);
        when(cachedJwtTokenManager.getTokenTtlInSeconds(anyString())).thenReturn(100L);
        when(cachedJwtTokenManager.getAuthentication(anyString())).thenReturn(authentication);
        when(cachedJwtTokenManager.parseToken(anyString())).thenReturn(user);
        when(cachedJwtTokenManager.createToken(anyString())).thenReturn("token");
        when(cachedJwtTokenManager.createToken(authentication)).thenReturn("token");
    }
    
    @Test
    public void testCreateToken1() throws AccessException {
        Assert.assertEquals("token", tokenManagerDelegate.createToken(authentication));
    }
    
    @Test
    public void testCreateToken2() throws AccessException {
        Assert.assertEquals("token", tokenManagerDelegate.createToken("nacos"));
    }
    
    @Test
    public void testGetAuthentication() throws AccessException {
        Assert.assertNotNull(tokenManagerDelegate.getAuthentication("token"));
    }
    
    @Test
    public void testValidateToken() throws AccessException {
        tokenManagerDelegate.validateToken("token");
    }
    
    @Test
    public void testParseToken() throws AccessException {
        Assert.assertNotNull(tokenManagerDelegate.parseToken("token"));
    }
    
    @Test
    public void testGetTokenTtlInSeconds() throws AccessException {
        Assert.assertTrue(tokenManagerDelegate.getTokenTtlInSeconds("token") > 0);
    }
    
    @Test
    public void testGetTokenValidityInSeconds() throws AccessException {
        Assert.assertTrue(tokenManagerDelegate.getTokenValidityInSeconds() > 0);
    }
    
    private void injectObject(String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = TokenManagerDelegate.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(tokenManagerDelegate, value);
    }
}
