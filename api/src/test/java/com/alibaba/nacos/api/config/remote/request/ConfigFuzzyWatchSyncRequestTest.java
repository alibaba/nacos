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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFuzzyWatchSyncRequestTest extends BasedConfigRequestTest {
    
    private static final String GROUP_KEY_PATTERN = "test.*";
    
    private static final String SYNC_TYPE = Constants.FUZZY_WATCH_INIT_NOTIFY;
    
    private static final String GROUP_KEY = "test-group-key";
    
    private static final String CHANGED_TYPE = "ADD";
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        Set<Context> contexts = new HashSet<>();
        Context context = Context.build(GROUP_KEY, CHANGED_TYPE);
        contexts.add(context);
        
        ConfigFuzzyWatchSyncRequest configFuzzyWatchSyncRequest = ConfigFuzzyWatchSyncRequest.buildSyncRequest(
                SYNC_TYPE, contexts, GROUP_KEY_PATTERN, 2, 1);
        configFuzzyWatchSyncRequest.putAllHeader(HEADERS);
        final String requestId = injectRequestUuId(configFuzzyWatchSyncRequest);
        
        String json = mapper.writeValueAsString(configFuzzyWatchSyncRequest);
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"groupKeyPattern\":\"" + GROUP_KEY_PATTERN));
        assertTrue(json.contains("\"syncType\":\"" + SYNC_TYPE));
        assertTrue(json.contains("\"totalBatch\":" + 2));
        assertTrue(json.contains("\"currentBatch\":" + 1));
        assertTrue(json.contains("\"contexts\":["));
        assertTrue(json.contains("\"groupKey\":\"" + GROUP_KEY));
        assertTrue(json.contains("\"changedType\":\"" + CHANGED_TYPE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{\"header1\":\"test_header1\"},\"groupKeyPattern\":\"test.*\","
                + "\"syncType\":\"" + Constants.FUZZY_WATCH_INIT_NOTIFY + "\",\"totalBatch\":2,\"currentBatch\":1,"
                + "\"contexts\":[{\"groupKey\":\"test-group-key\",\"changedType\":\"ADD\"}],\"module\":\"config\"}";
        ConfigFuzzyWatchSyncRequest actual = mapper.readValue(json, ConfigFuzzyWatchSyncRequest.class);
        assertEquals(GROUP_KEY_PATTERN, actual.getGroupKeyPattern());
        assertEquals(SYNC_TYPE, actual.getSyncType());
        assertEquals(2, actual.getTotalBatch());
        assertEquals(1, actual.getCurrentBatch());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getModule());
        assertEquals(HEADER_VALUE, actual.getHeader(HEADER_KEY));
        assertEquals(1, actual.getContexts().size());
        Context context = actual.getContexts().iterator().next();
        assertEquals(GROUP_KEY, context.getGroupKey());
        assertEquals(CHANGED_TYPE, context.getChangedType());
    }
    
    @Test
    void testBuildInitFinishRequest() {
        ConfigFuzzyWatchSyncRequest request = ConfigFuzzyWatchSyncRequest.buildInitFinishRequest(GROUP_KEY_PATTERN);
        assertEquals(GROUP_KEY_PATTERN, request.getGroupKeyPattern());
        assertEquals(Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY, request.getSyncType());
    }
    
    @Test
    void testContextBuild() {
        Context context = Context.build(GROUP_KEY, CHANGED_TYPE);
        assertEquals(GROUP_KEY, context.getGroupKey());
        assertEquals(CHANGED_TYPE, context.getChangedType());
    }
}