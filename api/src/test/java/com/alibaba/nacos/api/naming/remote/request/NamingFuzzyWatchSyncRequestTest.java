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

import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchSyncRequest.Context;
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

class NamingFuzzyWatchSyncRequestTest {
    
    private static final String GROUP_KEY_PATTERN = "groupKeyPattern";
    
    private static final String SYNC_TYPE = "syncType";
    
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
        Set<Context> contexts = new HashSet<>();
        Context context = Context.build(SERVICE_KEY, CHANGED_TYPE);
        contexts.add(context);
        
        NamingFuzzyWatchSyncRequest request = new NamingFuzzyWatchSyncRequest(GROUP_KEY_PATTERN, SYNC_TYPE, contexts);
        request.setTotalBatch(2);
        request.setCurrentBatch(1);
        
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"groupKeyPattern\":\"" + GROUP_KEY_PATTERN + "\""));
        assertTrue(json.contains("\"syncType\":\"" + SYNC_TYPE + "\""));
        assertTrue(json.contains("\"contexts\":[{"));
        assertTrue(json.contains("\"serviceKey\":\"" + SERVICE_KEY + "\""));
        assertTrue(json.contains("\"changedType\":\"" + CHANGED_TYPE + "\""));
        assertTrue(json.contains("\"totalBatch\":2"));
        assertTrue(json.contains("\"currentBatch\":1"));
        assertTrue(json.contains("\"module\":\"" + NAMING_MODULE + "\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{},\"groupKeyPattern\":\"groupKeyPattern\",\"contexts\":[{\"serviceKey\":\"serviceKey\","
                + "\"changedType\":\"changedType\"}],\"totalBatch\":2,\"currentBatch\":1,\"syncType\":\"syncType\",\"module\":\"naming\"}";
        NamingFuzzyWatchSyncRequest actual = mapper.readValue(json, NamingFuzzyWatchSyncRequest.class);
        assertEquals(GROUP_KEY_PATTERN, actual.getGroupKeyPattern());
        assertEquals(SYNC_TYPE, actual.getSyncType());
        assertEquals(2, actual.getTotalBatch());
        assertEquals(1, actual.getCurrentBatch());
        assertEquals(NAMING_MODULE, actual.getModule());
        assertEquals(1, actual.getContexts().size());
        
        Context context = actual.getContexts().iterator().next();
        assertEquals(SERVICE_KEY, context.getServiceKey());
        assertEquals(CHANGED_TYPE, context.getChangedType());
    }
    
    @Test
    void testBuildSyncNotifyRequest() {
        Set<Context> contexts = new HashSet<>();
        Context context = Context.build(SERVICE_KEY, CHANGED_TYPE);
        contexts.add(context);
        
        NamingFuzzyWatchSyncRequest request = NamingFuzzyWatchSyncRequest.buildSyncNotifyRequest(
                GROUP_KEY_PATTERN, SYNC_TYPE, contexts, 3, 2);
        
        assertEquals(GROUP_KEY_PATTERN, request.getGroupKeyPattern());
        assertEquals(SYNC_TYPE, request.getSyncType());
        assertEquals(3, request.getTotalBatch());
        assertEquals(2, request.getCurrentBatch());
        assertEquals(1, request.getContexts().size());
        
        Context actualContext = request.getContexts().iterator().next();
        assertEquals(SERVICE_KEY, actualContext.getServiceKey());
        assertEquals(CHANGED_TYPE, actualContext.getChangedType());
    }
}