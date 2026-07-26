/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils.json;

import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Neutral JSON adapter SPI for concrete JSON providers.
 *
 * @author nacos
 */
public interface NacosJsonAdapter {
    
    /**
     * Return adapter name.
     *
     * @return adapter name
     */
    String name();
    
    /**
     * Check whether this adapter can be used in the current runtime.
     *
     * @return {@code true} if adapter is available
     */
    boolean isAvailable();
    
    /**
     * Serialize object to JSON string.
     *
     * @param obj object
     * @return JSON string
     */
    String toJson(Object obj);
    
    /**
     * Serialize object to JSON byte array.
     *
     * @param obj object
     * @return JSON byte array
     */
    byte[] toJsonBytes(Object obj);
    
    /**
     * Serialize object to canonical JSON string.
     *
     * @param obj object
     * @return canonical JSON string
     */
    String toCanonicalJson(Object obj);
    
    /**
     * Deserialize byte array to object.
     *
     * @param json JSON byte array
     * @param cls target class
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(byte[] json, Class<T> cls);
    
    /**
     * Deserialize byte array to object.
     *
     * @param json JSON byte array
     * @param type target type
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(byte[] json, Type type);
    
    /**
     * Deserialize byte array to object.
     *
     * @param json JSON byte array
     * @param typeReference target type reference
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(byte[] json, NacosTypeReference<T> typeReference);
    
    /**
     * Deserialize JSON string to object.
     *
     * @param json JSON string
     * @param cls target class
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(String json, Class<T> cls);
    
    /**
     * Deserialize JSON string to object.
     *
     * @param json JSON string
     * @param type target type
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(String json, Type type);
    
    /**
     * Deserialize JSON string to object.
     *
     * @param json JSON string
     * @param typeReference target type reference
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(String json, NacosTypeReference<T> typeReference);
    
    /**
     * Deserialize input stream to object.
     *
     * @param inputStream JSON input stream
     * @param cls target class
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(InputStream inputStream, Class<T> cls);
    
    /**
     * Deserialize input stream to object.
     *
     * @param inputStream JSON input stream
     * @param type target type
     * @param <T> target type
     * @return target object
     */
    <T> T toObj(InputStream inputStream, Type type);
    
    /**
     * Register subtype metadata.
     *
     * @param subtype subtype registration
     */
    void registerSubtype(NacosJsonSubtype subtype);
}
