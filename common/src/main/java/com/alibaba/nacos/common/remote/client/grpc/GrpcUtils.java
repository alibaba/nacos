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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.PayloadRegistry;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.IOException;

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
        //meta.
        Payload.Builder builder = Payload.newBuilder();
        Metadata.Builder metaBuilder = Metadata.newBuilder();
        if (meta != null) {
            metaBuilder.setClientIp(meta.getClientIp()).setClientPort(meta.getClientPort())
                    .setConnectionId(meta.getConnectionId()).putAllLabels(meta.getLabels())
                    .putAllHeaders(request.getHeaders()).setClientVersion(meta.getClientVersion())
                    .setType(request.getClass().getName());
        }
        builder.setMetadata(metaBuilder.build());
    
        // request body .
        request.clearHeaders();
        String jsonString = toJson(request);
    
        Payload payload = builder.setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(jsonString))).build();
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
    
        Metadata buildMeta = meta.toBuilder().putAllHeaders(request.getHeaders()).build();
        request.clearHeaders();
        String jsonString = toJson(request);
    
        Payload.Builder builder = Payload.newBuilder();
        Payload payload = builder.setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(jsonString)))
                .setMetadata(buildMeta)
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
    
        Metadata.Builder metaBuilder = Metadata.newBuilder();
        metaBuilder.setClientVersion(VersionUtils.getFullClientVersion()).setType(response.getClass().getName());
    
        Payload payload = Payload.newBuilder().setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(jsonString)))
                .setMetadata(metaBuilder.build()).build();
        return payload;
    }
    
    /**
     * parse payload to request/response model.
     *
     * @param payload payload to be parsed.
     * @return
     */
    public static PlainRequest parse(Payload payload) {
        PlainRequest plainRequest = new PlainRequest();
        Class classbyType = PayloadRegistry.getClassbyType(payload.getMetadata().getType());
        if (classbyType != null) {
            Object obj = toObj(payload.getBody().getValue().toStringUtf8(), classbyType);
            if (obj instanceof Request) {
                ((Request) obj).putAllHeader(payload.getMetadata().getHeadersMap());
            }
            plainRequest.body = obj;
        }
    
        plainRequest.type = payload.getMetadata().getType();
        plainRequest.metadata = convertMeta(payload.getMetadata());
        return plainRequest;
    }
    
    private static RequestMeta convertMeta(Metadata metadata) {
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp(metadata.getClientIp());
        requestMeta.setClientPort(metadata.getClientPort());
        requestMeta.setConnectionId(metadata.getConnectionId());
        requestMeta.setClientVersion(metadata.getClientVersion());
        requestMeta.setLabels(metadata.getLabelsMap());
        return requestMeta;
    }
    
    public static class PlainRequest {
        
        String type;
        
        Object body;
        
        RequestMeta metadata;
        
        /**
         * Getter method for property <tt>metadata</tt>.
         *
         * @return property value of metadata
         */
        public RequestMeta getMetadata() {
            return metadata;
        }
        
        /**
         * Setter method for property <tt>metadata</tt>.
         *
         * @param metadata value to be assigned to property metadata
         */
        public void setMetadata(RequestMeta metadata) {
            this.metadata = metadata;
        }
        
        /**
         * Getter method for property <tt>type</tt>.
         *
         * @return property value of type
         */
        public String getType() {
            return type;
        }
        
        /**
         * Setter method for property <tt>type</tt>.
         *
         * @param type value to be assigned to property type
         */
        public void setType(String type) {
            this.type = type;
        }
        
        /**
         * Getter method for property <tt>body</tt>.
         *
         * @return property value of body
         */
        public Object getBody() {
            return body;
        }
        
        /**
         * Setter method for property <tt>body</tt>.
         *
         * @param body value to be assigned to property body
         */
        public void setBody(Object body) {
            this.body = body;
        }
    }
    
}
