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

public class ServerCheckResponseTest {
    
    ObjectMapper mapper = new ObjectMapper();
    
    @Before
    public void setUp() throws Exception {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    public void testSerialization() throws JsonProcessingException {
        ServerCheckResponse response = new ServerCheckResponse("35643245_1.1.1.1_3306", false);
        String actual = mapper.writeValueAsString(response);
        assertTrue(actual.contains("\"connectionId\":\"35643245_1.1.1.1_3306\""));
        assertTrue(actual.contains("\"supportAbilityNegotiation\":false"));
    }
    
    @Test
    public void testDeserialization() throws JsonProcessingException {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"connectionId\":\"35643245_1.1.1.1_3306\",\"success\":true,"
                + "\"supportAbilityNegotiation\":true}";
        ServerCheckResponse response = mapper.readValue(json, ServerCheckResponse.class);
        assertEquals("35643245_1.1.1.1_3306", response.getConnectionId());
        assertTrue(response.isSupportAbilityNegotiation());
    }
}