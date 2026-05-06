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

package com.alibaba.nacos.lock.factory;

import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.core.reentrant.mutex.ClientAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;

/**
 * create clientLock.
 *
 * @author 985492783@qq.com
 * @date 2023/8/30 0:59
 */
public class ClientLockFactory implements LockFactory {
    
    public static final String TYPE = "CLIENT_LOCK_TYPE";
    
    @Override
    public String getLockType() {
        return TYPE;
    }
    
    @Override
    public AbstractAtomicLock createLock(String key) {
        return new ClientAtomicLock(key);
    }
    
    public static class ClientLockInstance extends LockInfo {
    
    }
}
