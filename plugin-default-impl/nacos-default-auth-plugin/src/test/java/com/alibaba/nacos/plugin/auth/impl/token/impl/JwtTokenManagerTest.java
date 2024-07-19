/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtParser;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenManagerTest {
    
    private JwtTokenManager jwtTokenManager;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @BeforeEach
    void setUp() {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY, Base64.getEncoder()
                .encodeToString("SecretKey0123$567890$234567890123456789012345678901234567890123456789".getBytes(StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        jwtTokenManager = new JwtTokenManager(authConfigs);
    }
    
    @Test
    void testCreateTokenAndSecretKeyWithoutSpecialSymbol() throws AccessException {
        createToken("SecretKey0123567890234567890123456789012345678901234567890123456789");
    }
    
    @Test
    void testCreateTokenAndSecretKeyWithSpecialSymbol() throws AccessException {
        createToken("SecretKey01234@#!5678901234567890123456789012345678901234567890123456789");
    }
    
    private void createToken(String secretKey) throws AccessException {
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY,
                Base64.getEncoder().encodeToString(secretKey.getBytes(StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        
        JwtTokenManager jwtTokenManager = new JwtTokenManager(authConfigs);
        String nacosToken = jwtTokenManager.createToken("nacos");
        assertNotNull(nacosToken);
        jwtTokenManager.validateToken(nacosToken);
    }
    
    @Test
    void getAuthentication() throws AccessException {
        String nacosToken = jwtTokenManager.createToken("nacos");
        Authentication authentication = jwtTokenManager.getAuthentication(nacosToken);
        assertNotNull(authentication);
    }
    
    @Test
    void testInvalidSecretKey() {
        assertThrows(IllegalArgumentException.class, () -> createToken("0123456789ABCDEF0123456789ABCDE"));
    }
    
    @Test
    void testGetTokenTtlInSeconds() throws AccessException {
        assertTrue(jwtTokenManager.getTokenTtlInSeconds(jwtTokenManager.createToken("nacos")) > 0);
    }
    
    @Test
    void testGetExpiredTimeInSeconds() throws AccessException {
        assertTrue(jwtTokenManager.getExpiredTimeInSeconds(jwtTokenManager.createToken("nacos")) > 0);
    }
    
    @Test
    void testGetTokenTtlInSecondsWhenAuthDisabled() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(false);
        // valid secret key
        String ttl = EnvUtil.getProperty(AuthConstants.TOKEN_EXPIRE_SECONDS);
        assertEquals(Integer.parseInt(ttl), jwtTokenManager.getTokenTtlInSeconds(jwtTokenManager.createToken("nacos")));
        // invalid secret key
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY, "");
        EnvUtil.setEnvironment(mockEnvironment);
        jwtTokenManager = new JwtTokenManager(authConfigs);
        assertEquals(Integer.parseInt(ttl), jwtTokenManager.getTokenTtlInSeconds(jwtTokenManager.createToken("nacos")));
    }
    
    @Test
    void testCreateTokenWhenDisableAuthAndSecretKeyIsBlank() {
        when(authConfigs.isAuthEnabled()).thenReturn(false);
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY, "");
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        jwtTokenManager = new JwtTokenManager(authConfigs);
        assertEquals("AUTH_DISABLED", jwtTokenManager.createToken("nacos"));
    }
    
    @Test
    void testCreateTokenWhenDisableAuthAndSecretKeyIsNotBlank() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(false);
        MockEnvironment mockEnvironment = new MockEnvironment();
        String tmpKey = "SecretKey0123567890234567890123456789012345678901234567890123456789";
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY,
                Base64.getEncoder().encodeToString(tmpKey.getBytes(StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        EnvUtil.setEnvironment(mockEnvironment);
        jwtTokenManager = new JwtTokenManager(authConfigs);
        String token = jwtTokenManager.createToken("nacos");
        assertNotEquals("AUTH_DISABLED", token);
        jwtTokenManager.validateToken(token);
    }
    
    @Test
    void testNacosJwtParser() throws AccessException {
        String secretKey = "SecretKey0123$567890$234567890123456789012345678901234567890123456789";
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY,
                Base64.getEncoder().encodeToString(secretKey.getBytes(StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        
        JwtTokenManager jwtTokenManager = new JwtTokenManager(authConfigs);
        String nacosToken = jwtTokenManager.createToken("nacos");
        assertNotNull(nacosToken);
        System.out.println("oldToken: " + nacosToken);
        
        jwtTokenManager.validateToken(nacosToken);
        NacosJwtParser nacosJwtParser = new NacosJwtParser(Base64.getEncoder().encodeToString(secretKey.getBytes(StandardCharsets.UTF_8)));
        
        //check old token
        nacosJwtParser.parse(nacosToken);
        
        //create new token
        String newToken = nacosJwtParser.jwtBuilder().setUserName("nacos").setExpiredTime(TimeUnit.DAYS.toSeconds(10L)).compact();
        System.out.println("newToken: " + newToken);
        jwtTokenManager.validateToken(newToken);
    }
}
