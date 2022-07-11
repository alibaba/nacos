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

package com.alibaba.nacos.plugin.auth.spi.client;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ClientAuthPluginManager} unit test.
 *
 * @author wuyfee
 * @date 2021-08-12 12:56
 */

@RunWith(MockitoJUnitRunner.class)
public class ClientAuthPluginManagerTest {
    
    private ClientAuthPluginManager clientAuthPluginManager;
    
    @Mock
    private List<String> serverlist;
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        clientAuthPluginManager = new ClientAuthPluginManager();
    }
    
    @After
    public void tearDown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        getServiceLoaderMap().remove(AbstractClientAuthService.class);
        clientAuthPluginManager.shutdown();
    }
    
    private Map<Class<?>, Collection<Class<?>>> getServiceLoaderMap()
            throws NoSuchFieldException, IllegalAccessException {
        Field servicesField = NacosServiceLoader.class.getDeclaredField("SERVICES");
        servicesField.setAccessible(true);
        return (Map<Class<?>, Collection<Class<?>>>) servicesField.get(null);
    }
    
    @Test
    public void testGetAuthServiceSpiImplSet() {
        clientAuthPluginManager.init(serverlist, nacosRestTemplate);
        Set<ClientAuthService> clientAuthServiceSet = clientAuthPluginManager.getAuthServiceSpiImplSet();
        Assert.assertFalse(clientAuthServiceSet.isEmpty());
    }
    
    @Test
    public void testGetAuthServiceSpiImplSetForEmpty() throws NoSuchFieldException, IllegalAccessException {
        getServiceLoaderMap().put(AbstractClientAuthService.class, Collections.emptyList());
        clientAuthPluginManager.init(serverlist, nacosRestTemplate);
        Set<ClientAuthService> clientAuthServiceSet = clientAuthPluginManager.getAuthServiceSpiImplSet();
        Assert.assertTrue(clientAuthServiceSet.isEmpty());
    }
    
}
