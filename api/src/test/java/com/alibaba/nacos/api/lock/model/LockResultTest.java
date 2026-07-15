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

package com.alibaba.nacos.api.lock.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockResultTest {
    
    @Test
    void testSuccessFactoryClearsWaitingAndErrorState() {
        LockResult result = LockResult.success(2);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isWaiting());
        assertEquals(2, result.getReentrantCount());
        assertEquals(-1, result.getWaitPosition());
        assertNull(result.getErrorMessage());
        assertEquals("LockResult{success=true, count=2}", result.toString());
        
        result.setWaitPosition(3);
        result.setErrorMessage("failed");
        result.setSuccess(true);
        assertEquals(-1, result.getWaitPosition());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testFailFactory() {
        LockResult result = LockResult.fail("busy");
        
        assertFalse(result.isSuccess());
        assertFalse(result.isWaiting());
        assertEquals("busy", result.getErrorMessage());
        assertEquals("LockResult{success=false, msg=busy}", result.toString());
    }
    
    @Test
    void testWaitingFactory() {
        LockResult result = LockResult.waiting(4);
        
        assertFalse(result.isSuccess());
        assertTrue(result.isWaiting());
        assertEquals(4, result.getWaitPosition());
        assertEquals("LockResult{success=false, waiting=true, pos=4}", result.toString());
    }
    
    @Test
    void testGettersSettersEqualsAndHashCode() {
        LockResult result = new LockResult();
        result.setSuccess(false);
        result.setReentrantCount(1);
        result.setWaitPosition(5);
        result.setErrorMessage("queued");
        
        LockResult same = new LockResult(false);
        same.setReentrantCount(1);
        same.setWaitPosition(5);
        same.setErrorMessage("queued");
        
        LockResult different = LockResult.fail("other");
        
        assertEquals(result, result);
        assertEquals(result, same);
        assertEquals(result.hashCode(), same.hashCode());
        assertNotEquals(result, different);
        assertNotEquals(result, null);
        assertNotEquals(result, "queued");
    }
}
