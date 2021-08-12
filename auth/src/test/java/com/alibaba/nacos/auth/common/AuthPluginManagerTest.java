/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.common;

import com.alibaba.nacos.auth.AuthPluginManager;
import com.alibaba.nacos.auth.AuthService;
import com.alibaba.nacos.auth.context.IdentityContext;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * {@link com.alibaba.nacos.auth.AuthPluginManager} unit test.
 *
 * @author wuyfee
 * @date 2021-08-12 12:56
 */

@RunWith(MockitoJUnitRunner.class)
public class AuthPluginManagerTest {
    
    private AuthPluginManager authPluginManager;
    
    @Mock
    private AuthService authService;
    
    private static final String TYPE = "DefaultAuthServiceName";
    
    @Mock
    private IdentityContext identityContext;
    
    @Mock
    private Permission permission;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        authPluginManager = AuthPluginManager.getInstance();
        Class<AuthPluginManager> authPluginManagerClass = AuthPluginManager.class;
        Field authPlugins = authPluginManagerClass.getDeclaredField("authServiceMap");
        authPlugins.setAccessible(true);
        Map<String, AuthService> authServiceMap = (Map<String, AuthService>) authPlugins.get(authPluginManager);
        authServiceMap.put(TYPE, authService);
    }
    
    @Test
    public void testGetInstance() {
        AuthPluginManager instance = AuthPluginManager.getInstance();

        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testFindAuthServiceSpiImpl() throws AccessException {
        Mockito.when(authService.login(identityContext)).thenReturn(true);
        Mockito.when(authService.authorityAccess(identityContext, permission)).thenReturn(true);
        Mockito.when(authService.getAuthServiceName()).thenReturn(TYPE);
        Optional<AuthService> authServiceImpl = authPluginManager.findAuthServiceSpiImpl(TYPE);
        Assert.assertTrue(authServiceImpl.isPresent());
    }

}
