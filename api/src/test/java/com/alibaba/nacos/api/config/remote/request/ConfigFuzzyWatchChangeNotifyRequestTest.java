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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFuzzyWatchChangeNotifyRequestTest extends BasedConfigRequestTest {
    
    private static final String GROUP_KEY = "test-group-key";
    
    private static final String CHANGE_TYPE = "ADD";
    
    ConfigFuzzyWatchChangeNotifyRequest configFuzzyWatchChangeNotifyRequest;
    
    String requestId;
    
    @BeforeEach
    void before() {
        configFuzzyWatchChangeNotifyRequest = new ConfigFuzzyWatchChangeNotifyRequest(GROUP_KEY, CHANGE_TYPE);
        configFuzzyWatchChangeNotifyRequest.putAllHeader(HEADERS);
        requestId = injectRequestUuId(configFuzzyWatchChangeNotifyRequest);
    }
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configFuzzyWatchChangeNotifyRequest);
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"groupKey\":\"" + GROUP_KEY));
        assertTrue(json.contains("\"changeType\":\"" + CHANGE_TYPE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{\"header1\":\"test_header1\"},\"groupKey\":\"test-group-key\","
                + "\"changeType\":\"ADD\",\"module\":\"config\"}";
        ConfigFuzzyWatchChangeNotifyRequest actual = mapper.readValue(json, ConfigFuzzyWatchChangeNotifyRequest.class);
        assertEquals(GROUP_KEY, actual.getGroupKey());
        assertEquals(CHANGE_TYPE, actual.getChangeType());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getModule());
        assertEquals(HEADER_VALUE, actual.getHeader(HEADER_KEY));
    }
    
    @Test
    void testToString() {
        ConfigFuzzyWatchChangeNotifyRequest request = new ConfigFuzzyWatchChangeNotifyRequest(GROUP_KEY, CHANGE_TYPE);
        assertEquals("FuzzyListenNotifyChangeRequest{', groupKey='" + GROUP_KEY + "', changeType=" + CHANGE_TYPE + "}", 
                request.toString());
    }
}