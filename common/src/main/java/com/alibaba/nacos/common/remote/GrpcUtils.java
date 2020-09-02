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

package com.alibaba.nacos.common.remote;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.PayloadRegistry;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    
    /**
     * convert request to payload.
     *
     * @param request request.
     * @param meta    request meta.
     * @return
     */
    public static Payload convert(Request request, RequestMeta meta) {
        String jsonString = toJson(request);
        byte[] bytes = null;
        try {
            bytes = IoUtils.tryCompress(jsonString, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        
        Payload.Builder builder = Payload.newBuilder();
        Metadata.Builder metaBuilder = Metadata.newBuilder();
        if (meta != null) {
            metaBuilder.setClientIp(meta.getClientIp()).putAllLabels(meta.getLabels())
                    .putAllHeaders(request.getHeaders()).setClientVersion(meta.getClientVersion())
                    .setType(request.getClass().getName()).build();
        }
        builder.setMetadata(metaBuilder.build());
        Payload payload = builder.setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return payload;
        
    }
    
    /**
     * convert request to payload.
     *
     * @param request request.
     * @param meta    meta
     * @return
     */
    public static Payload convert(Request request, Metadata meta) {
        String jsonString = toJson(request);
        byte[] bytes = null;
        try {
            bytes = IoUtils.tryCompress(jsonString, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        Payload.Builder builder = Payload.newBuilder();
        Payload payload = builder.setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).setMetadata(meta)
                .build();
        return payload;
        
    }
    
    /**
     * convert response to payload.
     *
     * @param response response.
     * @return
     */
    public static Payload convert(Response response) {
        String jsonString = toJson(response);
        byte[] bytes = null;
        try {
            bytes = IoUtils.tryCompress(jsonString, StandardCharsets.UTF_8.name());
            ;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        Metadata.Builder metaBuilder = Metadata.newBuilder();
        metaBuilder.setClientVersion(VersionUtils.getFullClientVersion()).setType(response.getClass().getName());
    
        Payload payload = Payload.newBuilder().setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes)))
                .setMetadata(metaBuilder.build()).build();
        return payload;
    }
    
    /**
     * parse payload to request/response model.
     *
     * @param payload payload to be parsed.
     * @return
     */
    public static Object parse(Payload payload) {
        Class classbyType = PayloadRegistry.getClassbyType(payload.getMetadata().getType());
        if (classbyType != null) {
            byte[] value = new byte[0];
            try {
                value = IoUtils.tryDecompress(payload.getBody().getValue().toByteArray());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            Object obj = toObj(ByteString.copyFrom(value).toStringUtf8(), classbyType);
            return obj;
        }
        return null;
    }
    
}
