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

package com.alibaba.nacos.client.address.provider;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Properties Server List Provider Test.
 *
 * @author misakacoder
 */
public class PropertiesServerListProviderTest {
    
    @Test
    public void testName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "http://127.0.0.1,127.0.0.2:8888;127.0.0.3");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        PropertiesServerListProvider serverListProvider = new PropertiesServerListProvider();
        serverListProvider.startup(NacosClientProperties.PROTOTYPE.derive(properties), "public", null);
        assertEquals(serverListProvider.getName(), "fixed-public-127.0.0.1-127.0.0.2_8888-127.0.0.3");
        serverListProvider.shutdown();
    }
    
    @Test
    public void testServerList() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "http://127.0.0.1,127.0.0.2:8888;127.0.0.3");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        PropertiesServerListProvider serverListProvider = new PropertiesServerListProvider();
        serverListProvider.startup(NacosClientProperties.PROTOTYPE.derive(properties), "public", null);
        List<String> serverList = serverListProvider.getServerList();
        List<String> actual = Lists.newArrayList("http://127.0.0.1", "127.0.0.2:8888", "127.0.0.3");
        assertEquals(serverList, actual);
        serverListProvider.shutdown();
    }
    
    @Test
    public void getNameSuffix() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "http://127.0.0.1,127.0.0.2:8888;127.0.0.3");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        PropertiesServerListProvider serverListProvider = new PropertiesServerListProvider();
        serverListProvider.startup(NacosClientProperties.PROTOTYPE.derive(properties), "public", null);
        assertEquals(ReflectUtils.invokeMethod(getMethod("getNameSuffix"), serverListProvider),
                "127.0.0.1-127.0.0.2_8888-127.0.0.3");
        serverListProvider.shutdown();
    }
    
    private Method getMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = PropertiesServerListProvider.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
