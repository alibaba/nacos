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

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.pojo.Record;

import java.util.Map;

/**
 * Serializer specially for large map of data
 *
 * @author nkorange
 * @since 1.0.0
 */
public interface Serializer {

    /**
     * Serialize  data with some kind of serializing protocol
     *
     * @param data data to serialize
     * @param <T>  type of data
     * @return byte array of serialized data
     */
    <T> byte[] serialize(T data);

    /**
     * Deserialize byte array data to target type
     *
     * @param data  data to deserialize
     * @param clazz target type
     * @param <T>   target type
     * @return deserialized data map
     */
    <T> T deserialize(byte[] data, Class<T> clazz);

    /**
     * Deserialize byte array data to target type
     *
     * @param <T>   target type
     * @param data  data to deserialize
     * @param clazz target type
     * @return deserialized data map
     */
    <T extends Record> Map<String, Datum<T>> deserializeMap(byte[] data, Class<T> clazz);
}
