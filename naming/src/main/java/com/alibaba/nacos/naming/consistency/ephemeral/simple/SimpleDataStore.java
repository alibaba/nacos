/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.ephemeral.simple;

import com.alibaba.nacos.naming.consistency.Datum;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yet another simple in-memory data store
 *
 * @author lostcharlie
 */
@Component
public class SimpleDataStore {
    private Map<String, SimpleDatum> dataMap = new ConcurrentHashMap<>(1024);

    public void put(String key, SimpleDatum value) {
        dataMap.put(key, value);
    }

    public SimpleDatum remove(String key) {
        return dataMap.remove(key);
    }

    public Set<String> keys() {
        return dataMap.keySet();
    }

    public SimpleDatum get(String key) {
        return dataMap.get(key);
    }

    public boolean contains(String key) {
        return dataMap.containsKey(key);
    }

    public Map<String, SimpleDatum> getDataMap() {
        return dataMap;
    }
}
