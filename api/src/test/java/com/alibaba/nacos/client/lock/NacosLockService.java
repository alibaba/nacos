/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.model.LockInstance;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock Naming Service for test {@link com.alibaba.nacos.api.lock.NacosLockFactory}.
 *
 * @author xiweng.yy
 */
public class NacosLockService implements LockService {
    
    public static final AtomicBoolean IS_THROW_EXCEPTION = new AtomicBoolean(false);
    
    public NacosLockService(Properties properties) throws NacosException {
        if (IS_THROW_EXCEPTION.get()) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "mock exception");
        }
    }
    
    @Override
    public Boolean lock(LockInstance instance) throws NacosException {
        return null;
    }
    
    @Override
    public Boolean unLock(LockInstance instance) throws NacosException {
        return null;
    }
    
    @Override
    public Boolean remoteTryLock(LockInstance instance) throws NacosException {
        return null;
    }
    
    @Override
    public Boolean remoteReleaseLock(LockInstance instance) throws NacosException {
        return null;
    }
    
    @Override
    public void shutdown() throws NacosException {
    
    }
}
