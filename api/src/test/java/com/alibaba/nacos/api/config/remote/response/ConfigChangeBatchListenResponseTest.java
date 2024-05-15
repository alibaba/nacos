/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigChangeBatchListenResponseTest extends BasedConfigResponseTest {
    
    ConfigChangeBatchListenResponse configChangeBatchListenResponse;
    
    @BeforeEach
    void before() {
        configChangeBatchListenResponse = new ConfigChangeBatchListenResponse();
        requestId = injectResponseUuId(configChangeBatchListenResponse);
        configChangeBatchListenResponse.addChangeConfig(DATA_ID, GROUP, TENANT);
    }
    
    @Override
    @Test
    public void testSerializeSuccessResponse() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configChangeBatchListenResponse);
        assertTrue(json.contains("\"success\":" + Boolean.TRUE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.SUCCESS.getCode()));
        assertTrue(json.contains("\"errorCode\":0"));
        assertTrue(json.contains(
                "\"changedConfigs\":[{\"dataId\":\"test_data\",\"group\":\"group\",\"tenant\":\"test_tenant\"}]"));
    }
    
    @Override
    @Test
    public void testSerializeFailResponse() throws JsonProcessingException {
        ConfigChangeBatchListenResponse configChangeBatchListenResponse = ConfigChangeBatchListenResponse.buildFailResponse(
                "Fail");
        String json = mapper.writeValueAsString(configChangeBatchListenResponse);
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.FAIL.getCode()));
        assertTrue(json.contains("\"errorCode\":0"));
        assertTrue(json.contains("\"message\":\"Fail\""));
        assertTrue(json.contains("\"success\":false"));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"061e36b0-c7bd-4fd0-950c-73b13ca1cb2f\","
                + "\"changedConfigs\":[{\"group\":\"group\",\"dataId\":\"test_data\",\"tenant\":\"test_tenant\"}],\"success\":true}";
        ConfigChangeBatchListenResponse actual = mapper.readValue(json, ConfigChangeBatchListenResponse.class);
        assertTrue(actual.isSuccess());
        assertEquals(ResponseCode.SUCCESS.getCode(), actual.getResultCode());
        assertEquals("061e36b0-c7bd-4fd0-950c-73b13ca1cb2f", actual.getRequestId());
        assertEquals(TENANT, actual.getChangedConfigs().get(0).getTenant());
        assertEquals(GROUP, actual.getChangedConfigs().get(0).getGroup());
        assertEquals(DATA_ID, actual.getChangedConfigs().get(0).getDataId());
        assertEquals("ConfigContext{group='group', dataId='test_data', tenant='test_tenant'}",
                actual.getChangedConfigs().get(0).toString());
    }
}
