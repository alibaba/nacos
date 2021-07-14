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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.PayloadRegistry;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.exception.RemoteException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * gRPC utils, use to parse request and response.
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
     * @return payload.
     */
    public static Payload convert(Request request, RequestMeta meta) {
        //meta.
        Payload.Builder payloadBuilder = Payload.newBuilder();
        Metadata.Builder metaBuilder = Metadata.newBuilder();
        if (meta != null) {
            metaBuilder.putAllHeaders(request.getHeaders()).setType(request.getClass().getSimpleName());
        }
        metaBuilder.setClientIp(NetUtils.localIP());
        payloadBuilder.setMetadata(metaBuilder.build());
        
        // request body .
        request.clearHeaders();
        String jsonString = toJson(request);
        return payloadBuilder
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(jsonString, Charset.forName(Constants.ENCODE))))
                .build();
        
    }
    
    /**
     * convert request to payload.
     *
     * @param request request.
     * @return payload.
     */
    public static Payload convert(Request request) {
        
        Metadata newMeta = Metadata.newBuilder().setType(request.getClass().getSimpleName())
                .setClientIp(NetUtils.localIP()).putAllHeaders(request.getHeaders()).build();
        request.clearHeaders();
        String jsonString = toJson(request);
        
        Payload.Builder builder = Payload.newBuilder();
    
        return builder
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(jsonString, Charset.forName(Constants.ENCODE))))
                .setMetadata(newMeta).build();
        
    }
    
    /**
     * convert response to payload.
     *
     * @param response response.
     * @return payload.
     */
    public static Payload convert(Response response) {
        String jsonString = toJson(response);
        
        Metadata.Builder metaBuilder = Metadata.newBuilder().setType(response.getClass().getSimpleName());
        return Payload.newBuilder()
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(jsonString, Charset.forName(Constants.ENCODE))))
                .setMetadata(metaBuilder.build()).build();
    }
    
    /**
     * parse payload to request/response model.
     *
     * @param payload payload to be parsed.
     * @return payload
     */
    public static Object parse(Payload payload) {
        Class classType = PayloadRegistry.getClassByType(payload.getMetadata().getType());
        if (classType != null) {
            Object obj = toObj(payload.getBody().getValue().toString(Charset.forName(Constants.ENCODE)), classType);
            if (obj instanceof Request) {
                ((Request) obj).putAllHeader(payload.getMetadata().getHeadersMap());
            }
            return obj;
        } else {
            throw new RemoteException(NacosException.SERVER_ERROR,
                    "Unknown payload type:" + payload.getMetadata().getType());
        }
        
    }
    
    public static class PlainRequest {
        
        String type;
        
        Object body;
        
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
