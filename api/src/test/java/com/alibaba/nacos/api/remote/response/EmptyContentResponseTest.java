/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyContentResponseTest {
    
    private static final String COMMON_JSON = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"1\",\"success\":true}";
    
    private static final String TO_STRING = "Response{resultCode=200, errorCode=0, message='null', requestId='1'}";
    
    ObjectMapper mapper = new ObjectMapper();
    
    @BeforeEach
    void setUp() throws Exception {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    void testSetErrorInfo() {
        Response response = new Response() {
        };
        response.setErrorInfo(ResponseCode.FAIL.getCode(), ResponseCode.FAIL.getDesc());
        assertEquals(ResponseCode.FAIL.getCode(), response.getErrorCode());
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(ResponseCode.FAIL.getDesc(), response.getMessage());
    }
    
    @Test
    void testClientDetectionResponse() throws JsonProcessingException {
        ClientDetectionResponse response = new ClientDetectionResponse();
        response.setRequestId("1");
        String actual = mapper.writeValueAsString(response);
        assertCommonResponseJson(actual);
        response = mapper.readValue(COMMON_JSON, ClientDetectionResponse.class);
        assertCommonResponse(response);
    }
    
    @Test
    void testConnectResetResponse() throws JsonProcessingException {
        ConnectResetResponse response = new ConnectResetResponse();
        response.setRequestId("1");
        String actual = mapper.writeValueAsString(response);
        assertCommonResponseJson(actual);
        response = mapper.readValue(COMMON_JSON, ConnectResetResponse.class);
        assertCommonResponse(response);
    }
    
    @Test
    void testHealthCheckResponse() throws JsonProcessingException {
        HealthCheckResponse response = new HealthCheckResponse();
        response.setRequestId("1");
        String actual = mapper.writeValueAsString(response);
        assertCommonResponseJson(actual);
        response = mapper.readValue(COMMON_JSON, HealthCheckResponse.class);
        assertCommonResponse(response);
    }
    
    @Test
    void testServerReloadResponse() throws JsonProcessingException {
        ServerReloadResponse response = new ServerReloadResponse();
        response.setRequestId("1");
        String actual = mapper.writeValueAsString(response);
        assertCommonResponseJson(actual);
        response = mapper.readValue(COMMON_JSON, ServerReloadResponse.class);
        assertCommonResponse(response);
    }
    
    @Test
    void testSetupAckResponse() throws JsonProcessingException {
        SetupAckResponse response = new SetupAckResponse();
        response.setRequestId("1");
        String actual = mapper.writeValueAsString(response);
        assertCommonResponseJson(actual);
        response = mapper.readValue(COMMON_JSON, SetupAckResponse.class);
        assertCommonResponse(response);
    }
    
    private void assertCommonResponse(Response response) {
        assertTrue(response.isSuccess());
        assertNull(response.getMessage());
        assertEquals(0, response.getErrorCode());
        assertEquals(ResponseCode.SUCCESS.code, response.getResultCode());
        assertEquals("1", response.getRequestId());
        assertEquals(TO_STRING, response.toString());
    }
    
    private void assertCommonResponseJson(String actualJson) {
        assertTrue(actualJson.contains("\"requestId\":\"1\""));
        assertTrue(actualJson.contains("\"success\":true"));
        assertTrue(actualJson.contains("\"errorCode\":0"));
        assertTrue(actualJson.contains("\"resultCode\":200"));
        assertFalse(actualJson.contains("\"message\""));
    }
}