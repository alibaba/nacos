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

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.Observer;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProtocolMetaDataTest {
    
    @Test
    void testProtocolMetaData() throws Exception {
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("test-1", new Date());
        data.put("test_2", new Date());
        map.put("global", data);
        
        ProtocolMetaData metaData = new ProtocolMetaData();
        
        metaData.load(map);
        
        String json = JacksonUtils.toJson(metaData);
        AtomicInteger count = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(2);
        
        metaData.subscribe("global", "test-1", o -> {
            ProtocolMetaData.ValueItem item = (ProtocolMetaData.ValueItem) o;
            System.out.println(item.getData());
            count.incrementAndGet();
            latch.countDown();
        });
        
        map = new HashMap<>();
        data = new HashMap<>();
        data.put("test-1", new Date());
        data.put("test_2", new Date());
        map.put("global", data);
        
        metaData.load(map);
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
        
        assertEquals(2, count.get());
        
    }
    
    @Test
    void testGetWithBlankSubKey() {
        ProtocolMetaData metaData = new ProtocolMetaData();
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        map.put("group1", data);
        metaData.load(map);
        
        Object result = metaData.get("group1", "");
        assertNotNull(result);
    }
    
    @Test
    void testGetWithSubKey() {
        ProtocolMetaData metaData = new ProtocolMetaData();
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        map.put("group1", data);
        metaData.load(map);
        
        Object result = metaData.get("group1", "key1");
        assertNotNull(result);
    }
    
    @Test
    void testGetWithNonExistGroup() {
        ProtocolMetaData metaData = new ProtocolMetaData();
        Object result = metaData.get("nonExist", "key1");
        assertNull(result);
    }
    
    @Test
    void testUnSubscribe() {
        ProtocolMetaData metaData = new ProtocolMetaData();
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        map.put("group1", data);
        metaData.load(map);
        
        Observer observer = o -> {
        };
        metaData.subscribe("group1", "key1", observer);
        metaData.unSubscribe("group1", "key1", observer);
    }
    
    @Test
    void testUnSubscribeNonExistKey() {
        ProtocolMetaData metaData = new ProtocolMetaData();
        Observer observer = o -> {
        };
        metaData.unSubscribe("newGroup", "nonExistKey", observer);
    }
    
    @Test
    void testGetMetaDataMap() {
        ProtocolMetaData metaData = new ProtocolMetaData();
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        map.put("group1", data);
        metaData.load(map);
        
        Map<String, Map<Object, Object>> result = metaData.getMetaDataMap();
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testValueItemGetPath() {
        ProtocolMetaData.ValueItem item = new ProtocolMetaData.ValueItem("test/path");
        assertEquals("test/path", item.getPath());
    }
    
}
