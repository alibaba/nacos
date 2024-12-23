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

package com.alibaba.nacos.lock;

import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.LockKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * lock manager.
 *
 * @author 985492783@qq.com
 * @description LockManager
 * @date 2023/7/10 15:10
 */
public interface LockManager {
    
    /**
     * get mutex lock.
     *
     * @param lockType lock type
     * @param key      key
     * @return AbstractAtomicLock
     */
    AtomicLockService getMutexLock(String lockType, String key);
    
    /**
     * show all atomicLock entity to snapshot save.
     *
     * @return Map
     */
    ConcurrentHashMap<LockKey, AtomicLockService> showLocks();
}
