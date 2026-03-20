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

package com.alibaba.nacos.plugin.datafilter.spi;

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

/**
 * {@link DataFilterPluginManager} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class DataFilterPluginManagerTest {
    
    private static final String TEST_SERVICE_NAME = "test-filter";
    
    private DataFilterPluginManager manager;
    
    @Mock
    private DataFilterService mockFilterService;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        manager = DataFilterPluginManager.getInstance();
        Field field = DataFilterPluginManager.class.getDeclaredField("filterServiceMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DataFilterService> serviceMap = (Map<String, DataFilterService>) field.get(manager);
        serviceMap.put(TEST_SERVICE_NAME, mockFilterService);
    }
    
    @Test
    void testGetInstance() {
        assertNotNull(DataFilterPluginManager.getInstance());
    }
    
    @Test
    void testFindFilterServiceExists() {
        Optional<DataFilterService> result = manager.findFilterService(TEST_SERVICE_NAME);
        assertTrue(result.isPresent());
        assertEquals(mockFilterService, result.get());
    }
    
    @Test
    void testFindFilterServiceNotExists() {
        Optional<DataFilterService> result = manager.findFilterService("non-existent");
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetAllPlugins() {
        Map<String, DataFilterService> plugins = manager.getAllPlugins();
        assertNotNull(plugins);
        assertTrue(plugins.containsKey(TEST_SERVICE_NAME));
    }
}
