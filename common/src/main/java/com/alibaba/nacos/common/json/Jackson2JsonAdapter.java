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

package com.alibaba.nacos.common.json;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import com.alibaba.nacos.api.utils.json.NacosJsonSubtype;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Jackson 2 implementation of {@link NacosJsonAdapter}.
 *
 * @author nacos
 */
public class Jackson2JsonAdapter implements NacosJsonAdapter {
    
    private final ObjectMapper mapper = createObjectMapper();
    
    private final ObjectMapper canonicalMapper = createCanonicalObjectMapper();
    
    @Override
    public String name() {
        return NacosJsonAdapterNames.JACKSON2;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public String toJson(Object obj) {
        return write(obj, () -> mapper.writeValueAsString(obj));
    }
    
    @Override
    public byte[] toJsonBytes(Object obj) {
        return write(obj, () -> mapper.writeValueAsBytes(obj));
    }
    
    @Override
    public String toCanonicalJson(Object obj) {
        return write(obj, () -> canonicalMapper.writeValueAsString(obj));
    }
    
    @Override
    public <T> T toObj(byte[] json, Class<T> cls) {
        return read(() -> mapper.readValue(json, cls), cls);
    }
    
    @Override
    public <T> T toObj(byte[] json, Type type) {
        return read(() -> mapper.readValue(json, constructJavaType(type)), type);
    }
    
    @Override
    public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
        return toObj(json, typeReference.getType());
    }
    
    @Override
    public <T> T toObj(String json, Class<T> cls) {
        return read(() -> mapper.readValue(json, cls), cls);
    }
    
    @Override
    public <T> T toObj(String json, Type type) {
        return read(() -> mapper.readValue(json, constructJavaType(type)), type);
    }
    
    @Override
    public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
        return toObj(json, typeReference.getType());
    }
    
    @Override
    public <T> T toObj(InputStream inputStream, Class<T> cls) {
        return read(() -> mapper.readValue(inputStream, cls), cls);
    }
    
    @Override
    public <T> T toObj(InputStream inputStream, Type type) {
        return read(() -> mapper.readValue(inputStream, constructJavaType(type)), type);
    }
    
    @Override
    public void registerSubtype(NacosJsonSubtype subtype) {
        NamedType namedType = new NamedType(subtype.getSubtype(), subtype.getTypeName());
        mapper.registerSubtypes(namedType);
        canonicalMapper.registerSubtypes(namedType);
    }
    
    private JavaType constructJavaType(Type type) {
        return mapper.constructType(type);
    }
    
    private <T> T write(Object obj, JsonWriter<T> writer) {
        try {
            return writer.write();
        } catch (JsonProcessingException e) {
            Class<?> serializedClass = obj == null ? Object.class : obj.getClass();
            throw new NacosSerializationException(serializedClass, e);
        }
    }
    
    private <T> T read(JsonReader<T> reader, Class<?> cls) {
        try {
            return reader.read();
        } catch (Exception e) {
            throw new NacosDeserializationException(cls, e);
        }
    }
    
    private <T> T read(JsonReader<T> reader, Type type) {
        try {
            return reader.read();
        } catch (Exception e) {
            throw new NacosDeserializationException(type, e);
        }
    }
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        return objectMapper;
    }
    
    private static ObjectMapper createCanonicalObjectMapper() {
        ObjectMapper objectMapper = createObjectMapper();
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return objectMapper;
    }
    
    private interface JsonWriter<T> {
        
        T write() throws JsonProcessingException;
    }
    
    private interface JsonReader<T> {
        
        T read() throws IOException;
    }
}
