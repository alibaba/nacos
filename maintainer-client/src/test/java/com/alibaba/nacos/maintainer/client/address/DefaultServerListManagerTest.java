/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.address;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultServerListManagerTest {
    
    DefaultServerListManager defaultServerListManager;
    
    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        defaultServerListManager = new DefaultServerListManager(NacosClientProperties.PROTOTYPE.derive(properties));
    }
    
    @Test
    void testStartWithoutAddress() {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty("MockTest", "");
        properties.setProperty("EmptyList", "");
        defaultServerListManager = new DefaultServerListManager(properties);
        assertThrows(NacosLoadException.class, () -> defaultServerListManager.start(),
                "serverList is empty,please check configuration");
    }
    
    @Test
    void genNextServer() throws NacosException {
        defaultServerListManager.start();
        assertEquals("127.0.0.1:8848", defaultServerListManager.genNextServer());
    }
    
    @Test
    void getCurrentServer() throws NacosException {
        defaultServerListManager.start();
        assertEquals("127.0.0.1:8848", defaultServerListManager.getCurrentServer());
    }
}