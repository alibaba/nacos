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

package com.alibaba.nacos.api.remote.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyContentRequestTest extends BasicRequestTest {
    
    private static final String COMMON_JSON = "{\"headers\":{\"clientIp\":\"1.1.1.1\"},\"requestId\":\"1\",\"module\":\"internal\"}";
    
    private static final String TO_STRING = "%s{headers={clientIp=1.1.1.1}, requestId='1'}";
    
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    void testClientDetectionRequest() throws JsonProcessingException, InstantiationException, IllegalAccessException {
        doTest(ClientDetectionRequest.class);
    }
    
    @Test
    void testHealthCheckRequest() throws JsonProcessingException, InstantiationException, IllegalAccessException {
        doTest(HealthCheckRequest.class);
    }
    
    @Test
    void testServerCheckRequest() throws JsonProcessingException, InstantiationException, IllegalAccessException {
        doTest(ServerCheckRequest.class);
    }
    
    @Test
    void testServerLoaderInfoRequest() throws JsonProcessingException, InstantiationException, IllegalAccessException {
        doTest(ServerLoaderInfoRequest.class);
    }
    
    private void doTest(Class<? extends Request> clazz)
            throws IllegalAccessException, InstantiationException, JsonProcessingException {
        Request request = clazz.newInstance();
        request.setRequestId("1");
        request.putHeader("clientIp", "1.1.1.1");
        String actual = mapper.writeValueAsString(request);
        assertCommonRequestJson(actual);
        request = mapper.readValue(COMMON_JSON, ServerLoaderInfoRequest.class);
        assertCommonRequest(request);
    }
    
    private void assertCommonRequestJson(String actualJson) {
        assertTrue(actualJson.contains("\"requestId\":\"1\""));
        assertTrue(actualJson.contains("\"module\":\"internal\""));
        assertTrue(actualJson.contains("\"headers\":{\"clientIp\":\"1.1.1.1\"}"));
    }
    
    private void assertCommonRequest(Request request) {
        assertEquals("1", request.getRequestId());
        assertEquals("internal", request.getModule());
        assertEquals(1, request.getHeaders().size());
        assertEquals("1.1.1.1", request.getHeader("clientIp"));
        assertEquals(String.format(TO_STRING, request.getClass().getSimpleName()), request.toString());
    }
}
