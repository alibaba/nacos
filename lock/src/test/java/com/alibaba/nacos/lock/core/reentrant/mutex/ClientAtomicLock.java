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

import java.util.concurrent.atomic.AtomicReference;

/**
 * use clientId to get lock.
 *
 * @author 985492783@qq.com
 * @date 2023/8/30 1:02
 */
public class ClientAtomicLock extends AbstractAtomicLock {
    
    private static final String EMPTY = null;
    
    private final AtomicReference<String> state;
    
    private Long expireTimestamp;
    
    public ClientAtomicLock(String key) {
        super(key);
        this.state = new AtomicReference<>(EMPTY);
    }
    
    @Override
    public Boolean tryLock(LockInfo lockInfo) {
        String nacosClientId = (String) lockInfo.getParams().get("nacosClientId");
        if (nacosClientId == null) {
            return false;
        }
        return state.compareAndSet(EMPTY, nacosClientId) || state.get().equals(nacosClientId);
    }
    
    @Override
    public Boolean unLock(LockInfo lockInfo) {
        String nacosClientId = (String) lockInfo.getParams().get("nacosClientId");
        return state.compareAndSet(nacosClientId, EMPTY);
    }
    
    @Override
    public Boolean autoExpire() {
        return System.currentTimeMillis() >= this.expireTimestamp;
    }
    
    @Override
    public Boolean isClear() {
        return state.get() == null || autoExpire();
    }
}
