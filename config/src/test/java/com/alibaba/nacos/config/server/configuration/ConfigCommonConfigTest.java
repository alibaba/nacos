/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;

/**
 * Nacos config common configs test.
 *
 * @author blake.qiu
 */
public class ConfigCommonConfigTest {
    
    private ConfigCommonConfig commonConfig;
    
    private MockEnvironment environment;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        Constructor<ConfigCommonConfig> declaredConstructor = ConfigCommonConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        commonConfig = declaredConstructor.newInstance();
    }
    
    @Test
    public void getMaxPushRetryTimes() {
        Integer property = EnvUtil.getProperty("nacos.config.push.maxRetryTime", Integer.class, 50);
        assertEquals(property.intValue(), commonConfig.getMaxPushRetryTimes());
    }
    
    @Test
    public void setMaxPushRetryTimes() {
        int maxPushRetryTimesOld = commonConfig.getMaxPushRetryTimes();
        commonConfig.setMaxPushRetryTimes(100);
        assertEquals(100, commonConfig.getMaxPushRetryTimes());
        commonConfig.setMaxPushRetryTimes(maxPushRetryTimesOld);
    }
    
    @Test
    public void testUpgradeFromEvent() {
        environment.setProperty("nacos.config.push.maxRetryTime", "100");
        commonConfig.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals(100, commonConfig.getMaxPushRetryTimes());
    }
    
    @Test
    public void testInitConfigFormEnv() throws ReflectiveOperationException {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.config.push.maxRetryTime", "6");
        Constructor<ConfigCommonConfig> declaredConstructor = ConfigCommonConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        ConfigCommonConfig configCommonConfig = declaredConstructor.newInstance();
        Assert.assertEquals(6, configCommonConfig.getMaxPushRetryTimes());
    }
}
