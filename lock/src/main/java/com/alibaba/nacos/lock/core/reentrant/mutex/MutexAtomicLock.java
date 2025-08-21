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

package com.alibaba.nacos.lock.core.reentrant.mutex;

import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MutexAtomicLock.
 *
 * @author 985492783@qq.com
 * @description MutexAtomicLock
 * @date 2023/7/10 15:33
 */
public class MutexAtomicLock extends AbstractAtomicLock {
    
    private static final Integer EMPTY = 0;
    
    private static final Integer FULL = 1;
    
    private final AtomicInteger state;
    
    private Long expiredTimestamp;
    
    public MutexAtomicLock(String key) {
        super(key);
        this.state = new AtomicInteger(EMPTY);
    }
    
    @Override
    public Boolean tryLock(LockInfo lockInfo) {
        Long endTime = lockInfo.getEndTime();
        if (state.compareAndSet(EMPTY, FULL) || autoExpire()) {
            this.expiredTimestamp = endTime;
            return true;
        }
        return false;
    }
    
    @Override
    public Boolean unLock(LockInfo lockInfo) {
        return state.compareAndSet(FULL, EMPTY);
    }
    
    @Override
    public Boolean autoExpire() {
        return System.currentTimeMillis() >= this.expiredTimestamp;
    }
    
    @Override
    public Boolean isClear() {
        return EMPTY.equals(state.get()) || autoExpire();
    }
    
}
