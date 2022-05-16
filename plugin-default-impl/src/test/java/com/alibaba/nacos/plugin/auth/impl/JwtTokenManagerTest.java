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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import io.jsonwebtoken.lang.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JwtTokenManagerTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    @Mock
    private ObjectProvider<LdapAuthenticationProvider> ldapAuthenticationProvider;
    
    private NacosAuthConfig nacosAuthConfig;
    
    @Test
    public void testCreateTokenAndSecretKeyWithoutSpecialSymbol() throws NoSuchFieldException, IllegalAccessException {
        createToken("SecretKey0123$567890$234567890123456789012345678901234567890123456789");
        
    }
    
    @Test
    public void testCreateTokenAndSecretKeyWithSpecialSymbol() throws NoSuchFieldException, IllegalAccessException {
        createToken("SecretKey012345678901234567890123456789012345678901234567890123456789");
    }
    
    private void createToken(String secretKey) throws NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty(AuthConstants.TOKEN_SECRET_KEY, secretKey);
        properties.setProperty(AuthConstants.TOKEN_EXPIRE_SECONDS, "300");
        when(authConfigs.getAuthPluginProperties(AuthConstants.AUTH_PLUGIN_TYPE)).thenReturn(properties);
    
        nacosAuthConfig = new NacosAuthConfig(null, null, authConfigs, null,
                ldapAuthenticationProvider, methodsCache);
        nacosAuthConfig.init();
        JwtTokenManager jwtTokenManager = new JwtTokenManager();
        injectProperty(jwtTokenManager, "nacosAuthConfig", nacosAuthConfig);
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
