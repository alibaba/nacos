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

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * mutex atomic lock test.
 *
 * @author 985492783@qq.com
 * @date 2023/8/28 13:13
 */
@RunWith(MockitoJUnitRunner.class)
public class MutexAtomicLockTest {
    
    @Mock
    private LockManager lockManager;
    
    @Test
    public void testLockAndUnlock() {
        Mockito.when(lockManager.getMutexLock(Mockito.any(), Mockito.any())).thenReturn(new MutexAtomicLock("key"));
        AtomicLockService lock = lockManager.getMutexLock("key", LockConstants.NACOS_LOCK_TYPE);
        LockInstance lockInstance = new LockInstance();
        lockInstance.setExpireTimestamp(System.currentTimeMillis() + 2_000_000);
        Assert.assertTrue(lock.tryLock(lockInstance));
        Assert.assertTrue(lock.unLock(lockInstance));
    }
    
    @Test
    public void testAutoExpire() {
        Mockito.when(lockManager.getMutexLock(LockConstants.NACOS_LOCK_TYPE, "key"))
                .thenReturn(new MutexAtomicLock("key"));
        AtomicLockService lock = lockManager.getMutexLock(LockConstants.NACOS_LOCK_TYPE, "key");
        
        LockInstance lockInstance = new LockInstance();
        lockInstance.setExpireTimestamp(System.currentTimeMillis() - 2_000_000);
        Assert.assertTrue(lock.tryLock(lockInstance));
        Assert.assertTrue(lock.autoExpire());
        
        LockInstance lockInstanceAuto = new LockInstance();
        lockInstanceAuto.setExpireTimestamp(System.currentTimeMillis() + 2_000_000);
        Assert.assertTrue(lock.tryLock(lockInstanceAuto));
    }
    
}
