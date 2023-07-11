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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerLoaderInfoResponseTest {
    
    ObjectMapper mapper = new ObjectMapper();
    
    @Before
    public void setUp() throws Exception {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    public void testSerialization() throws JsonProcessingException {
        ServerLoaderInfoResponse response = new ServerLoaderInfoResponse();
        response.putMetricsValue("test", "testValue");
        String actual = mapper.writeValueAsString(response);
        System.out.println(actual);
        assertTrue(actual.contains("\"loaderMetrics\":{\"test\":\"testValue\"}"));
    }
    
    @Test
    public void testDeserialization() throws JsonProcessingException {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"loaderMetrics\":{\"test\":\"testValue\"},\"success\":true}";
        ServerLoaderInfoResponse response = mapper.readValue(json, ServerLoaderInfoResponse.class);
        assertEquals(1, response.getLoaderMetrics().size());
        assertEquals("testValue", response.getMetricsValue("test"));
    }
}