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
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Address Server List Provider Test.
 *
 * @author misakacoder
 */
public class AddressServerListProviderTest {
    
    @Test
    public void testName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, "8888");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        properties.setProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS, "username=nacos&password=nacos");
        AddressServerListProvider serverListProvider = new AddressServerListProvider();
        serverListProvider.startup(NacosClientProperties.PROTOTYPE.derive(properties), "public", null);
        assertEquals(serverListProvider.getName(), "custom-127.0.0.1_8888_nacos_serverlist_public");
        serverListProvider.shutdown();
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
        String configActual = "http://127.0.0.1:8888/nacos/serverlist?namespace=public&username=nacos&password=nacos";
        assertEquals(getFieldValue(properties, "addressServerUrl", "public", ModuleType.CONFIG), configActual);
        String namingActual = "http://127.0.0.1:8888/nacos/serverlist";
        assertEquals(getFieldValue(properties, "addressServerUrl", "public", ModuleType.NAMING), namingActual);
    }
    
    private Object getFieldValue(Properties properties, String name) throws Exception {
        return getFieldValue(properties, name, null, null);
    }
    
    private Object getFieldValue(Properties properties, String name, String namespace, ModuleType moduleType)
            throws Exception {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        AddressServerListProvider addressServerListProvider = new AddressServerListProvider();
        addressServerListProvider.startup(clientProperties, namespace, moduleType);
        Field field = AddressServerListProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(addressServerListProvider);
        addressServerListProvider.shutdown();
        return value;
    }
}
