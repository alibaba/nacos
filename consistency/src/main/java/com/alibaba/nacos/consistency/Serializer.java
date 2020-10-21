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

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serialization interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Serializer {
    
    Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(8);
    
    /**
     * Deserialize the data.
     *
     * @param data byte[]
     * @param <T>  class type
     * @return target object instance
     */
    <T> T deserialize(byte[] data);
    
    /**
     * Deserialize the data.
     *
     * @param data byte[]
     * @param cls  class
     * @param <T>  class type
     * @return target object instance
     */
    <T> T deserialize(byte[] data, Class<T> cls);
    
    /**
     * Deserialize the data.
     *
     * @param data byte[]
     * @param type data type
     * @param <T>  class type
     * @return target object instance
     */
    <T> T deserialize(byte[] data, Type type);
    
    /**
     * Deserialize the data.
     *
     * @param data          byte[]
     * @param classFullName class full name
     * @param <T>           class type
     * @return target object instance
     */
    default <T> T deserialize(byte[] data, String classFullName) {
        try {
            Class<?> cls;
            CLASS_CACHE.computeIfAbsent(classFullName, name -> {
                try {
                    return Class.forName(classFullName);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
            cls = CLASS_CACHE.get(classFullName);
            return (T) deserialize(data, cls);
        } catch (Exception ignore) {
            return null;
        }
    }
    
    /**
     * Serialize the object.
     *
     * @param obj target obj
     * @return byte[]
     */
    <T> byte[] serialize(T obj);
    
    /**
     * The name of the serializer implementer.
     *
     * @return name
     */
    String name();
    
}
