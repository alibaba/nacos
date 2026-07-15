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

package com.alibaba.nacos.api.lock.remote.request;

import com.alibaba.nacos.api.lock.common.LockNotificationType;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static com.alibaba.nacos.api.common.Constants.Lock.LOCK_MODULE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockNotificationRequestTest extends BasicRequestTest {
    
    @Test
    void testConstructorAndAccessors() {
        LockNotificationRequest request = new LockNotificationRequest("key", "type", "owner",
            LockNotificationType.AVAILABLE);
        
        assertEquals("key", request.getLockKey());
        assertEquals("type", request.getLockType());
        assertEquals("owner", request.getOwner());
        assertEquals(LockNotificationType.AVAILABLE, request.getNotificationType());
        assertEquals(LOCK_MODULE, request.getModule());
        
        request.setLockKey("key2");
        request.setLockType("type2");
        request.setOwner("owner2");
        request.setNotificationType(LockNotificationType.TIMEOUT);
        
        assertEquals("key2", request.getLockKey());
        assertEquals("type2", request.getLockType());
        assertEquals("owner2", request.getOwner());
        assertEquals(LockNotificationType.TIMEOUT, request.getNotificationType());
    }
    
    @Test
    void testAvailableAndTimeoutFactories() {
        LockNotificationRequest available =
            LockNotificationRequest.available("key", "type", "owner");
        LockNotificationRequest timeout = LockNotificationRequest.timeout("key", "type", "owner");
        
        assertEquals(LockNotificationType.AVAILABLE, available.getNotificationType());
        assertEquals(LockNotificationType.TIMEOUT, timeout.getNotificationType());
    }
    
    @Test
    void testSerializeAndDeserialize() throws Exception {
        LockNotificationRequest request =
            LockNotificationRequest.available("key", "type", "owner");
        request.setRequestId("1");
        
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"lockKey\":\"key\""));
        assertTrue(json.contains("\"lockType\":\"type\""));
        assertTrue(json.contains("\"owner\":\"owner\""));
        assertTrue(json.contains("\"notificationType\":\"AVAILABLE\""));
        
        LockNotificationRequest result =
            mapper.readValue(json, LockNotificationRequest.class);
        assertEquals("1", result.getRequestId());
        assertEquals("key", result.getLockKey());
        assertEquals("type", result.getLockType());
        assertEquals("owner", result.getOwner());
        assertEquals(LockNotificationType.AVAILABLE, result.getNotificationType());
    }
}
