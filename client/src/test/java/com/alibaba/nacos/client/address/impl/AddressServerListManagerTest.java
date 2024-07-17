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
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AddressServerListManagerTest.
 *
 * @author misakacoder
 */
public class AddressServerListManagerTest {

    @Test
    public void testName() throws Exception {
        Properties properties = new Properties();
        System.setProperty("nacos.endpoint", "127.0.0.1");
        properties.setProperty(PropertyKeyConst.ENDPOINT, "${nacos.endpoint}");
        properties.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, "8888");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        AbstractServerListManager abstractServerListManager = create(properties);
        String name = abstractServerListManager.getName();
        assertEquals(name, "address-server-127.0.0.1_8888_nacos_serverlist-public");
        abstractServerListManager.shutdown();
    }

    @Test
    public void testEndpoint() throws Exception {
        Properties properties = new Properties();
        assertEquals(getFieldValue(properties, "endpoint"), "");

        properties.setProperty(PropertyKeyConst.ENDPOINT, "0.0.0.0");
        assertEquals(getFieldValue(properties, "endpoint"), "0.0.0.0");
    }

    @Test
    public void testUseEndpointParsingRule() throws Exception {
        System.setProperty("nacos.endpoint", "127.0.0.1");
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.ENDPOINT, "${nacos.endpoint}");
        properties.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "false");
        assertEquals(getFieldValue(properties, "endpoint"), "${nacos.endpoint}");

        properties.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        assertEquals(getFieldValue(properties, "endpoint"), "127.0.0.1");
    }

    @Test
    public void testEndpointPort() throws Exception {
        Properties properties = new Properties();
        assertEquals(getFieldValue(properties, "endpointPort"), 8080);

        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, "8888");
        assertEquals(getFieldValue(properties, "endpointPort"), 8888);
    }

    @Test
    public void testEndpointContextPath() throws Exception {
        Properties properties = new Properties();
        assertEquals(getFieldValue(properties, "endpointContextPath"), "nacos");

        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "nacos-prod");
        assertEquals(getFieldValue(properties, "endpointContextPath"), "nacos-prod");
    }

    @Test
    public void testEndpointClusterName() throws Exception {
        Properties properties = new Properties();
        assertEquals(getFieldValue(properties, "endpointClusterName"), "serverlist");

        properties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "server-list");
        assertEquals(getFieldValue(properties, "endpointClusterName"), "server-list");
    }

    @Test
    public void testAddressServerUrl() throws Exception {
        Properties properties = new Properties();
        System.setProperty("nacos.endpoint", "127.0.0.1");
        properties.setProperty(PropertyKeyConst.ENDPOINT, "${nacos.endpoint}");
        properties.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, "8888");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        properties.setProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS, "username=nacos&password=nacos");
        String actual = "http://127.0.0.1:8888/nacos/serverlist?namespace=public&username=nacos&password=nacos";
        assertEquals(getFieldValue(properties, "addressServerUrl"), actual);
    }

    @Test
    public void testReadServerList() throws Exception {
        final AddressServerListManager addressServerListManager = Mockito.mock(AddressServerListManager.class, Answers.CALLS_REAL_METHODS);

        Field nacosRestTemplate = AddressServerListManager.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplate.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.set(nacosRestTemplate, nacosRestTemplate.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

        NacosRestTemplate restTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setCode(200);
        result.setData(String.join("\n", "127.0.0.1", "127.0.0.2", "127.0.0.3"));
        when(restTemplate.get(any(), any(), any(), any())).thenReturn(result);

        nacosRestTemplate.set(addressServerListManager, restTemplate);

        Method readServerList = AddressServerListManager.class.getDeclaredMethod("readServerList");
        readServerList.setAccessible(true);
        Object serverList = readServerList.invoke(addressServerListManager);

        assertEquals(serverList, Lists.newArrayList("127.0.0.1", "127.0.0.2", "127.0.0.3"));
    }

    private AddressServerListManager create(Properties properties) throws Exception {
        properties.setProperty("initServerListRetryTime", "0");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        return new AddressServerListManager(clientProperties);
    }

    private Object getFieldValue(Properties properties, String name) throws Exception {
        AddressServerListManager addressServerListManager = create(properties);
        Field field = AddressServerListManager.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(addressServerListManager);
        addressServerListManager.shutdown();
        return value;
    }
}
