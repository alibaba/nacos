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

package com.alibaba.nacos.api.lock.remote.request;

import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockOperationRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        LockOperationRequest request = new LockOperationRequest();
        request.setRequestId("1");
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setKey("testKey");
        lockInstance.setLockType("testType");
        lockInstance.setExpiredTime(1000L);
        
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"lockInstance\":{"));
        assertTrue(json.contains("\"key\":\"testKey\""));
        assertTrue(json.contains("\"lockType\":\"testType\""));
        assertTrue(json.contains("\"expiredTime\":1000"));
        assertTrue(json.contains("\"lockOperationEnum\":\"ACQUIRE\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"lockInstance\":{\"key\":\"testKey\",\"expiredTime\":1000,"
                + "\"lockType\":\"testType\"},\"lockOperationEnum\":\"ACQUIRE\",\"module\":\"lock\"}";
        LockOperationRequest result = mapper.readValue(json, LockOperationRequest.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        
        LockInstance lockInstance = result.getLockInstance();
        assertNotNull(lockInstance);
        assertEquals("testKey", lockInstance.getKey());
        assertEquals("testType", lockInstance.getLockType());
        assertEquals(Long.valueOf(1000L), lockInstance.getExpiredTime());
        
        assertEquals(LockOperationEnum.ACQUIRE, result.getLockOperationEnum());
    }
}