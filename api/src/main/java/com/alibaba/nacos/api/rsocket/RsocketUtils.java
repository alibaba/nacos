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

package com.alibaba.nacos.api.rsocket;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerRequestRegistry;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;

import java.io.IOException;

/**
 * rsocket utils.
 *
 * @author liuzunfei
 * @version $Id: RsocketUtils.java, v 0.1 2020年08月06日 2:25 PM liuzunfei Exp $
 */
public class RsocketUtils {
    
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
     * Json string deserialize to Jackson {@link JsonNode}.
     *
     * @param json json string
     * @return {@link JsonNode}
     * @throws NacosDeserializationException if deserialize failed
     */
    public static JsonNode toObj(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new NacosDeserializationException(e);
        }
    }
    
    public static Payload convertRequestToPayload(Request request, RequestMeta meta) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("type", request.getType());
        jsonObject.addProperty("body", toJson(request));
        jsonObject.addProperty("meta", toJson(meta));
        return DefaultPayload.create(jsonObject.toString());
    }
    
    public static Payload convertResponseToPayload(Response response) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("type", response.getType());
        jsonObject.addProperty("body", toJson(response));
        return DefaultPayload.create(jsonObject.toString());
    }
    
    public static Response parseResponseFromPayload(Payload payload) {
        String message = payload.getDataUtf8();
        JsonNode jsonNode = toObj(message);
        String type = jsonNode.get("type").textValue();
        String bodyString = jsonNode.get("body").textValue();
        Class classByType = ResponseRegistry.getClassByType(type);
        final Response response;
        if (classByType != null) {
            response = (Response) toObj(bodyString, classByType);
        } else {
            PlainBodyResponse myresponse = toObj(bodyString, PlainBodyResponse.class);
            myresponse.setBodyString(bodyString);
            response = myresponse;
        }
        return response;
    }
    
    public static Request parseServerRequestFromPayload(Payload payload) {
        // {"type":"type","body":"bodyString"}
        String message = payload.getDataUtf8();
        JsonNode jsonNode = toObj(message);
        String type = jsonNode.get("type").textValue();
        String bodyString = jsonNode.get("body").textValue();
        Class classByType = ServerRequestRegistry.getClassByType(type);
        final Request request;
        if (classByType != null) {
            request = (Request) toObj(bodyString, classByType);
            return request;
        } else {
            return null;
        }
    }
    
    /**
     * parse request type from payload.
     *
     * @param payload
     * @return
     */
    public static PlainRequest parsePlainRequestFromPayload(Payload payload) {
        // {"type":"type","body":"bodyString"}
        String message = payload.getDataUtf8();
        JsonNode jsonNode = toObj(message);
        String type = jsonNode.has("type") ? jsonNode.get("type").textValue() : "";
        String bodyString = jsonNode.has("body") ? jsonNode.get("body").textValue() : "";
        String meta = jsonNode.has("meta") ? jsonNode.get("meta").textValue() : "";
        PlainRequest plainRequest = new PlainRequest();
        plainRequest.setType(type);
        plainRequest.setBody(bodyString);
        plainRequest.setMeta(meta);
        return plainRequest;
    }
    
    public static class PlainRequest {
        
        String type;
        
        String body;
    
        String meta;
    
        /**
         * Getter method for property <tt>meta</tt>.
         *
         * @return property value of meta
         */
        public String getMeta() {
            return meta;
        }
    
        /**
         * Setter method for property <tt>meta</tt>.
         *
         * @param meta value to be assigned to property meta
         */
        public void setMeta(String meta) {
            this.meta = meta;
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
        public String getBody() {
            return body;
        }
        
        /**
         * Setter method for property <tt>body</tt>.
         *
         * @param body value to be assigned to property body
         */
        public void setBody(String body) {
            this.body = body;
        }
    }
}
