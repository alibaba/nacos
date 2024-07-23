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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nacos config common configs test.
 *
 * @author blake.qiu
 */
class ConfigCommonConfigTest {
    
    private ConfigCommonConfig commonConfig;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        Constructor<ConfigCommonConfig> declaredConstructor = ConfigCommonConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        commonConfig = declaredConstructor.newInstance();
    }
    
    @Test
    void getMaxPushRetryTimes() {
        Integer property = EnvUtil.getProperty("nacos.config.push.maxRetryTime", Integer.class, 50);
        assertEquals(property.intValue(), commonConfig.getMaxPushRetryTimes());
    }
    
    @Test
    void setMaxPushRetryTimes() {
        int maxPushRetryTimesOld = commonConfig.getMaxPushRetryTimes();
        commonConfig.setMaxPushRetryTimes(100);
        assertEquals(100, commonConfig.getMaxPushRetryTimes());
        commonConfig.setMaxPushRetryTimes(maxPushRetryTimesOld);
    }
    
    @Test
    void testSetDerbyOpsEnabled() {
        assertFalse(commonConfig.isDerbyOpsEnabled());
        commonConfig.setDerbyOpsEnabled(true);
        assertTrue(commonConfig.isDerbyOpsEnabled());
    }
    
    @Test
    void testUpgradeFromEvent() {
        environment.setProperty("nacos.config.push.maxRetryTime", "100");
        environment.setProperty("nacos.config.derby.ops.enabled", "true");
        commonConfig.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals(100, commonConfig.getMaxPushRetryTimes());
        assertTrue(commonConfig.isDerbyOpsEnabled());
    }
    
    @Test
    void testInitConfigFormEnv() throws ReflectiveOperationException {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.config.push.maxRetryTime", "6");
        Constructor<ConfigCommonConfig> declaredConstructor = ConfigCommonConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        ConfigCommonConfig configCommonConfig = declaredConstructor.newInstance();
        assertEquals(6, configCommonConfig.getMaxPushRetryTimes());
    }
}
