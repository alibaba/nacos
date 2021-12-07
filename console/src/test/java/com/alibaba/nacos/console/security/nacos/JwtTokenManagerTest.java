/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.security.nacos;

import com.alibaba.nacos.auth.common.AuthConfigs;
import io.jsonwebtoken.lang.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class JwtTokenManagerTest {
    
    @Test
    public void testCreateTokenAndSecretKeyWithoutSpecialSymbol() throws NoSuchFieldException, IllegalAccessException {
        createToken("SecretKey0123$567890$234567890123456789012345678901234567890123456789");
        
    }
    
    @Test
    public void testCreateTokenAndSecretKeyWithSpecialSymbol() throws NoSuchFieldException, IllegalAccessException {
        createToken("SecretKey012345678901234567890123456789012345678901234567890123456789");
    }
    
    private void createToken(String secretKey) throws NoSuchFieldException, IllegalAccessException {
        AuthConfigs authConfigs = new AuthConfigs();
        injectProperty(authConfigs, "secretKey", secretKey);
        injectProperty(authConfigs, "tokenValidityInSeconds", 300);
        JwtTokenManager jwtTokenManager = new JwtTokenManager();
        injectProperty(jwtTokenManager, "authConfigs", authConfigs);
        String nacosToken = jwtTokenManager.createToken("nacos");
        Assert.notNull(nacosToken);
        jwtTokenManager.validateToken(nacosToken);
    }
    
    private void injectProperty(Object o, String propertyName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> aClass = o.getClass();
        Field declaredField = aClass.getDeclaredField(propertyName);
        declaredField.setAccessible(true);
        declaredField.set(o, value);
    }
    
}
