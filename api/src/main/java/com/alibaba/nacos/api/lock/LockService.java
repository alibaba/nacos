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

package com.alibaba.nacos.api.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.model.LockInstance;

/**
 * Nacos Lock Process.
 *
 * <p>lock => {@link LockService#lock(LockInstance)} -> {@link LockInstance#lock(LockService)} ->
 * {@link  LockService#remoteTryLock(LockInstance)} <br/> unLock => {@link LockService#unLock(LockInstance)} ->
 * {@link LockInstance#unLock(LockService)} -> {@link LockService#remoteReleaseLock(LockInstance)}
 *
 * @author 985492783@qq.com
 * @date 2023/8/24 19:49
 */
public interface LockService {
    
    /**
     * Real lock method expose to user to acquire the lock.<br/> It will call {@link LockInstance#lock(LockService)}
     * <br/>
     *
     * @param instance instance
     * @return Boolean
     * @throws NacosException NacosException
     */
    Boolean lock(LockInstance instance) throws NacosException;
    
    /**
     * Real lock method expose to user to release the lock.<br/> It will call {@link LockInstance#unLock(LockService)}
     * <br/>
     *
     * @param instance instance
     * @return Boolean
     * @throws NacosException NacosException
     */
    Boolean unLock(LockInstance instance) throws NacosException;
    
    /**
     * use grpc request to try lock.
     *
     * @param instance instance
     * @return Boolean
     * @throws NacosException NacosException
     */
    Boolean remoteTryLock(LockInstance instance) throws NacosException;
    
    /**
     * use grpc request to release lock.
     *
     * @param instance instance
     * @return Boolean
     * @throws NacosException NacosException
     */
    Boolean remoteReleaseLock(LockInstance instance) throws NacosException;
    
    /**
     * Shutdown the Resources, such as Thread Pool.
     *
     * @throws NacosException exception.
     */
    void shutdown() throws NacosException;
}
