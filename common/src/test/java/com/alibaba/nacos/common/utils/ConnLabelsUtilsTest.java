/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * description.
 *
 * @author rong
 * @date 2024-03-01 15:10
 */
public class ConnLabelsUtilsTest {
    
    @Test
    public void testParsePropertyValue2Map() {
        Properties properties = new Properties();
        String property = "property";
        String rawValue = "k1 = v1, k2 = v2";
        properties.put(property, rawValue);
        String property1 = "property2";
        String rawValue1 = "k11=v11, kk2";
        properties.put(property1, rawValue1);
        
        Map<String, String> m = ConnLabelsUtils.parsePropertyValue2Map(properties, property);
        assertEquals(2, m.size());
        assertEquals("v1", m.get("k1"));
        assertEquals("v2", m.get("k2"));
        
        Map<String, String> m1 = ConnLabelsUtils.parsePropertyValue2Map(properties, property1);
        assertEquals(1, m1.size());
        assertEquals("v11", m1.get("k11"));
        assertEquals(null, m1.get("kk2"));
        
        m = ConnLabelsUtils.mergeMapByOrder(m, m1);
        assertEquals(3, m.size());
        assertEquals("v1", m.get("k1"));
        assertEquals("v2", m.get("k2"));
        assertEquals("v11", m.get("k11"));
        
        m = ConnLabelsUtils.addPrefixForEachKey(m, "test_prefix");
        assertEquals(3, m.size());
        m.forEach((k, v) -> {
            assertTrue(k.startsWith("test_prefix"));
        });
    }
    
}
