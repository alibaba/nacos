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

package com.alibaba.nacos.core.utils;

import com.alibaba.fastjson.JSON;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SerializeFactory {

    private static final Map<String, Serializer> SERIALIZER_MAP = new HashMap<>();

    public static final String JSON_INDEX = "json";
    public static final String KRYO_INDEX = "kryo";

    static {
        SERIALIZER_MAP.put(JSON_INDEX, new JsonSerializer());
        SERIALIZER_MAP.put(KRYO_INDEX, new KryoSerializer());
    }

    public static Serializer getSerializerDefaultJson(String name) {
        return SERIALIZER_MAP.getOrDefault(name, SERIALIZER_MAP.get(JSON_INDEX));
    }

    private static class KryoSerializer implements Serializer {

        @Override
        public <T> T deSerialize(byte[] data, Class<T> cls) {
            return null;
        }

        @Override
        public <T> T deSerialize(byte[] data, String classFullName) {
            return null;
        }

        @Override
        public byte[] serialize(Object obj) {
            return new byte[0];
        }
    }

    private static class JsonSerializer implements Serializer {

        @Override
        public <T> T deSerialize(byte[] data, Class<T> cls) {
            return JSON.parseObject(data, cls);
        }

        @Override
        public <T> T deSerialize(byte[] data, String classFullName) {
            try {
                final Class<?> cls = Class.forName(classFullName);
                return (T) deSerialize(data, cls);
            } catch (Exception ignore) {
                return null;
            }
        }

        @Override
        public byte[] serialize(Object obj) {
            return JSON.toJSONBytes(obj);
        }
    }

}
