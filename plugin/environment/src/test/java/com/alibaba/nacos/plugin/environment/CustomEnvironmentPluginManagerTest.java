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

package com.alibaba.nacos.plugin.environment;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import com.alibaba.nacos.plugin.environment.spi.EnvironmentPluginProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CustomEnvironment Plugin Test.
 *
 * @author : huangtianhui
 */
class CustomEnvironmentPluginManagerTest {
    
    @Test
    void testInstance() {
        CustomEnvironmentPluginManager instance = CustomEnvironmentPluginManager.getInstance();
        assertNotNull(instance);
    }
    
    @Test
    void testJoin() {
        CustomEnvironmentPluginManager.join(new CustomEnvironmentPluginService() {
            
            @Override
            public Map<String, Object> customValue(Map<String, Object> property) {
                String pwd = (String) property.get("db.password.0");
                property.put("db.password.0", "test" + pwd);
                // [issue 13367] check property remove
                property.put("db.password.1", null);
                property.put("db.password.2", null);
                property.put("db.password.extra", "extra");
                return property;
            }
            
            @Override
            public Set<String> propertyKey() {
                Set<String> propertyKey = new HashSet<>();
                propertyKey.add("db.password.0");
                propertyKey.add("db.password.1");
                propertyKey.add("db.password.2");
                return propertyKey;
            }
            
            @Override
            public Integer order() {
                return 0;
            }
            
            @Override
            public String pluginName() {
                return "test";
            }
        });
        assertNotNull(CustomEnvironmentPluginManager.getInstance().getPropertyKeys());
        Map<String, Object> sourcePropertyMap = new HashMap<>();
        sourcePropertyMap.put("db.password.0", "nacos");
        Map<String, Object> customValues =
            CustomEnvironmentPluginManager.getInstance().getCustomValues(sourcePropertyMap);
        assertNotNull(customValues);
        // [issue 13367] check property remove
        assertFalse(customValues.containsKey("db.password.1"));
        assertFalse(customValues.containsKey("db.password.2"));
        assertFalse(customValues.containsKey("db.password.extra"));
        
        CustomEnvironmentPluginManager.join(null);
    }
    
    @Test
    void testCustomEnvironmentPluginServiceMethods() {
        CustomEnvironmentPluginService service = new CustomEnvironmentPluginService() {
            
            @Override
            public Map<String, Object> customValue(Map<String, Object> property) {
                property.put("key", "value");
                return property;
            }
            
            @Override
            public Set<String> propertyKey() {
                return Collections.singleton("key");
            }
            
            @Override
            public Integer order() {
                return 1;
            }
            
            @Override
            public String pluginName() {
                return "test";
            }
        };
        
        Map<String, Object> property = new HashMap<>();
        assertEquals(property, service.customValue(property));
        assertEquals(Collections.singleton("key"), service.propertyKey());
        assertEquals(1, service.order());
        assertEquals("test", service.pluginName());
    }
    
    @Test
    void testLoadInitialFromSpiSkipsBlankPluginName() throws Exception {
        List<CustomEnvironmentPluginService> services = getServices();
        List<CustomEnvironmentPluginService> snapshot = new ArrayList<>(services);
        services.clear();
        Method method = CustomEnvironmentPluginManager.class.getDeclaredMethod("loadInitial");
        method.setAccessible(true);
        
        try {
            method.invoke(CustomEnvironmentPluginManager.getInstance());
            
            assertTrue(CustomEnvironmentPluginManager.getInstance().getPropertyKeys()
                .contains("spi.key"));
        } finally {
            services.clear();
            services.addAll(snapshot);
        }
    }
    
    @Test
    void testEnvironmentPluginProvider() {
        EnvironmentPluginProvider provider = new EnvironmentPluginProvider();
        
        assertEquals(PluginType.ENVIRONMENT, provider.getPluginType());
        assertNotNull(provider.getAllPlugins());
    }
    
    @SuppressWarnings("unchecked")
    private List<CustomEnvironmentPluginService> getServices() throws Exception {
        Field field = CustomEnvironmentPluginManager.class.getDeclaredField("SERVICE_LIST");
        field.setAccessible(true);
        return (List<CustomEnvironmentPluginService>) field.get(null);
    }
}
