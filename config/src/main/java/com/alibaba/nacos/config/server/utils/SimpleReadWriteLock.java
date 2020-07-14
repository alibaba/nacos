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

/**
 * Simplest read-write lock implementation. Requires locking and unlocking must be called in pairs.
 *
 * @author Nacos
 */
public class SimpleReadWriteLock {
    
    /**
     * Try read lock.
     */
    public synchronized boolean tryReadLock() {
        if (isWriteLocked()) {
            return false;
        } else {
            status++;
            return true;
        }
    }
    
    /**
     * Release the read lock.
     */
    public synchronized void releaseReadLock() {
        status--;
    }
    
    /**
     * Try write lock.
     */
    public synchronized boolean tryWriteLock() {
        if (!isFree()) {
            return false;
        } else {
            status = -1;
            return true;
        }
    }
    
    public synchronized void releaseWriteLock() {
        status = 0;
    }
    
    private boolean isWriteLocked() {
        return status < 0;
    }
    
    private boolean isFree() {
        return status == 0;
    }
    
    /**
     * Zero means no lock; Negative Numbers mean write locks; Positive Numbers mean read locks, and the numeric value
     * represents the number of read locks.
     */
    private int status = 0;
}
