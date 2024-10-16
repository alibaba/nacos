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

package com.alibaba.nacos.client.address;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesListProviderTest {
    
    private PropertiesListProvider propertiesListProvider;
    
    @BeforeEach
    void setUp() {
        propertiesListProvider = new PropertiesListProvider();
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        propertiesListProvider.shutdown();
    }
    
    @Test
    void testInitWithoutProperties() throws NacosException {
        assertThrows(NacosException.class, () -> propertiesListProvider.init(null, null));
    }
    
    @Test
    void testInit() throws NacosException {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        assertFalse(propertiesListProvider.match(properties));
        properties.setProperty(PropertyKeyConst.SERVER_ADDR,
                "localhost:1111,http://127.0.0.1:2222;https://1.1.1.1:3333,2.2.2.2;http://3.3.3.3,https://4.4.4.4");
        assertTrue(propertiesListProvider.match(properties));
        propertiesListProvider.init(properties, null);
        assertEquals(6, propertiesListProvider.getServerList().size());
        assertEquals("localhost:1111", propertiesListProvider.getServerList().get(0));
        assertEquals("http://127.0.0.1:2222", propertiesListProvider.getServerList().get(1));
        assertEquals("https://1.1.1.1:3333", propertiesListProvider.getServerList().get(2));
        assertEquals("2.2.2.2:8848", propertiesListProvider.getServerList().get(3));
        assertEquals("http://3.3.3.3", propertiesListProvider.getServerList().get(4));
        assertEquals("https://4.4.4.4", propertiesListProvider.getServerList().get(5));
        assertTrue(propertiesListProvider.isFixed());
        assertEquals(Constants.Address.ADDRESS_SERVER_LIST_PROVIDER_ORDER, propertiesListProvider.getOrder());
        assertEquals("fixed-localhost_1111-127.0.0.1_2222-1.1.1.1_3333-2.2.2.2_8848-3.3.3.3-4.4.4.4",
                propertiesListProvider.getServerName());
    }
    
    @Test
    void testGetServerNameWithNamespace() throws NacosException {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.NAMESPACE, "test_namespace");
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "localhost:1111");
        propertiesListProvider.init(properties, null);
        assertEquals("fixed-test_namespace-localhost_1111", propertiesListProvider.getServerName());
        assertEquals("test_namespace", propertiesListProvider.getNamespace());
    }
}