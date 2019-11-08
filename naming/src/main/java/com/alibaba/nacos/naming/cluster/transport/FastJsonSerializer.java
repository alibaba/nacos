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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Use FastJSON to serialize data
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class FastJsonSerializer implements Serializer {

    @Override
    public <T> byte[] serialize(T data) {
        return JSON.toJSONBytes(data);
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        return JSON.parseObject(new String(data, StandardCharsets.UTF_8), clazz);
    }

    @Override
    public <T> T deserialize(byte[] data, TypeReference<T> clazz) {
        try {
            String dataString = new String(data, StandardCharsets.UTF_8);
            return JSON.parseObject(dataString, clazz);
        } catch (Exception e) {
            Loggers.SRV_LOG.error("deserialize data failed.", e);
        }
        return null;
    }

    @Override
    public <T extends Record> Map<String, Datum<T>> deserializeMap(byte[] data, Class<T> clazz) {
        try {
            String dataString = new String(data, StandardCharsets.UTF_8);
            Map<String, JSONObject> dataMap = JSON.parseObject(dataString, new TypeReference<Map<String, JSONObject>>() {
            });

            Map<String, Datum<T>> resultMap = new HashMap<>(dataMap.size());
            for (Map.Entry<String, JSONObject> entry : dataMap.entrySet()) {

                Datum<T> datum = new Datum<>();
                datum.timestamp.set(entry.getValue().getLongValue("timestamp"));
                datum.key = entry.getValue().getString("key");
                datum.value = JSON.parseObject(entry.getValue().getJSONObject("value").toJSONString(), clazz);
                resultMap.put(entry.getKey(), datum);
            }

            return resultMap;
        } catch (Exception e) {
            Loggers.SRV_LOG.error("deserialize data failed.", e);
        }
        return null;
    }
}
