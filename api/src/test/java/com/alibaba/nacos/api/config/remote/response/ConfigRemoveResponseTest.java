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

package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ConfigRemoveResponseTest extends BasedConfigResponseTest {
    
    ConfigRemoveResponse configRemoveResponse;
    
    @Before
    public void before() {
        configRemoveResponse = ConfigRemoveResponse.buildSuccessResponse();
        requestId = injectResponseUuId(configRemoveResponse);
    }
    
    @Override
    @Test
    public void testSerializeSuccessResponse() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configRemoveResponse);
        assertTrue(json.contains("\"success\":" + Boolean.TRUE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.SUCCESS.getCode()));
        assertTrue(json.contains("\"errorCode\":0"));
    }
    
    @Override
    @Test
    public void testSerializeFailResponse() throws JsonProcessingException {
        ConfigRemoveResponse configRemoveResponse = ConfigRemoveResponse.buildFailResponse("Fail");
        String json = mapper.writeValueAsString(configRemoveResponse);
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.FAIL.getCode()));
        assertTrue(json.contains("\"errorCode\":0"));
        assertTrue(json.contains("\"message\":\"Fail\""));
        assertTrue(json.contains("\"success\":false"));
    }
}
