/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.address.impl;

import com.alibaba.nacos.plugin.address.common.AddressProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Date 2022/8/16.
 *
 * @author GuoJiangFu
 */
public class PropertyAddressPluginTest {

    private PropertyAddressPlugin propertyAddressPlugin;
    
    @Before
    public void setUp() {
        propertyAddressPlugin = new PropertyAddressPlugin();
        AddressProperties.setProperties("serverAddressStr", "localhost:8080, localhost:8081");
    }
    
    @Test
    public void testGetServerList() {
        List<String> serverList = propertyAddressPlugin.getServerList();
        assertEquals(2, serverList.size());
        assertEquals("localhost:8080", serverList.get(0));
        assertEquals("localhost:8081", serverList.get(1));
    }
    
    @Test
    public void testGetPluginName() {
        assertEquals("property-address-plugin", propertyAddressPlugin.getPluginName());
    }
}
