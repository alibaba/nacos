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

package com.alibaba.nacos.client.address.base;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractServerListManagerTest {

    private AbstractServerListManager abstractServerListManager;

    @BeforeEach
    public void setUp() throws Exception {
        abstractServerListManager = Mockito.mock(AbstractServerListManager.class, Answers.CALLS_REAL_METHODS);
    }

    @Test
    public void testNamespace() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        Method initNamespace = AbstractServerListManager.class.getDeclaredMethod("initNamespace", NacosClientProperties.class);
        initNamespace.setAccessible(true);
        Object namespace = initNamespace.invoke(abstractServerListManager, clientProperties);
        assertEquals(namespace, "public");
    }

    @Test
    public void testReadServerList() throws Exception {
        String data = "127.0.0.1:8848\n"
                + "\n\r"
                + "127.0.0.2:8848\n"
                + "\n\r"
                + "127.0.0.3:8848";
        List<String> actual = Arrays.asList("127.0.0.1:8848", "127.0.0.2:8848", "127.0.0.3:8848");
        List<String> serverList = abstractServerListManager.readServerList(new StringReader(data));
        assertEquals(serverList, actual);
    }

    @Test
    public void testRepairServerAddr() throws Exception {
        assertEquals(abstractServerListManager.repairServerAddr("127.0.0.1"), "http://127.0.0.1:8848");
        assertEquals(abstractServerListManager.repairServerAddr("127.0.0.1:8888"), "http://127.0.0.1:8888");
        assertEquals(abstractServerListManager.repairServerAddr("http://127.0.0.1"), "http://127.0.0.1:8848");
        assertEquals(abstractServerListManager.repairServerAddr("http://127.0.0.1:8888"), "http://127.0.0.1:8888");
        assertEquals(abstractServerListManager.repairServerAddr("https://127.0.0.1"), "https://127.0.0.1:8848");
        assertEquals(abstractServerListManager.repairServerAddr("https://127.0.0.1:8888"), "https://127.0.0.1:8888");
    }

    @Test
    public void testUpdateServerList() throws Exception {
        List<String> serverList = Lists.newArrayList("127.0.0.1", "127.0.0.2", "127.0.0.3");
        abstractServerListManager.updateServerList(serverList);
        List<String> actual = Lists.newArrayList("http://127.0.0.1:8848", "http://127.0.0.2:8848", "http://127.0.0.3:8848");
        assertEquals(abstractServerListManager.getServerList(), actual);
    }

    @Test
    public void testCreateUpdateServerListTask() throws Exception {
        Runnable task = abstractServerListManager.createUpdateServerListTask(() -> Lists.newArrayList("127.0.0.1"));
        task.run();
        assertEquals(abstractServerListManager.getServerList(), Lists.newArrayList("http://127.0.0.1:8848"));
    }

    @Test
    public void testName() throws Exception {
        Properties properties = new Properties();
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        AbstractServerListManager serverListManager = new AbstractServerListManager(clientProperties) {
            @Override
            protected String initServerName(NacosClientProperties properties) {
                return "test";
            }
        };
        Method initName = AbstractServerListManager.class.getDeclaredMethod("initName", NacosClientProperties.class);
        initName.setAccessible(true);

        assertEquals(initName.invoke(serverListManager, clientProperties), "test");

        properties.setProperty(PropertyKeyConst.SERVER_NAME, "test");
        assertEquals(initName.invoke(serverListManager, clientProperties), "test");
    }
}
