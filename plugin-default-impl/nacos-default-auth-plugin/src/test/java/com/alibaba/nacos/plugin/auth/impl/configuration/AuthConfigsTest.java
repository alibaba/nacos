/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthConfigsTest {
    
    private static final boolean TEST_AUTH_ENABLED = true;
    
    private static final boolean TEST_CACHING_ENABLED = false;
    
    private static final String TEST_SERVER_IDENTITY_KEY = "testKey";
    
    private static final String TEST_SERVER_IDENTITY_VALUE = "testValue";
    
    private AuthConfigs authConfigs;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(null);
        ReflectionTestUtils.setField(AuthConfigs.class, "cachingEnabled", null);
        environment.setProperty("nacos.core.auth.plugin.test.key", "test");
        authConfigs = new AuthConfigs();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setIsStandalone(null);
        ReflectionTestUtils.setField(AuthConfigs.class, "cachingEnabled", null);
    }
    
    @Test
    void testUpgradeFromEvent() {
        environment.setProperty("nacos.core.auth.enabled", String.valueOf(TEST_AUTH_ENABLED));
        environment.setProperty("nacos.core.auth.caching.enabled",
            String.valueOf(TEST_CACHING_ENABLED));
        environment.setProperty("nacos.core.auth.server.identity.key", TEST_SERVER_IDENTITY_KEY);
        environment.setProperty("nacos.core.auth.server.identity.value",
            TEST_SERVER_IDENTITY_VALUE);
        
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals(TEST_AUTH_ENABLED, authConfigs.isAuthEnabled());
        assertEquals(TEST_CACHING_ENABLED, authConfigs.isCachingEnabled());
        assertEquals(TEST_SERVER_IDENTITY_KEY, authConfigs.getServerIdentityKey());
        assertEquals(TEST_SERVER_IDENTITY_VALUE, authConfigs.getServerIdentityValue());
    }
    
    @Test
    void testUpgradeAllFieldsAndPluginPropertiesFromEvent() {
        environment.setProperty("nacos.core.auth.enabled", "true");
        environment.setProperty("nacos.core.auth.console.enabled", "false");
        environment.setProperty("nacos.core.auth.caching.enabled", "true");
        environment.setProperty("nacos.core.auth.system.type", "nacos");
        environment.setProperty("nacos.core.auth.server.identity.key", TEST_SERVER_IDENTITY_KEY);
        environment.setProperty("nacos.core.auth.server.identity.value",
            TEST_SERVER_IDENTITY_VALUE);
        environment.setProperty("nacos.core.auth.plugin.test.secret", "value");
        environment.setProperty("nacos.core.auth.nacos.anonymous.ai.enabled", "true");
        
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        authConfigs.setHasGlobalAdminRole(true);
        
        assertTrue(authConfigs.isAuthEnabled());
        assertFalse(authConfigs.isConsoleAuthEnabled());
        assertTrue(authConfigs.isCachingEnabled());
        assertTrue(authConfigs.isAiAnonymousEnabled());
        assertTrue(authConfigs.isHasGlobalAdminRole());
        assertEquals("nacos", authConfigs.getNacosAuthSystemType());
        assertEquals(TEST_SERVER_IDENTITY_KEY, authConfigs.getServerIdentityKey());
        assertEquals(TEST_SERVER_IDENTITY_VALUE, authConfigs.getServerIdentityValue());
        assertEquals("value", authConfigs.getAuthPluginProperties("test").getProperty("secret"));
        assertSame(ServerConfigChangeEvent.class, authConfigs.subscribeType());
    }
    
    @Test
    void testValidateSkipsWhenAllAuthDisabled() {
        environment.setProperty("nacos.core.auth.enabled", "false");
        environment.setProperty("nacos.core.auth.console.enabled", "false");
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertDoesNotThrow(() -> authConfigs.validate());
    }
    
    @Test
    void testValidateRejectsMissingAuthType() {
        environment.setProperty("nacos.core.auth.enabled", "true");
        environment.setProperty("nacos.core.auth.console.enabled", "true");
        environment.setProperty("nacos.core.auth.system.type", "");
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertThrows(NacosException.class, () -> authConfigs.validate());
    }
    
    @Test
    void testValidateSkipsIdentityWhenStandalone() {
        environment.setProperty("nacos.core.auth.enabled", "true");
        environment.setProperty("nacos.core.auth.console.enabled", "true");
        environment.setProperty("nacos.core.auth.system.type", "nacos");
        EnvUtil.setIsStandalone(true);
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertDoesNotThrow(() -> authConfigs.validate());
    }
    
    @Test
    void testValidateRejectsMissingIdentityInCluster() {
        environment.setProperty("nacos.core.auth.enabled", "true");
        environment.setProperty("nacos.core.auth.console.enabled", "true");
        environment.setProperty("nacos.core.auth.system.type", "nacos");
        EnvUtil.setIsStandalone(false);
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertThrows(NacosException.class, () -> authConfigs.validate());
    }
    
    @Test
    void testIsCachingEnabledReadsEnvironmentWhenOverrideMissing() {
        environment.setProperty("nacos.core.auth.caching.enabled", "false");
        ReflectionTestUtils.setField(AuthConfigs.class, "cachingEnabled", null);
        
        assertFalse(authConfigs.isCachingEnabled());
    }
    
    @Test
    void testSetCachingEnabledOverridesEnvironment() {
        environment.setProperty("nacos.core.auth.caching.enabled", "true");
        
        AuthConfigs.setCachingEnabled(false);
        
        assertFalse(authConfigs.isCachingEnabled());
    }
    
    @Test
    void testRefreshPluginPropertiesIgnoresMalformedProperty() {
        environment.setProperty("nacos.core.auth.plugin.malformed", "value");
        
        assertDoesNotThrow(() -> new AuthConfigs());
    }
    
    @Test
    void testOnEventKeepsOldValuesWhenEnvironmentUnavailable() {
        EnvUtil.setEnvironment(null);
        
        assertDoesNotThrow(() -> authConfigs.onEvent(ServerConfigChangeEvent.newEvent()));
    }
    
    @Test
    void testGetAuthPluginPropertiesNeverReturnsNullDuringConcurrentRefresh()
        throws InterruptedException {
        // Reproduces the check-then-act race: previously `getAuthPluginProperties` read
        // the field twice (`containsKey` then `get`), so if a refresh swapped in a map
        // missing the key in between, `get` returned null and the method propagated null
        // to callers instead of falling back to the empty-properties branch.
        int readerThreads = 8;
        int durationMillis = 300;
        String pluginType = "raceProbePlugin";
        String pluginEnabledKey = "nacos.core.auth.plugin." + pluginType + ".enabled";
        MockEnvironment withKey = new MockEnvironment();
        withKey.setProperty(pluginEnabledKey, "true");
        MockEnvironment withoutKey = new MockEnvironment();
        withoutKey.setProperty("nacos.core.auth.plugin.otherPlugin.enabled", "true");
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] readers = new Thread[readerThreads];
        for (int i = 0; i < readerThreads; i++) {
            readers[i] = new Thread(() -> {
                try {
                    start.await();
                    long deadline = System.currentTimeMillis() + durationMillis;
                    while (System.currentTimeMillis() < deadline) {
                        Properties properties = authConfigs.getAuthPluginProperties(pluginType);
                        if (properties == null) {
                            throw new AssertionError(
                                "getAuthPluginProperties returned null mid-refresh");
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "auth-configs-reader-" + i);
            readers[i].start();
        }
        Thread refresher = new Thread(() -> {
            try {
                start.await();
                long deadline = System.currentTimeMillis() + durationMillis;
                int counter = 0;
                while (System.currentTimeMillis() < deadline) {
                    EnvUtil.setEnvironment((counter++ % 2 == 0) ? withKey : withoutKey);
                    authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "auth-configs-refresher");
        refresher.start();
        start.countDown();
        refresher.join(TimeUnit.SECONDS.toMillis(5));
        for (Thread reader : readers) {
            reader.join(TimeUnit.SECONDS.toMillis(5));
        }
        Throwable observed = failure.get();
        if (observed != null) {
            throw new AssertionError(observed);
        }
    }
}
