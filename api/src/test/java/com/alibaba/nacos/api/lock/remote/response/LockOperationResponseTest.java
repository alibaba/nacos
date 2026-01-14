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

package com.alibaba.nacos.api.lock.remote.response;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockOperationResponseTest extends BasicRequestTest {
    
    @Test
    void testConstructor() {
        LockOperationResponse response = new LockOperationResponse(true);
        assertTrue((Boolean) response.getResult());
        
        response = new LockOperationResponse(false);
        assertFalse((Boolean) response.getResult());
    }
    
    @Test
    void testSuccess() {
        LockOperationResponse response = LockOperationResponse.success(true);
        assertTrue((Boolean) response.getResult());
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
    }
    
    @Test
    void testFail() {
        String errorMessage = "test error";
        LockOperationResponse response = LockOperationResponse.fail(errorMessage);
        assertFalse((Boolean) response.getResult());
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(errorMessage, response.getMessage());
    }
    
    @Test
    void testSerialize() throws Exception {
        LockOperationResponse response = new LockOperationResponse();
        response.setRequestId("1");
        response.setResult(true);
        
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"result\":true"));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"1\",\"result\":true,\"success\":true}";
        LockOperationResponse result = mapper.readValue(json, LockOperationResponse.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertTrue((Boolean) result.getResult());
    }
}