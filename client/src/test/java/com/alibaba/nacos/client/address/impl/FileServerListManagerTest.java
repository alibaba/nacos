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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FileServerListManagerTest.
 *
 * @author misakacoder
 */
public class FileServerListManagerTest {

    @Test
    public void testWindowsName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_FILE, "D:\\nacos\\server.conf");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        AbstractServerListManager abstractServerListManager = create(properties);
        String name = abstractServerListManager.getName();
        assertEquals(name, "file-D_nacos_server.conf-public");
        abstractServerListManager.shutdown();
    }

    @Test
    public void testLinuxName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_FILE, "/nacos/server.conf");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        AbstractServerListManager abstractServerListManager = create(properties);
        String name = abstractServerListManager.getName();
        assertEquals(name, "file-nacos_server.conf-public");
        abstractServerListManager.shutdown();
    }

    @Test
    public void testServerFile() throws Exception {
        Properties properties = new Properties();
        assertEquals(getFieldValue(properties, "serverFile"), "");

        properties.setProperty(PropertyKeyConst.SERVER_FILE, "/home/server.conf");
        assertEquals(getFieldValue(properties, "serverFile"), "/home/server.conf");
    }

    private FileServerListManager create(Properties properties) throws Exception {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        return new FileServerListManager(clientProperties);
    }

    private Object getFieldValue(Properties properties, String name) throws Exception {
        FileServerListManager fileServerListManager = create(properties);
        Field field = FileServerListManager.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(fileServerListManager);
        fileServerListManager.shutdown();
        return value;
    }
}
