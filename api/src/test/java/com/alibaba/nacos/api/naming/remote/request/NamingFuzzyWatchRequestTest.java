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

import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.Naming.NAMING_MODULE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingFuzzyWatchRequestTest {
    
    private static final String NAMESPACE = "namespace";
    
    private static final String GROUP_KEY_PATTERN = "groupKeyPattern";
    
    private static final String WATCH_TYPE = "watchType";
    
    private static ObjectMapper mapper;
    
    @BeforeAll
    static void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        NamingFuzzyWatchRequest request = new NamingFuzzyWatchRequest(GROUP_KEY_PATTERN, WATCH_TYPE);
        request.setNamespace(NAMESPACE);
        Set<String> receivedGroupKeys = new HashSet<>();
        receivedGroupKeys.add("key1");
        receivedGroupKeys.add("key2");
        request.setReceivedGroupKeys(receivedGroupKeys);
        request.setInitializing(true);
        
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"namespace\":\"" + NAMESPACE + "\""));
        assertTrue(json.contains("\"groupKeyPattern\":\"" + GROUP_KEY_PATTERN + "\""));
        assertTrue(json.contains("\"watchType\":\"" + WATCH_TYPE + "\""));
        assertTrue(json.contains("\"receivedGroupKeys\":["));
        assertTrue(json.contains("\"initializing\":true"));
        assertTrue(json.contains("\"module\":\"" + NAMING_MODULE + "\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{},\"initializing\":true,\"namespace\":\"namespace\",\"groupKeyPattern\":\"groupKeyPattern\","
                + "\"receivedGroupKeys\":[\"key1\",\"key2\"],\"watchType\":\"watchType\",\"module\":\"naming\"}";
        NamingFuzzyWatchRequest actual = mapper.readValue(json, NamingFuzzyWatchRequest.class);
        assertEquals(NAMESPACE, actual.getNamespace());
        assertEquals(GROUP_KEY_PATTERN, actual.getGroupKeyPattern());
        assertEquals(WATCH_TYPE, actual.getWatchType());
        assertEquals(true, actual.isInitializing());
        assertEquals(NAMING_MODULE, actual.getModule());
        assertEquals(2, actual.getReceivedGroupKeys().size());
    }
}