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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Neutral JSON utility facade for Nacos Java SDK code.
 *
 * @author nacos
 */
public final class JsonUtils {
    
    /**
     * System property for selecting JSON adapter.
     */
    public static final String ADAPTER_PROPERTY_NAME = JsonAdapterSelector.ADAPTER_PROPERTY_NAME;
    
    private static final List<NacosJsonSubtype> SUBTYPES = new ArrayList<NacosJsonSubtype>();
    
    private static final Set<String> REPLAYED_SUBTYPES = new HashSet<String>();
    
    private static volatile JsonAdapterSelector selector = new JsonAdapterSelector();
    
    private JsonUtils() {
    }
    
    /**
     * Preload and initialize selected JSON adapter.
     */
    public static void preload() {
        adapter();
    }
    
    /**
     * Return selected JSON adapter name.
     *
     * @return selected adapter name
     */
    public static String selectedAdapterName() {
        return adapter().name();
    }
    
    /**
     * Object to JSON string.
     *
     * @param obj object
     * @return JSON string
     */
    public static String toJson(Object obj) {
        return adapter().toJson(obj);
    }
    
    /**
     * Object to JSON bytes.
     *
     * @param obj object
     * @return JSON bytes
     */
    public static byte[] toJsonBytes(Object obj) {
        return adapter().toJsonBytes(obj);
    }
    
    /**
     * Object to canonical JSON string.
     *
     * @param obj object
     * @return canonical JSON string
     */
    public static String toCanonicalJson(Object obj) {
        return adapter().toCanonicalJson(obj);
    }
    
    /**
     * JSON bytes deserialize to object.
     *
     * @param json JSON bytes
     * @param cls target class
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(byte[] json, Class<T> cls) {
        return adapter().toObj(json, cls);
    }
    
    /**
     * JSON bytes deserialize to object.
     *
     * @param json JSON bytes
     * @param type target type
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(byte[] json, Type type) {
        return adapter().toObj(json, type);
    }
    
    /**
     * JSON bytes deserialize to object.
     *
     * @param json JSON bytes
     * @param typeReference target type reference
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
        return adapter().toObj(json, typeReference);
    }
    
    /**
     * JSON string deserialize to object.
     *
     * @param json JSON string
     * @param cls target class
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(String json, Class<T> cls) {
        return adapter().toObj(json, cls);
    }
    
    /**
     * JSON string deserialize to object.
     *
     * @param json JSON string
     * @param type target type
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(String json, Type type) {
        return adapter().toObj(json, type);
    }
    
    /**
     * JSON string deserialize to object.
     *
     * @param json JSON string
     * @param typeReference target type reference
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(String json, NacosTypeReference<T> typeReference) {
        return adapter().toObj(json, typeReference);
    }
    
    /**
     * JSON input stream deserialize to object.
     *
     * @param inputStream JSON input stream
     * @param cls target class
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(InputStream inputStream, Class<T> cls) {
        return adapter().toObj(inputStream, cls);
    }
    
    /**
     * JSON input stream deserialize to object.
     *
     * @param inputStream JSON input stream
     * @param type target type
     * @param <T> target type
     * @return object
     */
    public static <T> T toObj(InputStream inputStream, Type type) {
        return adapter().toObj(inputStream, type);
    }
    
    /**
     * Register subtype without an explicit base type.
     *
     * @param subtype subtype class
     * @param typeName wire type name
     */
    public static void registerSubtype(Class<?> subtype, String typeName) {
        registerSubtype(Object.class, subtype, typeName);
    }
    
    /**
     * Register subtype with an explicit base type.
     *
     * @param baseType base type
     * @param subtype subtype class
     * @param typeName wire type name
     */
    public static void registerSubtype(Class<?> baseType, Class<?> subtype, String typeName) {
        NacosJsonSubtype registration = new NacosJsonSubtype(baseType, subtype, typeName);
        synchronized (SUBTYPES) {
            if (!SUBTYPES.contains(registration)) {
                SUBTYPES.add(registration);
            }
        }
        NacosJsonAdapter adapter = selector.getSelectedAdapter();
        if (adapter != null) {
            replaySubtypes(adapter);
        }
    }
    
    private static NacosJsonAdapter adapter() {
        NacosJsonAdapter adapter = selector.select();
        replaySubtypes(adapter);
        return adapter;
    }
    
    private static void replaySubtypes(NacosJsonAdapter adapter) {
        synchronized (SUBTYPES) {
            for (NacosJsonSubtype subtype : SUBTYPES) {
                String replayKey = adapter.name() + ":" + subtype.hashCode();
                if (REPLAYED_SUBTYPES.add(replayKey)) {
                    adapter.registerSubtype(subtype);
                }
            }
        }
    }
    
    static void setAdapterSelectorForTest(JsonAdapterSelector selectorForTest) {
        selector = selectorForTest;
        synchronized (SUBTYPES) {
            REPLAYED_SUBTYPES.clear();
        }
    }
    
    static void resetForTest() {
        selector = new JsonAdapterSelector();
        synchronized (SUBTYPES) {
            SUBTYPES.clear();
            REPLAYED_SUBTYPES.clear();
        }
    }
}
