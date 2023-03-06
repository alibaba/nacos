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

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapUtilTest {
    
    @Test
    public void testMap() {
        Map<Object, Object> map = new HashMap<Object, Object>();
        
        MapUtil.putIfValNoNull(map, "key-1", null);
        Assert.assertFalse(map.containsKey("key-1"));
        
        MapUtil.putIfValNoEmpty(map, "key-str", null);
        Assert.assertFalse(map.containsKey("key-str"));
        
        MapUtil.putIfValNoEmpty(map, "key-str", "");
        Assert.assertFalse(map.containsKey("key-str"));
        
        MapUtil.putIfValNoEmpty(map, "key-str", "1");
        Assert.assertTrue(map.containsKey("key-str"));
        
        MapUtil.putIfValNoEmpty(map, "key-list", null);
        Assert.assertFalse(map.containsKey("key-list"));
        
        MapUtil.putIfValNoEmpty(map, "key-list", Collections.emptyList());
        Assert.assertFalse(map.containsKey("key-list"));
        
        MapUtil.putIfValNoEmpty(map, "key-list", Collections.singletonList(1));
        Assert.assertTrue(map.containsKey("key-list"));
        
        MapUtil.putIfValNoEmpty(map, "key-map", null);
        Assert.assertFalse(map.containsKey("key-map"));
        
        MapUtil.putIfValNoEmpty(map, "key-map", Collections.emptyMap());
        Assert.assertFalse(map.containsKey("key-map"));
        
        Map<String, String> map1 = new HashMap<String, String>();
        map1.put("1123", "123");
        
        MapUtil.putIfValNoEmpty(map, "key-map", map1);
        Assert.assertTrue(map.containsKey("key-map"));
    }
    
}
