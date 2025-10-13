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
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFuzzyWatchRequestTest extends BasedConfigRequestTest {
    
    private static final String GROUP_KEY_PATTERN = "test.*";
    
    private static final String WATCH_TYPE = "FUZZY";
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        ConfigFuzzyWatchRequest configFuzzyWatchRequest = new ConfigFuzzyWatchRequest();
        configFuzzyWatchRequest.putAllHeader(HEADERS);
        configFuzzyWatchRequest.setGroupKeyPattern(GROUP_KEY_PATTERN);
        configFuzzyWatchRequest.setWatchType(WATCH_TYPE);
        configFuzzyWatchRequest.setInitializing(true);
        
        Set<String> receivedGroupKeys = new HashSet<>();
        receivedGroupKeys.add("test-group-key-1");
        receivedGroupKeys.add("test-group-key-2");
        configFuzzyWatchRequest.setReceivedGroupKeys(receivedGroupKeys);
        
        final String requestId = injectRequestUuId(configFuzzyWatchRequest);
        String json = mapper.writeValueAsString(configFuzzyWatchRequest);
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"groupKeyPattern\":\"" + GROUP_KEY_PATTERN));
        assertTrue(json.contains("\"watchType\":\"" + WATCH_TYPE));
        assertTrue(json.contains("\"initializing\":" + true));
        assertTrue(json.contains("\"receivedGroupKeys\":["));
        assertTrue(json.contains("\"test-group-key-1\""));
        assertTrue(json.contains("\"test-group-key-2\""));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{\"header1\":\"test_header1\"},\"groupKeyPattern\":\"test.*\","
                + "\"watchType\":\"FUZZY\",\"initializing\":true,"
                + "\"receivedGroupKeys\":[\"test-group-key-1\",\"test-group-key-2\"],\"module\":\"config\"}";
        ConfigFuzzyWatchRequest actual = mapper.readValue(json, ConfigFuzzyWatchRequest.class);
        assertEquals(GROUP_KEY_PATTERN, actual.getGroupKeyPattern());
        assertEquals(WATCH_TYPE, actual.getWatchType());
        assertEquals(true, actual.isInitializing());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getModule());
        assertEquals(HEADER_VALUE, actual.getHeader(HEADER_KEY));
        assertEquals(2, actual.getReceivedGroupKeys().size());
        assertTrue(actual.getReceivedGroupKeys().contains("test-group-key-1"));
        assertTrue(actual.getReceivedGroupKeys().contains("test-group-key-2"));
    }
}