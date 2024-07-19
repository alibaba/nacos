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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClientAuthPluginManager} unit test.
 *
 * @author wuyfee
 * @date 2021-08-12 12:56
 */

@ExtendWith(MockitoExtension.class)
class ClientAuthPluginManagerTest {
    
    private ClientAuthPluginManager clientAuthPluginManager;
    
    @Mock
    private List<String> serverlist;
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        clientAuthPluginManager = new ClientAuthPluginManager();
    }
    
    @AfterEach
    void tearDown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        getServiceLoaderMap().remove(AbstractClientAuthService.class);
        clientAuthPluginManager.shutdown();
    }
    
    private Map<Class<?>, Collection<Class<?>>> getServiceLoaderMap() throws NoSuchFieldException, IllegalAccessException {
        Field servicesField = NacosServiceLoader.class.getDeclaredField("SERVICES");
        servicesField.setAccessible(true);
        return (Map<Class<?>, Collection<Class<?>>>) servicesField.get(null);
    }
    
    @Test
    void testGetAuthServiceSpiImplSet() {
        clientAuthPluginManager.init(serverlist, nacosRestTemplate);
        Set<ClientAuthService> clientAuthServiceSet = clientAuthPluginManager.getAuthServiceSpiImplSet();
        assertFalse(clientAuthServiceSet.isEmpty());
    }
    
    @Test
    void testGetAuthServiceSpiImplSetForEmpty() throws NoSuchFieldException, IllegalAccessException {
        getServiceLoaderMap().put(AbstractClientAuthService.class, Collections.emptyList());
        clientAuthPluginManager.init(serverlist, nacosRestTemplate);
        Set<ClientAuthService> clientAuthServiceSet = clientAuthPluginManager.getAuthServiceSpiImplSet();
        assertTrue(clientAuthServiceSet.isEmpty());
    }
    
}
