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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Jackson 3 implementation of {@link NacosJsonAdapter}.
 *
 * @author nacos
 */
class Jackson3JsonAdapterDelegate implements NacosJsonAdapter {
    
    private final List<NacosJsonSubtype> subtypes = new ArrayList<NacosJsonSubtype>();
    
    private volatile ObjectMapper mapper = createObjectMapper(subtypes);
    
    private volatile ObjectMapper canonicalMapper = createCanonicalObjectMapper(subtypes);
    
    @Override
    public String name() {
        return NacosJsonAdapterNames.JACKSON3;
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
        synchronized (subtypes) {
            if (!subtypes.contains(subtype)) {
                subtypes.add(subtype);
            }
            mapper = createObjectMapper(subtypes);
            canonicalMapper = createCanonicalObjectMapper(subtypes);
        }
    }
    
    private JavaType constructJavaType(Type type) {
        return mapper.constructType(type);
    }
    
    private <T> T write(Object obj, JsonWriter<T> writer) {
        try {
            return writer.write();
        } catch (JacksonException e) {
            Class<?> serializedClass = obj == null ? Object.class : obj.getClass();
            throw new NacosSerializationException(serializedClass, e);
        }
    }
    
    private <T> T read(JsonReader<T> reader, Class<?> cls) {
        try {
            return reader.read();
        } catch (JacksonException e) {
            throw new NacosDeserializationException(cls, e);
        }
    }
    
    private <T> T read(JsonReader<T> reader, Type type) {
        try {
            return reader.read();
        } catch (JacksonException e) {
            throw new NacosDeserializationException(type, e);
        }
    }
    
    private static ObjectMapper createObjectMapper(Collection<NacosJsonSubtype> subtypes) {
        JsonMapper.Builder builder = JsonMapper.builder();
        builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        builder.changeDefaultPropertyInclusion(new NonNullPropertyInclusion());
        registerSubtypes(builder, subtypes);
        return builder.build();
    }
    
    private static ObjectMapper createCanonicalObjectMapper(Collection<NacosJsonSubtype> subtypes) {
        JsonMapper.Builder builder = JsonMapper.builder();
        builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        builder.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        builder.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        builder.changeDefaultPropertyInclusion(new NonNullPropertyInclusion());
        registerSubtypes(builder, subtypes);
        return builder.build();
    }
    
    private static void registerSubtypes(JsonMapper.Builder builder,
        Collection<NacosJsonSubtype> subtypes) {
        for (NacosJsonSubtype subtype : subtypes) {
            builder.registerSubtypes(new NamedType(subtype.getSubtype(), subtype.getTypeName()));
        }
    }
    
    private interface JsonWriter<T> {
        
        T write() throws JacksonException;
    }
    
    private interface JsonReader<T> {
        
        T read() throws JacksonException;
    }
    
    private static class NonNullPropertyInclusion implements UnaryOperator<JsonInclude.Value> {
        
        @Override
        public JsonInclude.Value apply(JsonInclude.Value value) {
            return JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL);
        }
    }
}
