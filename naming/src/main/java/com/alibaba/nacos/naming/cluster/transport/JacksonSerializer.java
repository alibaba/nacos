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

package com.alibaba.nacos.naming.cluster.transport;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.pojo.Record;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Use Jackson to serialize data.
 *
 * @author yangyi
 */
@Component
public class JacksonSerializer implements Serializer {
    
    private static final String TIMESTAMP_KEY = "timestamp";
    
    private static final String KEY = "key";
    
    private static final String VALUE = "value";
    
    @Override
    public <T> byte[] serialize(T data) {
        return JacksonUtils.toJsonBytes(data);
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        return JacksonUtils.toObj(data, clazz);
    }
    
    @Override
    public <T extends Record> Map<String, Datum<T>> deserializeMap(byte[] data, Class<T> clazz) {
        Map<String, Datum<T>> resultMap;
        try {
            resultMap = JacksonUtils.toObj(data, new TypeReference<Map<String, Datum<T>>>() {
            });
        } catch (Exception e) {
            Map<String, JsonNode> dataMap = JacksonUtils.toObj(data, new TypeReference<Map<String, JsonNode>>() {
            });
            resultMap = new HashMap<>(dataMap.size());
            for (Map.Entry<String, JsonNode> entry : dataMap.entrySet()) {
                Datum<T> datum = new Datum<>();
                datum.timestamp.set(entry.getValue().get(TIMESTAMP_KEY).asLong());
                datum.key = entry.getValue().get(KEY).asText();
                datum.value = JacksonUtils.toObj(entry.getValue().get(VALUE).toString(), clazz);
                resultMap.put(entry.getKey(), datum);
            }
        }
        return resultMap;
    }
}
