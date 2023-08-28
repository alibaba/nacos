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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.factory.LockFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * nacos lock manager.
 *
 * @author 985492783@qq.com
 * @date 2023/8/22 21:01
 */
@Service
public class NacosLockManager implements LockManager {
    
    Map<String, LockFactory> factoryMap;
    
    Map<LockKey, AbstractAtomicLock> atomicLockMap = new ConcurrentHashMap<>();
    
    public NacosLockManager() {
        Collection<LockFactory> factories = NacosServiceLoader.load(LockFactory.class);
        factoryMap = factories.stream()
                .collect(Collectors.toConcurrentMap(LockFactory::getLockType, lockFactory -> lockFactory));
    }
    
    @Override
    public AbstractAtomicLock getMutexLock(String lockType, String key) {
        LockKey lockKey = new LockKey(lockType, key);
        if (!factoryMap.containsKey(lockType)) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR);
        }
        return atomicLockMap.computeIfAbsent(lockKey, lock -> {
            LockFactory lockFactory = factoryMap.get(lock.getLockType());
            return lockFactory.createLock(lock.getKey());
        });
    }
    
    public static class LockKey {
        
        public LockKey(String lockType, String key) {
            this.lockType = lockType;
            this.key = key;
        }
        
        private String lockType;
        
        private String key;
        
        public String getLockType() {
            return lockType;
        }
        
        public void setLockType(String lockType) {
            this.lockType = lockType;
        }
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LockKey lockKey = (LockKey) o;
            return Objects.equals(lockType, lockKey.lockType) && Objects.equals(key, lockKey.key);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(lockType, key);
        }
    }
}
