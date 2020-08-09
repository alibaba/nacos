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
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerPushRequest;
import com.alibaba.nacos.api.remote.request.ServerRequestRegistry;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.IOException;

/**
 * grpc utils, use to parse request and response.
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
     * parse response from grpc response.
     *
     * @param grpcResponse grpcResponse.
     * @return response. null if parse fail.
     */
    public static Response parseResponsefromGrpcResponse(GrpcResponse grpcResponse) {
        if (grpcResponse == null) {
            return null;
        }
        String type = grpcResponse.getType();
        String bodyString = grpcResponse.getBody().getValue().toStringUtf8();
        
        // transfrom grpcResponse to response model
        Class classByType = ResponseRegistry.getClassByType(type);
        if (classByType != null) {
            Object object = toObj(bodyString, classByType);
            return (Response) object;
        }
        return null;
    }
    
    /**
     * parse request from grpc response.
     *
     * @param grpcResponse grpc response from grpc server.
     * @return request object parse from grpc response
     */
    public static ServerPushRequest parseRequestFromGrpcResponse(GrpcResponse grpcResponse) {
        if (grpcResponse == null) {
            return null;
        }
        String message = grpcResponse.getBody().getValue().toStringUtf8();
        String type = grpcResponse.getType();
        String bodyString = grpcResponse.getBody().getValue().toStringUtf8();
        Class classByType = ServerRequestRegistry.getClassByType(type);
        ServerPushRequest request = null;
        if (classByType != null) {
            request = (ServerPushRequest) toObj(bodyString, classByType);
        }
        return request;
    }
    
    /**
     * convert to grpc request with grpc request and request meta.
     *
     * @param request request.
     * @param meta    grpc request meta.
     * @return
     */
    public static GrpcRequest convertToGrpcRequest(Request request, GrpcMetadata meta) {
        GrpcRequest grpcRequest = GrpcRequest.newBuilder().setMetadata(meta).setType(request.getType())
                .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(toJson(request))).build()).build();
        return grpcRequest;
    }
    
    /**
     * convert to grpc request with grpc request and request meta.
     *
     * @param grpcRequest grpcRequest.
     * @param clazz       request class.
     * @return RequestInfo. include request and meta.
     */
    public static RequestInfo parseRequestFromGrpcRequest(GrpcRequest grpcRequest, Class clazz) {
        RequestInfo requestInfo = new RequestInfo();
        Request request = (Request) toObj(grpcRequest.getBody().getValue().toStringUtf8(), clazz);
        requestInfo.setRequestMeta(convertMeta(grpcRequest.getMetadata()));
        return requestInfo;
    }
    
    private static RequestMeta convertMeta(GrpcMetadata metadata) {
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp(metadata.getClientIp());
        requestMeta.setConnectionId(metadata.getConnectionId());
        requestMeta.setClientVersion(metadata.getVersion());
        return requestMeta;
    }
    
    public static class RequestInfo {
        
        Request request;
        
        RequestMeta requestMeta;
        
        /**
         * Getter method for property <tt>request</tt>.
         *
         * @return property value of request
         */
        public Request getRequest() {
            return request;
        }
        
        /**
         * Setter method for property <tt>request</tt>.
         *
         * @param request value to be assigned to property request
         */
        public void setRequest(Request request) {
            this.request = request;
        }
        
        /**
         * Getter method for property <tt>requestMeta</tt>.
         *
         * @return property value of requestMeta
         */
        public RequestMeta getRequestMeta() {
            return requestMeta;
        }
        
        /**
         * Setter method for property <tt>requestMeta</tt>.
         *
         * @param requestMeta value to be assigned to property requestMeta
         */
        public void setRequestMeta(RequestMeta requestMeta) {
            this.requestMeta = requestMeta;
        }
    }
    
}
