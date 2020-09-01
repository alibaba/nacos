/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.grpc;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.PayloadRegistry;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * grpc utils, use to parse request and response.
 *
 * @author liuzunfei
 * @version $Id: GrpcUtils.java, v 0.1 2020年08月09日 1:43 PM liuzunfei Exp $
 */
public class GrpcUtils {
    
    static ObjectMapper mapper = new ObjectMapper();
    
    static {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    /**
     * Object to json string.
     *
     * @param obj obj
     * @return json string
     * @throws NacosSerializationException if transfer failed
     */
    private static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new NacosSerializationException(obj.getClass(), e);
        }
    }
    
    /**
     * Json string deserialize to Object.
     *
     * @param json json string
     * @param cls  class of object
     * @param <T>  General type
     * @return object
     * @throws NacosDeserializationException if deserialize failed
     */
    public static <T> T toObj(String json, Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (IOException e) {
            throw new NacosDeserializationException(cls, e);
        }
    }
    
    public static Payload convert(Request request, RequestMeta meta) {
        String jsonString = toJson(request);
        byte[] bytes = null;
        try {
            bytes = jsonString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        Payload.Builder builder = Payload.newBuilder();
        if (meta != null) {
            Metadata metadata = Metadata.newBuilder().setClientIp(meta.getClientIp())
                    .setVersion(meta.getClientVersion()).build();
            builder.setMetadata(metadata);
        }
        Payload payload = builder.setType(request.getClass().getName())
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return payload;
        
    }
    
    public static Payload convert(Request request, Metadata meta) {
        String jsonString = toJson(request);
        byte[] bytes = null;
        try {
            bytes = jsonString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        Payload.Builder builder = Payload.newBuilder();
        if (meta != null) {
            builder.setMetadata(meta);
        }
        Payload payload = builder.setType(request.getClass().getName())
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return payload;
        
    }
    
    public static Payload convert(Response response) {
        String jsonString = toJson(response);
        byte[] bytes = null;
        try {
            bytes = jsonString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        Payload payload = Payload.newBuilder().setType(response.getClass().getName())
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return payload;
    }
    
    /**
     * parse payload to request/response model.
     * @param payload
     * @return
     */
    public static Object parse(Payload payload) {
        Class classbyType = PayloadRegistry.getClassbyType(payload.getType());
        if (classbyType != null) {
            Object obj = toObj(payload.getBody().getValue().toStringUtf8(), classbyType);
            return obj;
        }
        return null;
    }
    
}
