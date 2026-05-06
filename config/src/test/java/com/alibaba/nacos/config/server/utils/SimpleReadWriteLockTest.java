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

package com.alibaba.nacos.config.server.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
class SimpleReadWriteLockTest {
    
    @Test
    void testDoubleReadLockByAllReleaseAndWriteLock() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        assertTrue(lock.tryReadLock());
        assertTrue(lock.tryReadLock());
        
        lock.releaseReadLock();
        lock.releaseReadLock();
        
        assertTrue(lock.tryWriteLock());
    }
    
    @Test
    void testAddWriteLock() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        assertTrue(lock.tryWriteLock());
        lock.releaseWriteLock();
    }
    
    @Test
    void testDoubleWriteLock() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        
        assertTrue(lock.tryWriteLock());
        assertFalse(lock.tryWriteLock());
    }
    
    @Test
    void testFirstReadLockThenWriteLock() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        
        assertTrue(lock.tryReadLock());
        assertFalse(lock.tryWriteLock());
    }
    
    @Test
    void testFirstWriteLockThenReadLock() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        
        assertTrue(lock.tryWriteLock());
        assertFalse(lock.tryReadLock());
    }
    
    @Test
    void testDoubleReadLockAndOneReleaseOneFailed() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        assertTrue(lock.tryReadLock());
        assertTrue(lock.tryReadLock());
        
        lock.releaseReadLock();
        
        assertFalse(lock.tryWriteLock());
    }
}
