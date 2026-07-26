/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockNotificationResponseTest extends BasicRequestTest {
    
    @Test
    void testSuccess() {
        LockNotificationResponse response = LockNotificationResponse.success();
        
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
    }
    
    @Test
    void testFail() {
        LockNotificationResponse response = LockNotificationResponse.fail("failed");
        
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals("failed", response.getMessage());
    }
    
    @Test
    void testSerializeAndDeserialize() throws Exception {
        LockNotificationResponse response = LockNotificationResponse.fail("failed");
        response.setRequestId("1");
        
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"message\":\"failed\""));
        
        LockNotificationResponse result =
            mapper.readValue(json, LockNotificationResponse.class);
        assertEquals("1", result.getRequestId());
        assertEquals("failed", result.getMessage());
        assertEquals(ResponseCode.FAIL.getCode(), result.getResultCode());
    }
}
