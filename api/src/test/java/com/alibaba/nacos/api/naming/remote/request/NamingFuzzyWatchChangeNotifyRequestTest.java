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

package com.alibaba.nacos.api.naming.remote.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_RESOURCE_CHANGED;
import static com.alibaba.nacos.api.common.Constants.Naming.NAMING_MODULE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingFuzzyWatchChangeNotifyRequestTest {
    
    private static final String SERVICE_KEY = "serviceKey";
    
    private static final String CHANGED_TYPE = "changedType";
    
    private static ObjectMapper mapper;
    
    @BeforeAll
    static void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        NamingFuzzyWatchChangeNotifyRequest request = new NamingFuzzyWatchChangeNotifyRequest(SERVICE_KEY, CHANGED_TYPE);
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"serviceKey\":\"" + SERVICE_KEY + "\""));
        assertTrue(json.contains("\"changedType\":\"" + CHANGED_TYPE + "\""));
        assertTrue(json.contains("\"syncType\":\"" + FUZZY_WATCH_RESOURCE_CHANGED + "\""));
        assertTrue(json.contains("\"module\":\"" + NAMING_MODULE + "\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{},\"serviceKey\":\"serviceKey\",\"changedType\":\"changedType\","
                + "\"syncType\":\"FUZZY_WATCH_RESOURCE_CHANGED\",\"module\":\"naming\"}";
        NamingFuzzyWatchChangeNotifyRequest actual = mapper.readValue(json, NamingFuzzyWatchChangeNotifyRequest.class);
        assertEquals(SERVICE_KEY, actual.getServiceKey());
        assertEquals(CHANGED_TYPE, actual.getChangedType());
        assertEquals(FUZZY_WATCH_RESOURCE_CHANGED, actual.getSyncType());
        assertEquals(NAMING_MODULE, actual.getModule());
    }
}