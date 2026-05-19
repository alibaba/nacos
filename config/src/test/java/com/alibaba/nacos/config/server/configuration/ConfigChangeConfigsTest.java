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
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nacos config change configs test.
 *
 * @author liyunfei
 **/
class ConfigChangeConfigsTest {
    
    private ConfigChangeConfigs configChangeConfigs;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        environment.setProperty("nacos.core.config.plugin.mockPlugin.enabled", "true");
        EnvUtil.setEnvironment(environment);
        configChangeConfigs = new ConfigChangeConfigs();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testEnable() {
        assertTrue(Boolean.parseBoolean(
            configChangeConfigs.getPluginProperties("mockPlugin").getProperty("enabled")));
    }
    
    @Test
    void testUpgradeEnable() {
        environment.setProperty("nacos.core.config.plugin.mockPlugin.enabled", "false");
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertFalse(Boolean.parseBoolean(
            configChangeConfigs.getPluginProperties("mockPlugin").getProperty("enabled")));
    }
    
    @Test
    void testGetPluginPropertiesReturnsEmptyForUnknownType() {
        Properties properties = configChangeConfigs.getPluginProperties("notRegistered");
        assertNotNull(properties);
        assertTrue(properties.isEmpty());
        assertNull(properties.getProperty("enabled"));
    }
    
    @Test
    void testRefreshPluginPropertiesIgnoresPropertyLoadingException() {
        try (MockedStatic<PropertiesUtil> mocked = Mockito.mockStatic(PropertiesUtil.class)) {
            mocked.when(() -> PropertiesUtil.getPropertiesWithPrefix(environment,
                ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX))
                .thenThrow(new RuntimeException("failed"));
            
            configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        }
        
        assertTrue(configChangeConfigs.getPluginProperties("mockPlugin")
            .containsKey("enabled"));
    }
    
    @Test
    void testGetPluginPropertiesNeverReturnsNullDuringConcurrentRefresh()
        throws InterruptedException {
        // Reproduces the check-then-act race: previously `getPluginProperties` reloaded
        // the field between `containsKey` and `get`, so if a refresh swapped in a map
        // missing the key in between, `get` returned null and the method propagated null.
        // The fix reads the field once and treats a null lookup as the empty-properties
        // branch. Two environments are alternated: one with the probe key, one without.
        int readerThreads = 8;
        int durationMillis = 300;
        String pluginType = "raceProbePlugin";
        String pluginEnabledKey = "nacos.core.config.plugin." + pluginType + ".enabled";
        MockEnvironment withKey = new MockEnvironment();
        withKey.setProperty(pluginEnabledKey, "true");
        MockEnvironment withoutKey = new MockEnvironment();
        withoutKey.setProperty("nacos.core.config.plugin.otherPlugin.enabled", "true");
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] readers = new Thread[readerThreads];
        for (int i = 0; i < readerThreads; i++) {
            readers[i] = new Thread(() -> {
                try {
                    start.await();
                    long deadline = System.currentTimeMillis() + durationMillis;
                    while (System.currentTimeMillis() < deadline) {
                        Properties properties = configChangeConfigs.getPluginProperties(pluginType);
                        if (properties == null) {
                            throw new AssertionError(
                                "getPluginProperties returned null mid-refresh");
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "config-change-reader-" + i);
            readers[i].start();
        }
        Thread refresher = new Thread(() -> {
            try {
                start.await();
                long deadline = System.currentTimeMillis() + durationMillis;
                int counter = 0;
                while (System.currentTimeMillis() < deadline) {
                    EnvUtil.setEnvironment((counter++ % 2 == 0) ? withKey : withoutKey);
                    configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "config-change-refresher");
        refresher.start();
        
        start.countDown();
        refresher.join(TimeUnit.SECONDS.toMillis(5));
        for (Thread reader : readers) {
            reader.join(TimeUnit.SECONDS.toMillis(5));
        }
        
        if (failure.get() != null) {
            throw new AssertionError("Concurrent reader observed failure", failure.get());
        }
    }
    
}
