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

package com.alibaba.nacos.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SerializeFactory {

    public static final String JSON_INDEX = "FastJson".toLowerCase();

    private static final Map<String, Serializer> SERIALIZER_MAP = new HashMap<String, Serializer>(4);

    public static String DEFAULT_SERIALIZER = JSON_INDEX;

    static {

        DEFAULT_SERIALIZER = System.getProperty("com.alibaba.nacos.serializer-type", JSON_INDEX).toLowerCase();

        Serializer jsonSerializer = new JsonSerializer();

        SERIALIZER_MAP.put(JSON_INDEX, jsonSerializer);

        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);

        for (Serializer serializer : loader) {
            SERIALIZER_MAP.put(serializer.name().toLowerCase(), serializer);
        }

    }

    public static Serializer getDefault() {
        return SERIALIZER_MAP.get(DEFAULT_SERIALIZER);
    }

    public static Serializer getSerializerDefaultJson(String name) {
        Serializer serializer = SERIALIZER_MAP.get(name.toLowerCase());
        if (serializer == null) {
            return SERIALIZER_MAP.get(JSON_INDEX);
        }
        return serializer;
    }

    private static class JsonSerializer implements Serializer {

        @Override
        public <T> T deSerialize(byte[] data, Class<T> cls) {
            return JSON.parseObject(data, cls);
        }

        @Override
        public <T> T deSerialize(byte[] data, TypeReference<T> reference) {
            return JSON.parseObject(new String(data), reference);
        }

        @Override
        public <T> T deSerialize(byte[] data, String classFullName) {
            try {

                Class<?> cls;

                if (!CLASS_CACHE.containsKey(classFullName)) {
                    synchronized (monitor) {
                        if (!CLASS_CACHE.containsKey(classFullName)) {
                            cls = Class.forName(classFullName);
                            CLASS_CACHE.put(classFullName, cls);
                        }
                    }
                }

                cls = CLASS_CACHE.get(classFullName);

                return (T) deSerialize(data, cls);
            } catch (Exception ignore) {
                return null;
            }
        }

        @Override
        public <T> byte[] serialize(T obj) {
            return JSON.toJSONBytes(obj);
        }

        @Override
        public String name() {
            return JSON_INDEX;
        }
    }

}
