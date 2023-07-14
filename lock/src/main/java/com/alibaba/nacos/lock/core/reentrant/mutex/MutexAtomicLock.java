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
import com.alibaba.nacos.lock.model.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * MutexAtomicLock.
 * @author 985492783@qq.com
 * @description MutexAtomicLock
 * @date 2023/7/10 15:33
 */
public class MutexAtomicLock extends AbstractAtomicLock {

    private final AtomicReference<Service> lockService;

    public MutexAtomicLock(String key) {
        super(key);
        this.lockService = new AtomicReference<>(null);
    }

    @Override
    public Boolean innerTryLock(Service service) {
        if (service == lockService.get()) {
            return true;
        }
        return lockService.compareAndSet(null, service);
    }

    @Override
    public Boolean innerUnLock(Service service) {
        return lockService.compareAndSet(service, null);
    }
}
