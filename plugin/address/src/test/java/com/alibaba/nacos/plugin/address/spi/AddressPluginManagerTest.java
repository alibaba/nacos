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

package com.alibaba.nacos.plugin.address.spi;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * Date 2022/7/31.
 *
 * @author GuoJiangFu
 */
@RunWith(MockitoJUnitRunner.class)
public class AddressPluginManagerTest {
    
    private AddressPluginManager addressPluginManager;
    
    @Mock
    private AddressPlugin addressPlugin;
    
    private static final String TYPE = "test";
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        addressPluginManager = AddressPluginManager.getInstance();
        Class<AddressPluginManager> addressPluginManagerClass = AddressPluginManager.class;
        Field addressPlugins = addressPluginManagerClass.getDeclaredField("addressPluginMap");
        addressPlugins.setAccessible(true);
        Map<String, AddressPlugin> authServiceMap = (Map<String, AddressPlugin>) addressPlugins
                .get(addressPluginManager);
        authServiceMap.put(TYPE, addressPlugin);
    }
    
    @Test
    public void testGetInstance() {
        AddressPluginManager instance = AddressPluginManager.getInstance();
        
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testFindAuthServiceSpiImpl() {
        Optional<AddressPlugin> authServiceImpl = addressPluginManager.findAuthServiceSpiImpl(TYPE);
        Assert.assertTrue(authServiceImpl.isPresent());
    }
}
