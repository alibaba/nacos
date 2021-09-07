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

package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.exception.NacosException;
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
import java.util.Properties;

/**
 * {@link com.alibaba.nacos.client.auth.ClientAuthPluginManager} unit test.
 *
 * @author wuyfee
 * @date 2021-08-12 12:56
 */

@RunWith(MockitoJUnitRunner.class)
public class ClientAuthPluginManagerTest {
    
    private ClientAuthPluginManager clientAuthPluginManager;
    
    @Mock
    private ClientAuthService clientAuthService;
    
    @Mock
    private Properties properties;
    
    private static final String TYPE = "NacosClientAuthServiceImpl";
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        clientAuthPluginManager = ClientAuthPluginManager.getInstance();
        Class<ClientAuthPluginManager> clientAuthPluginManagerClass = ClientAuthPluginManager.class;
        Field authPlugins = clientAuthPluginManagerClass.getDeclaredField("clientAuthServiceHashMap");
        authPlugins.setAccessible(true);
        Map<String, ClientAuthService> clientAuthServiceMap = (Map<String, ClientAuthService>) authPlugins
                .get(clientAuthPluginManager);
        clientAuthServiceMap.put(TYPE, clientAuthService);
    }
    
    @Test
    public void testGetInstance() {
        ClientAuthPluginManager instance = ClientAuthPluginManager.getInstance();
        
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testFindAuthServiceSpiImpl() throws NacosException {
        Mockito.when(clientAuthService.login(properties)).thenReturn(true);
        Mockito.when(clientAuthService.getClientAuthServiceName()).thenReturn(TYPE);
        Optional<ClientAuthService> authServiceImpl = clientAuthPluginManager.findAuthServiceSpiImpl(TYPE);
        Assert.assertTrue(authServiceImpl.isPresent());
    }
    
}
