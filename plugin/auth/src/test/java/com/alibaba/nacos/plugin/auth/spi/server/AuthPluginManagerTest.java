/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.spi.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthPluginManager} unit test.
 *
 * @author wuyfee
 * @date 2021-08-12 12:56
 */

@ExtendWith(MockitoExtension.class)
class AuthPluginManagerTest {
    
    private static final String TYPE = "test";
    
    private AuthPluginManager authPluginManager;
    
    @Mock
    private AuthPluginService authPluginService;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        authPluginManager = AuthPluginManager.getInstance();
        Class<AuthPluginManager> authPluginManagerClass = AuthPluginManager.class;
        Field authPlugins = authPluginManagerClass.getDeclaredField("authServiceMap");
        authPlugins.setAccessible(true);
        Map<String, AuthPluginService> authServiceMap = (Map<String, AuthPluginService>) authPlugins.get(authPluginManager);
        authServiceMap.put(TYPE, authPluginService);
    }
    
    @Test
    void testGetInstance() {
        AuthPluginManager instance = AuthPluginManager.getInstance();
        
        assertNotNull(instance);
    }
    
    @Test
    void testFindAuthServiceSpiImpl() {
        Optional<AuthPluginService> authServiceImpl = authPluginManager.findAuthServiceSpiImpl(TYPE);
        assertTrue(authServiceImpl.isPresent());
    }
    
}
