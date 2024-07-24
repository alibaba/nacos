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

package com.alibaba.nacos.sys.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("prefix")
@SpringBootTest(classes = PropertiesUtilTest.class)
class PropertiesUtilTest {
    
    @Autowired
    private ConfigurableEnvironment environment;
    
    @Test
    @SuppressWarnings("unchecked")
    void testGetPropertiesWithPrefixForMap() {
        Map<String, Object> actual = PropertiesUtil.getPropertiesWithPrefixForMap(environment, "nacos.prefix");
        assertEquals(3, actual.size());
        for (Map.Entry<String, Object> entry : actual.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> subMap = (Map<String, Object>) entry.getValue();
            switch (key) {
                case "one":
                    assertEquals("1", subMap.get("value"));
                    break;
                case "two":
                    assertEquals("2", subMap.get("value"));
                    break;
                case "three":
                    assertEquals("3", subMap.get("value"));
                    break;
                default:
                    throw new RuntimeException();
            }
        }
    }
    
    @Test
    void testGetPropertiesWithPrefix() {
        Properties actual = PropertiesUtil.getPropertiesWithPrefix(environment, "nacos.prefix");
        assertEquals(3, actual.size());
    }
    
    @Test
    void testHandleSpringBinder() {
        Map properties = PropertiesUtil.handleSpringBinder(environment, "nacos.prefix", Map.class);
        assertEquals(3, properties.size());
    }
}
