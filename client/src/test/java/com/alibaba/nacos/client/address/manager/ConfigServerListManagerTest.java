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
import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Config Server List Manager Test.
 *
 * @author misakacoder
 */
public class ConfigServerListManagerTest {
    
    private ConfigServerListManager configServerListManager;
    
    @BeforeEach
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "nacos-dev");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        configServerListManager = new ConfigServerListManager(clientProperties);
    }
    
    @Test
    public void testName() throws Exception {
        assertEquals(configServerListManager.getName(), "fixed-public-127.0.0.1");
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "public");
        properties.setProperty(PropertyKeyConst.SERVER_NAME, "default");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        assertEquals(serverListManager.getName(), "default");
    }
    
    @Test
    public void testGetModuleType() {
        assertEquals(configServerListManager.getModuleType(), ModuleType.CONFIG);
    }
    
    @Test
    public void testGetTenant() {
        assertEquals(configServerListManager.getTenant(), "public");
    }
    
    @Test
    public void testGetNamespace() {
        assertEquals(configServerListManager.getNamespace(), "public");
    }
    
    @Test
    public void testGetContentPath() {
        assertEquals(configServerListManager.getContentPath(), "nacos-dev");
    }
    
    @Test
    public void testNamespace() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, "private");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ReflectUtils.invokeMethod(getMethod("initNamespace", NacosClientProperties.class), configServerListManager,
                clientProperties);
        assertEquals(configServerListManager.getTenant(), "private");
        assertEquals(configServerListManager.getNamespace(), "private");
    }
    
    @Test
    public void testContextPath() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "nacos-prod");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ReflectUtils.invokeMethod(getMethod("initContextPath", NacosClientProperties.class), configServerListManager,
                clientProperties);
        assertEquals(configServerListManager.getContentPath(), "nacos-prod");
    }
    
    private Method getMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ConfigServerListManager.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
