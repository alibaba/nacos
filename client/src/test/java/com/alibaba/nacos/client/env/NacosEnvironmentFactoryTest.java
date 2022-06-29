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

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Properties;

public class NacosEnvironmentFactoryTest {
    
    @Test(expected = IllegalStateException.class)
    public void testCreateEnvironment() {
        final NacosEnvironment environment = NacosEnvironmentFactory.createEnvironment();
        Assert.assertNotNull(environment);
        Assert.assertTrue(Proxy.isProxyClass(environment.getClass()));
        environment.getProperty("test.exception");
    }
    
    @Test
    public void testNacosEnvInit() {
        final NacosEnvironment environment = NacosEnvironmentFactory.createEnvironment();
        final NacosEnvironmentFactory.NacosEnvironmentDelegate invocationHandler =
                (NacosEnvironmentFactory.NacosEnvironmentDelegate) Proxy.getInvocationHandler(
                environment);
        Properties properties = new Properties();
        properties.setProperty("init.nacos", "true");
        
        invocationHandler.init(properties);
        
        final String property = environment.getProperty("init.nacos");
        Assert.assertEquals("true", property);
    }
    
}
