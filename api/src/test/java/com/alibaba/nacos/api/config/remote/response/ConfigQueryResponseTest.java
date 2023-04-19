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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigQueryResponseTest extends BasedConfigResponseTest {
    
    ConfigQueryResponse configQueryResponse;
    
    @Before
    public void before() {
        configQueryResponse = ConfigQueryResponse.buildSuccessResponse("success");
        configQueryResponse.setContentType("text");
        configQueryResponse.setEncryptedDataKey("encryptedKey");
        configQueryResponse.setLastModified(1111111L);
        configQueryResponse.setMd5(MD5);
        configQueryResponse.setTag(TAG);
        requestId = injectResponseUuId(configQueryResponse);
    }
    
    @Override
    @Test
    public void testSerializeSuccessResponse() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configQueryResponse);
        assertTrue(json.contains("\"success\":" + Boolean.TRUE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.SUCCESS.getCode()));
        assertTrue(json.contains("\"md5\":\"" + MD5 + "\""));
        assertTrue(json.contains("\"errorCode\":0"));
        assertTrue(json.contains("\"content\":\"success\""));
        assertTrue(json.contains("\"contentType\":\"text\""));
        assertTrue(json.contains("\"lastModified\":1111111"));
    }
    
    @Override
    @Test
    public void testSerializeFailResponse() throws JsonProcessingException {
        ConfigQueryResponse configQueryResponse = ConfigQueryResponse.buildFailResponse(500, "Fail");
        String json = mapper.writeValueAsString(configQueryResponse);
        assertTrue(json.contains("\"resultCode\":" + ResponseCode.FAIL.getCode()));
        assertTrue(json.contains("\"errorCode\":500"));
        assertTrue(json.contains("\"message\":\"Fail\""));
        assertTrue(json.contains("\"success\":false"));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"2239753e-e682-441c-83cf-fb8129ca68a4\","
                + "\"content\":\"success\",\"encryptedDataKey\":\"encryptedKey\",\"contentType\":\"text\",\"md5\":\"test_MD5\","
                + "\"lastModified\":1111111,\"tag\":\"tag\",\"beta\":false,\"success\":true}\n";
        ConfigQueryResponse actual = mapper.readValue(json, ConfigQueryResponse.class);
        assertTrue(actual.isSuccess());
        assertEquals(ResponseCode.SUCCESS.getCode(), actual.getResultCode());
        assertEquals("success", actual.getContent());
        assertEquals("text", actual.getContentType());
        assertEquals("2239753e-e682-441c-83cf-fb8129ca68a4", actual.getRequestId());
        assertEquals(MD5, actual.getMd5());
        assertEquals(TAG, actual.getTag());
        assertEquals("text", actual.getContentType());
        assertEquals(1111111L, actual.getLastModified());
    }
}
