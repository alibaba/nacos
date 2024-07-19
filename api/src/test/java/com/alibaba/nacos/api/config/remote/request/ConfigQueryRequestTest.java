/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigQueryRequestTest extends BasedConfigRequestTest {
    
    ConfigQueryRequest configQueryRequest;
    
    Map<String, String> headers = new HashMap<>();
    
    String requestId;
    
    @BeforeEach
    void before() {
        headers.put(Constants.Config.NOTIFY_HEADER, Boolean.TRUE.toString());
        configQueryRequest = ConfigQueryRequest.build(DATA_ID, GROUP, TENANT);
        configQueryRequest.putAllHeader(headers);
        configQueryRequest.setTag(TAG);
        requestId = injectRequestUuId(configQueryRequest);
    }
    
    @Test
    void testIsNotify() {
        assertTrue(configQueryRequest.isNotify());
    }
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configQueryRequest);
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"dataId\":\"" + DATA_ID));
        assertTrue(json.contains("\"group\":\"" + GROUP));
        assertTrue(json.contains("\"tenant\":\"" + TENANT));
        assertTrue(json.contains("\"tag\":\"" + TAG));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{\"notify\":\"true\"},\"dataId\":\"test_data\",\"group\":\"group\","
                + "\"tenant\":\"test_tenant\",\"notify\":true,\"module\":\"config\",\"tag\":\"tag\"}";
        ConfigQueryRequest actual = mapper.readValue(json, ConfigQueryRequest.class);
        assertEquals(DATA_ID, actual.getDataId());
        assertEquals(GROUP, actual.getGroup());
        assertEquals(TENANT, actual.getTenant());
        assertEquals(TAG, actual.getTag());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getModule());
    }
}
