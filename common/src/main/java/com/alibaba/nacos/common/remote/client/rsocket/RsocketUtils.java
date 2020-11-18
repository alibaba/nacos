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

package com.alibaba.nacos.common.remote.client.rsocket;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.remote.PayloadRegistry;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

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
    
    /**
     * convert request to palyload.
     *
     * @param request request.
     * @param meta    request meta.
     * @return payload of rsocket
     */
    public static Payload convertRequestToPayload(Request request, RequestMeta meta) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("clientPort", meta.getClientPort());
        jsonObject.addProperty("connectionId", meta.getConnectionId());
        jsonObject.addProperty("clientIp", meta.getClientIp());
        jsonObject.addProperty("clientVersion", meta.getClientVersion());
        jsonObject.addProperty("labels", toJson(meta.getLabels()));
        jsonObject.addProperty("headers", toJson(request.getHeaders()));
        jsonObject.addProperty("type", request.getClass().getName());
        request.clearHeaders();
        return DefaultPayload.create(toJson(request).getBytes(), jsonObject.toString().getBytes());
        
    }
    
    /**
     * convert response to palyload.
     *
     * @param response response.
     * @return payload.
     */
    public static Payload convertResponseToPayload(Response response) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("type", response.getClass().getName());
        return DefaultPayload.create(toJson(response).getBytes(), jsonObject.toString().getBytes());
    }
    
    /**
     * parse response from payload.
     *
     * @param payload payload.
     * @return response.
     */
    public static Response parseResponseFromPayload(Payload payload) {
        //parse meta
        String metaString = payload.getMetadataUtf8();
        JsonNode metaJsonNode = toObj(metaString);
        String type = metaJsonNode.get("type").textValue();
    
        String bodyString = getPayloadString(payload);
        Class classbyType = PayloadRegistry.getClassbyType(type);
        PlainRequest plainRequest = new PlainRequest();
        plainRequest.setType(type);
        Response response = (Response) toObj(bodyString, classbyType);
        return response;
    }
    
    /**
     * parse plain request type from payload.
     *
     * @param payload payload.
     * @return plain request.
     */
    public static PlainRequest parsePlainRequestFromPayload(Payload payload) {
        //parse meta
        String metaString = payload.getMetadataUtf8();
        JsonNode metaJsonNode = toObj(metaString);
        String type = metaJsonNode.get("type").textValue();
        Map<String, String> labels = (Map<String, String>) toObj(metaJsonNode.get("labels").textValue(), Map.class);
        RequestMeta requestMeta = new RequestMeta();
    
        requestMeta.setClientVersion(
                metaJsonNode.has("clientVersion") ? metaJsonNode.get("clientVersion").textValue() : "");
        requestMeta
                .setConnectionId(metaJsonNode.has("connectionId") ? metaJsonNode.get("connectionId").textValue() : "");
        requestMeta.setClientPort(metaJsonNode.has("clientPort") ? metaJsonNode.get("clientPort").intValue() : 0);
        requestMeta.setClientIp(metaJsonNode.has("clientIp") ? metaJsonNode.get("clientIp").textValue() : "");
        requestMeta.setLabels(labels);
    
        String bodyString = getPayloadString(payload);
        Class classbyType = PayloadRegistry.getClassbyType(type);
        PlainRequest plainRequest = new PlainRequest();
        plainRequest.setType(type);
        Request request = (Request) toObj(bodyString, classbyType);
        Map<String, String> headers = (Map<String, String>) toObj(metaJsonNode.get("headers").textValue(), Map.class);
        request.putAllHeader(headers);
        plainRequest.setBody(request);
    
        plainRequest.setMetadata(requestMeta);
        return plainRequest;
    
    }
    
    private static String getPayloadString(Payload payload) {
        ByteBuffer data1 = payload.getData();
        byte[] data = new byte[data1.remaining()];
        payload.data().readBytes(data);
        byte[] bytes = new byte[0];
        return new String(bytes);
    }
    
    public static class PlainRequest {
        
        String type;
    
        Request body;
    
        RequestMeta metadata;
    
        @Override
        public String toString() {
            return "PlainRequest{" + "type='" + type + '\'' + ", body=" + body + ", metadata=" + metadata + '}';
        }
    
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
        public Request getBody() {
            return body;
        }
        
        /**
         * Setter method for property <tt>body</tt>.
         *
         * @param body value to be assigned to property body
         */
        public void setBody(Request body) {
            this.body = body;
        }
    }
}
