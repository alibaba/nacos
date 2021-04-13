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
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProtocolMetaDataTest {
    
    @Test
    public void testProtocolMetaData() throws Exception {
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("test-1", LocalDateTime.now());
        data.put("test_2", LocalDateTime.now());
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
        
        System.out.println(json);
        
        map = new HashMap<>();
        data = new HashMap<>();
        data.put("test-1", LocalDateTime.now());
        data.put("test_2", LocalDateTime.now());
        map.put("global", data);
        
        metaData.load(map);
        
        json = JacksonUtils.toJson(metaData);
        System.out.println(json);
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(2, count.get());
        
    }
    
}
