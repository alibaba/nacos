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

import com.alibaba.nacos.consistency.serialize.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializeFactoryTest {
    
    @Test
    void testListSerialize() {
        Serializer serializer = SerializeFactory.getDefault();
        
        List<Integer> logsList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            logsList.add(i);
        }
        byte[] data = serializer.serialize(logsList);
        assertNotEquals(0, data.length);
        
        ArrayList<Integer> list = serializer.deserialize(data, ArrayList.class);
        System.out.println(list);
    }
    
    @Test
    void testMapSerialize() {
        Serializer serializer = SerializeFactory.getDefault();
        Map<Integer, Integer> logsMap = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            logsMap.put(i, i);
        }
        byte[] data = serializer.serialize(logsMap);
        assertNotEquals(0, data.length);
        Map<Integer, Integer> result = serializer.deserialize(data, HashMap.class);
        System.out.println(result);
    }
    
    @Test
    void testSetSerialize() {
        Serializer serializer = SerializeFactory.getDefault();
        Set<Integer> logsMap = new CopyOnWriteArraySet<>();
        for (int i = 0; i < 4; i++) {
            logsMap.add(i);
        }
        
        byte[] data = serializer.serialize(logsMap);
        assertNotEquals(0, data.length);
        Set<Integer> result = serializer.deserialize(data, CopyOnWriteArraySet.class);
        System.out.println(result);
    }
    
    @Test
    void testGetSerializer() {
        Serializer serializer = SerializeFactory.getSerializer("JSON");
        assertTrue(serializer instanceof JacksonSerializer);
    }
}
