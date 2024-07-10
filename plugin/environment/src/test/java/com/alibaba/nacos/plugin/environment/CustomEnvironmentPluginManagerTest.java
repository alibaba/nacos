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

import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
                return property;
            }
            
            @Override
            public Set<String> propertyKey() {
                Set<String> propertyKey = new HashSet<>();
                propertyKey.add("db.password.0");
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
        assertNotNull(CustomEnvironmentPluginManager.getInstance().getCustomValues(sourcePropertyMap));
    }
}
