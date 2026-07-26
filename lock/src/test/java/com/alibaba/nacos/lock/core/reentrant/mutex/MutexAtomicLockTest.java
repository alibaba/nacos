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
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mutex atomic lock test.
 *
 * @author 985492783@qq.com
 * @date 2023/8/28 13:13
 */
@ExtendWith(MockitoExtension.class)
public class MutexAtomicLockTest {
    
    @Mock
    private LockManager lockManager;
    
    @Test
    public void testLockAndUnlock() {
        Mockito.when(lockManager.getMutexLock(Mockito.any()))
            .thenReturn(new MutexAtomicLock("key"));
        AtomicLockService lock =
            lockManager.getMutexLock(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key"));
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("test-owner");
        lockInfo.setEndTime(System.currentTimeMillis() + 2_000_000);
        assertTrue(lock.tryLock(lockInfo));
        assertTrue(lock.unLock(lockInfo));
    }
    
    @Test
    public void testAutoExpire() {
        Mockito.when(lockManager.getMutexLock(Mockito.any()))
            .thenReturn(new MutexAtomicLock("key"));
        AtomicLockService lock =
            lockManager.getMutexLock(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key"));
        
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("test-owner");
        lockInfo.setEndTime(System.currentTimeMillis() - 2_000_000);
        assertTrue(lock.tryLock(lockInfo));
        assertTrue(lock.autoExpire());
        
        LockInfo lockInstanceAuto = new LockInfo();
        lockInstanceAuto.setOwner("test-owner-2");
        lockInstanceAuto.setEndTime(System.currentTimeMillis() + 2_000_000);
        assertTrue(lock.tryLock(lockInstanceAuto));
    }
    
    @Test
    public void testMigrateFromLegacyWithFullState() throws Exception {
        MutexAtomicLock mutexLock = new MutexAtomicLock("key");
        setState(mutexLock, 1);
        
        mutexLock.migrateFromLegacy();
        
        assertEquals("legacy-migrated", mutexLock.getOwner());
        assertEquals(1, mutexLock.getReentrantCount());
    }
    
    @Test
    public void testMigrateFromLegacyWithEmptyState() throws Exception {
        MutexAtomicLock mutexLock = new MutexAtomicLock("key");
        setState(mutexLock, 0);
        
        mutexLock.migrateFromLegacy();
        
        assertNull(mutexLock.getOwner());
        assertEquals(0, mutexLock.getReentrantCount());
    }
    
    @Test
    public void testMigrateFromLegacyWithNullState() throws Exception {
        MutexAtomicLock mutexLock = new MutexAtomicLock("key");
        // state is null by default
        
        mutexLock.migrateFromLegacy();
        
        assertNull(mutexLock.getOwner());
    }
    
    @Test
    public void testMigrateFromLegacyIdempotent() throws Exception {
        MutexAtomicLock mutexLock = new MutexAtomicLock("key");
        setState(mutexLock, 1);
        
        mutexLock.migrateFromLegacy();
        assertEquals("legacy-migrated", mutexLock.getOwner());
        
        // Second call should be no-op
        mutexLock.migrateFromLegacy();
        assertEquals("legacy-migrated", mutexLock.getOwner());
    }
    
    private void setState(MutexAtomicLock mutexLock, Integer value) throws Exception {
        Field stateField = MutexAtomicLock.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(mutexLock, value);
    }
    
}
