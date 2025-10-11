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

package com.alibaba.nacos.api.lock.model;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LockInstanceTest {
    
    private LockInstance lockInstance;
    
    @BeforeEach
    void setUp() {
        lockInstance = new LockInstance();
    }
    
    @Test
    void testConstructorWithParameters() {
        String key = "testKey";
        Long expiredTime = 1000L;
        String lockType = "testType";
        
        LockInstance instance = new LockInstance(key, expiredTime, lockType);
        
        assertEquals(key, instance.getKey());
        assertEquals(expiredTime, instance.getExpiredTime());
        assertEquals(lockType, instance.getLockType());
    }
    
    @Test
    void testDefaultConstructor() {
        LockInstance instance = new LockInstance();
        
        assertNull(instance.getKey());
        assertNull(instance.getExpiredTime());
        assertNull(instance.getLockType());
        assertNull(instance.getParams());
    }
    
    @Test
    void testGetAndSetKey() {
        String key = "testKey";
        lockInstance.setKey(key);
        assertEquals(key, lockInstance.getKey());
    }
    
    @Test
    void testGetAndSetExpiredTime() {
        Long expiredTime = 1000L;
        lockInstance.setExpiredTime(expiredTime);
        assertEquals(expiredTime, lockInstance.getExpiredTime());
    }
    
    @Test
    void testGetAndSetLockType() {
        String lockType = "testType";
        lockInstance.setLockType(lockType);
        assertEquals(lockType, lockInstance.getLockType());
    }
    
    @Test
    void testGetAndSetParams() {
        Map<String, String> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", "value2");
        
        lockInstance.setParams(params);
        assertEquals(params, lockInstance.getParams());
    }
    
    @Test
    void testLockMethod() throws NacosException {
        LockService lockService = mock(LockService.class);
        Boolean expectedResult = true;
        
        when(lockService.remoteTryLock(lockInstance)).thenReturn(expectedResult);
        
        lockInstance.setKey("testKey");
        lockInstance.setLockType("testType");
        lockInstance.setExpiredTime(1000L);
        
        Boolean result = lockInstance.lock(lockService);
        
        assertEquals(expectedResult, result);
    }
    
    @Test
    void testUnlockMethod() throws NacosException {
        LockService lockService = mock(LockService.class);
        Boolean expectedResult = true;
        
        when(lockService.remoteReleaseLock(lockInstance)).thenReturn(expectedResult);
        
        lockInstance.setKey("testKey");
        lockInstance.setLockType("testType");
        lockInstance.setExpiredTime(1000L);
        
        Boolean result = lockInstance.unLock(lockService);
        
        assertEquals(expectedResult, result);
    }
}