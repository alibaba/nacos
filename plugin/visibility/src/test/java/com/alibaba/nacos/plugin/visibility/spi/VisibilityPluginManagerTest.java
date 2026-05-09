/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.visibility.spi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class VisibilityPluginManagerTest {
    
    private static final String TEST_SERVICE_NAME = "test-visibility";
    
    private static final String VISIBILITY_ENABLED_KEY = "nacos.plugin.visibility.enabled";
    
    private VisibilityPluginManager manager;
    
    @Mock
    private VisibilityService mockVisibilityService;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        System.clearProperty(VISIBILITY_ENABLED_KEY);
        manager = VisibilityPluginManager.getInstance();
        Field field = VisibilityPluginManager.class.getDeclaredField("visibilityServiceMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, VisibilityService> serviceMap = (Map<String, VisibilityService>) field.get(
            manager);
        serviceMap.put(TEST_SERVICE_NAME, mockVisibilityService);
    }
    
    @Test
    void testGetInstance() {
        assertNotNull(VisibilityPluginManager.getInstance());
    }
    
    @Test
    void testFindVisibilityServiceExists() {
        Optional<VisibilityService> result = manager.findVisibilityService(TEST_SERVICE_NAME);
        assertTrue(result.isPresent());
        assertEquals(mockVisibilityService, result.get());
    }
    
    @Test
    void testFindVisibilityServiceWhenVisibilityPluginDisabled() {
        System.setProperty(VISIBILITY_ENABLED_KEY, "false");
        Optional<VisibilityService> result = manager.findVisibilityService(TEST_SERVICE_NAME);
        assertFalse(result.isPresent());
        System.clearProperty(VISIBILITY_ENABLED_KEY);
    }
}
