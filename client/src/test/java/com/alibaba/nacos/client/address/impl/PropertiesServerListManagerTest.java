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

package com.alibaba.nacos.client.address.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.address.base.AbstractServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PropertiesServerListManagerTest.
 *
 * @author misakacoder
 */
public class PropertiesServerListManagerTest {

    @Test
    public void testName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "http://127.0.0.1,127.0.0.2:8888;127.0.0.3");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        AbstractServerListManager abstractServerListManager = create(properties);
        String name = abstractServerListManager.getName();
        assertEquals(name, "prop-127.0.0.1_8848-127.0.0.2_8888-127.0.0.3_8848-public");
        abstractServerListManager.shutdown();
    }

    @Test
    public void testServerList() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "http://127.0.0.1,127.0.0.2:8888;127.0.0.3");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        PropertiesServerListManager serverListManager = create(properties);
        List<String> serverList = serverListManager.getServerList();
        List<String> actual = Lists.newArrayList(
                "http://127.0.0.1:8848",
                "http://127.0.0.2:8888",
                "http://127.0.0.3:8848"
        );
        assertEquals(serverList, actual);
    }

    private PropertiesServerListManager create(Properties properties) throws Exception {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        return new PropertiesServerListManager(clientProperties);
    }
}
