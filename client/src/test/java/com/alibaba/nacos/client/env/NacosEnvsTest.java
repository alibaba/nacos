/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.env;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;

public class NacosEnvsTest {
    
    static MockedStatic<NacosEnvironmentFactory> mockedStatic;
    
    @BeforeClass
    public static void before() {
        mockedStatic = Mockito.mockStatic(NacosEnvironmentFactory.class);
        mockedStatic.when(NacosEnvironmentFactory::createEnvironment).thenReturn(createProxy());
        
    }
    
    @AfterClass
    public static void teardown() {
        if (mockedStatic != null) {
            mockedStatic.close();
        }
    }
    
    private static NacosEnvironment createProxy() {
        return (NacosEnvironment) Proxy.newProxyInstance(NacosEnvironmentFactory.class.getClassLoader(),
                new Class[] {NacosEnvironment.class}, new NacosEnvironmentFactory.NacosEnvironmentDelegate() {
                    volatile NacosEnvironment environment;
                    
                    @Override
                    public void init(Properties properties) {
                        environment = new SearchableEnvironment(properties);
                    }
                    
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (environment == null) {
                            throw new IllegalStateException(
                                    "Nacos environment doesn't init, please call NEnvs#init method then try it again.");
                        }
                        return method.invoke(environment, args);
                    }
                });
    }
    
    @Test
    public void testGetProperty() {
        
        final Properties properties = new Properties();
        properties.setProperty("nacos.home", "/home/nacos");
        NacosEnvs.init(properties);
        final String value = NacosEnvs.getProperty("nacos.home");
        
        Assert.assertEquals("/home/nacos", value);
    }
    
    @Test
    public void testGetPropertyDefaultValue() {
        
        final Properties properties = new Properties();
        NacosEnvs.init(properties);
        final String value = NacosEnvs.getProperty("nacos.home", "/home/default_value");
        
        Assert.assertEquals("/home/default_value", value);
    }
    
    @Test
    public void testGetBoolean() {
        final Properties properties = new Properties();
        properties.setProperty("use.cluster", "true");
        NacosEnvs.init(properties);
        
        final Boolean value = NacosEnvs.getBoolean("use.cluster");
        Assert.assertTrue(value);
    }
    
    @Test
    public void testGetBooleanDefaultValue() {
        final Properties properties = new Properties();
        NacosEnvs.init(properties);
        
        final Boolean value = NacosEnvs.getBoolean("use.cluster", false);
        Assert.assertFalse(value);
    }
    
    @Test
    public void testGetInteger() {
        final Properties properties = new Properties();
        properties.setProperty("max.timeout", "200");
        NacosEnvs.init(properties);
        
        final Integer value = NacosEnvs.getInteger("max.timeout");
        
        Assert.assertEquals(200, value.intValue());
    }
    
    @Test
    public void testGetIntegerDefaultValue() {
        final Properties properties = new Properties();
        NacosEnvs.init(properties);
        
        final Integer value = NacosEnvs.getInteger("max.timeout", 400);
        Assert.assertEquals(400, value.intValue());
    }
    
    @Test
    public void testGetLong() {
        final Properties properties = new Properties();
        properties.setProperty("connection.timeout", "200");
        NacosEnvs.init(properties);
        
        final Long value = NacosEnvs.getLong("connection.timeout");
        Assert.assertEquals(200L, value.longValue());
    }
    
    @Test
    public void testGetLongDefault() {
        final Properties properties = new Properties();
        NacosEnvs.init(properties);
        final Long value = NacosEnvs.getLong("connection.timeout", 400L);
        Assert.assertEquals(400L, value.longValue());
    }
    
    @Test
    public void testGetPropertyJvmFirst() {
        System.setProperty("nacos.envs.search", "jvm");
        System.setProperty("nacos.home", "/home/jvm_first");
        
        Properties properties = new Properties();
        properties.setProperty("nacos.home", "/home/properties_first");
        
        NacosEnvs.init(properties);
        final String value = NacosEnvs.getProperty("nacos.home");
        
        Assert.assertEquals("/home/jvm_first", value);
        System.clearProperty("nacos.envs.search");
        System.clearProperty("nacos.home");
    }
    
    @Test
    public void testGetPropertyDefaultSetting() {
        Properties properties = new Properties();
        
        NacosEnvs.init(properties);
        final String value = NacosEnvs.getProperty("nacos.home.default.test");
        
        Assert.assertEquals("/home/default_setting", value);
        
    }
    
}
