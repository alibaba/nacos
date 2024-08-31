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

package com.alibaba.nacos.auth.config;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthConfigsTest {
    
    private static final boolean TEST_AUTH_ENABLED = true;
    
    private static final boolean TEST_CACHING_ENABLED = false;
    
    private static final String TEST_SERVER_IDENTITY_KEY = "testKey";
    
    private static final String TEST_SERVER_IDENTITY_VALUE = "testValue";
    
    private static final boolean TEST_ENABLE_UA_WHITE = true;
    
    private AuthConfigs authConfigs;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.core.auth.plugin.test.key", "test");
        authConfigs = new AuthConfigs();
    }
    
    @Test
    void testUpgradeFromEvent() {
        environment.setProperty("nacos.core.auth.enabled", String.valueOf(TEST_AUTH_ENABLED));
        environment.setProperty("nacos.core.auth.caching.enabled", String.valueOf(TEST_CACHING_ENABLED));
        environment.setProperty("nacos.core.auth.server.identity.key", TEST_SERVER_IDENTITY_KEY);
        environment.setProperty("nacos.core.auth.server.identity.value", TEST_SERVER_IDENTITY_VALUE);
        environment.setProperty("nacos.core.auth.enable.userAgentAuthWhite", String.valueOf(TEST_ENABLE_UA_WHITE));
        
        authConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals(TEST_AUTH_ENABLED, authConfigs.isAuthEnabled());
        assertEquals(TEST_CACHING_ENABLED, authConfigs.isCachingEnabled());
        assertEquals(TEST_SERVER_IDENTITY_KEY, authConfigs.getServerIdentityKey());
        assertEquals(TEST_SERVER_IDENTITY_VALUE, authConfigs.getServerIdentityValue());
        assertEquals(TEST_ENABLE_UA_WHITE, authConfigs.isEnableUserAgentAuthWhite());
    }
}
