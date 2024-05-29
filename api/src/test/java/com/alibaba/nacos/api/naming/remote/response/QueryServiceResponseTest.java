/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.remote.response;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryServiceResponseTest {
    
    protected static ObjectMapper mapper;
    
    @BeforeAll
    static void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    void testSerializeSuccessResponse() throws JsonProcessingException {
        QueryServiceResponse response = QueryServiceResponse.buildSuccessResponse(new ServiceInfo());
        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"serviceInfo\":{"));
        assertTrue(json.contains("\"resultCode\":200"));
        assertTrue(json.contains("\"errorCode\":0"));
        assertTrue(json.contains("\"success\":true"));
    }
    
    @Test
    void testSerializeFailResponse() throws JsonProcessingException {
        QueryServiceResponse response = QueryServiceResponse.buildFailResponse("test");
        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"resultCode\":500"));
        assertTrue(json.contains("\"errorCode\":0"));
        assertTrue(json.contains("\"message\":\"test\""));
        assertTrue(json.contains("\"success\":false"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"serviceInfo\":{\"cacheMillis\":1000,\"hosts\":[],"
                + "\"lastRefTime\":0,\"checksum\":\"\",\"allIPs\":false,\"reachProtectionThreshold\":false,"
                + "\"valid\":true},\"success\":true}";
        QueryServiceResponse response = mapper.readValue(json, QueryServiceResponse.class);
        assertNotNull(response.getServiceInfo());
    }
    
}