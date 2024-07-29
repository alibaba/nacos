/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.manager;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Naming Server List Manager Test.
 *
 * @author misakacoder
 */
public class NamingServerListManagerTest {
    
    private NamingServerListManager namingServerListManager;
    
    @BeforeEach
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "nacos-dev");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        namingServerListManager = new NamingServerListManager(clientProperties, "public");
    }
    
    @Test
    public void testGetModuleName() {
        assertEquals(namingServerListManager.getModuleType(), ModuleType.NAMING);
    }
}
