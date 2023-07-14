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

import com.alibaba.nacos.lock.model.Service;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * mutex atomic lock test.
 *
 * @author 985492783@qq.com
 * @description MutexAtomicLockTest
 * @date 2023/7/13 19:43
 */
public class MutexAtomicLockTest {
    
    @Test
    public void testAtomicLock() {
        String key = "Hello World!";
        MutexAtomicLock mutexAtomicLock = new MutexAtomicLock(key);
        Service service = new Service("1.1.1.1", 8080);
        Service service2 = new Service("1.1.1.1", 8081);
        assertTrue(mutexAtomicLock.tryLock(service));
        //reentrant
        assertTrue(mutexAtomicLock.tryLock(service));
        assertFalse(mutexAtomicLock.tryLock(service2));
        assertTrue(mutexAtomicLock.unLock(service));
    }
}
