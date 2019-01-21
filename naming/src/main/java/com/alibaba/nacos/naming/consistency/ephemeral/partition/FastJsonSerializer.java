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
package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

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
    public <T> byte[] serialize(Map<String, T> dataMap) {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, T> entry : dataMap.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json.toJSONString().getBytes();
    }

    @Override
    public <T> Map<String, T> deserialize(byte[] data, Class<T> clazz) {
        try {
            String dataString = new String(data, "UTF-8");
            JSONObject json = JSON.parseObject(dataString);
            Map<String, T> dataMap = new HashMap<>();
            for (String key : json.keySet()) {
                dataMap.put(key, JSON.parseObject(json.getString(key), clazz));
            }
            return dataMap;
        } catch (Exception e) {
            Loggers.SRV_LOG.error("deserialize data failed.", e);
        }
        return null;
    }
}
