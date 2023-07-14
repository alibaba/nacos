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

package com.alibaba.nacos.lock.core;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.model.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * nacos lock manager.
 * @author 985492783@qq.com
 * @date 2023/6/28 2:16
 */
@org.springframework.stereotype.Service
public class NacosLockManager implements LockManager {

    private final ConcurrentHashMap<String, AbstractAtomicLock> atomicLockMap;
    
    private final ConcurrentHashMap<String, Service> connectionServiceMap;
    
    private final ConcurrentHashMap<Service, Service> singletonServiceMap;
    
    public NacosLockManager() {
        this.singletonServiceMap = new ConcurrentHashMap<>();
        this.connectionServiceMap = new ConcurrentHashMap<>();
        this.atomicLockMap = new ConcurrentHashMap<>();
        //TODO 增加定时轮询，超时机制
    }

    @Override
    public AbstractAtomicLock getMutexLock(String key) {
        AbstractAtomicLock atomicLock = atomicLockMap.computeIfAbsent(key, MutexAtomicLock::new);
        return atomicLock;
    }

    @Override
    public void acquireLock(String connectionId, Service service) {
        connectionServiceMap.put(connectionId, service);
    }
    
    @Override
    public Service getSingletonService(Service service) {
        if (service == null) {
            return null;
        }
        return singletonServiceMap.computeIfAbsent(service, (key) -> service);
    }
    
    @Override
    public void disConnected(String connectionId) {
        Service service = getSingletonService(connectionServiceMap.remove(connectionId));
        if (service == null || CollectionUtils.isEmpty(service.getKeysSet())) {
            return;
        }
        service.getKeysSet().forEach(key -> {
            atomicLockMap.get(key).unLock(service);
        });
    }
}
