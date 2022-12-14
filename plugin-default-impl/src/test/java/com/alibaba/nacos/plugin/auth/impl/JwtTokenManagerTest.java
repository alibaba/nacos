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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.jsonwebtoken.io.Encoders;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;

@RunWith(MockitoJUnitRunner.class)
public class JwtTokenManagerTest {
    
    private JwtTokenManager jwtTokenManager;
    
    @Before
    public void setUp() {
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY, Encoders.BASE64.encode(
                "SecretKey0123$567890$234567890123456789012345678901234567890123456789".getBytes(
                        StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS,
                AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        
        jwtTokenManager = new JwtTokenManager();
    }
    
    @Test
    public void testCreateTokenAndSecretKeyWithoutSpecialSymbol() {
        createToken("SecretKey0123567890234567890123456789012345678901234567890123456789");
    }
    
    @Test
    public void testCreateTokenAndSecretKeyWithSpecialSymbol() {
        createToken("SecretKey01234@#!5678901234567890123456789012345678901234567890123456789");
    }
    
    private void createToken(String secretKey) {
        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(AuthConstants.TOKEN_SECRET_KEY,
                Encoders.BASE64.encode(secretKey.getBytes(StandardCharsets.UTF_8)));
        mockEnvironment.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS,
                AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString());
        
        EnvUtil.setEnvironment(mockEnvironment);
        
        JwtTokenManager jwtTokenManager = new JwtTokenManager();
        String nacosToken = jwtTokenManager.createToken("nacos");
        Assert.assertNotNull(nacosToken);
        jwtTokenManager.validateToken(nacosToken);
    }
    
    @Test
    public void getAuthentication() {
        String nacosToken = jwtTokenManager.createToken("nacos");
        Authentication authentication = jwtTokenManager.getAuthentication(nacosToken);
        Assert.assertNotNull(authentication);
    }
    
    @Test
    public void testInvalidSecretKey() {
        Assert.assertThrows(IllegalArgumentException.class, () -> createToken("0123456789ABCDEF0123456789ABCDE"));
    }
}
