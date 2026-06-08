/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.schedule;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.mutex.ReentrantAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LockExpireScanner}.
 *
 * @author DHX
 * @date 2026/06/02
 */
@ExtendWith(MockitoExtension.class)
public class LockExpireScannerTest {
    
    @Mock
    private LockManager lockManager;
    
    @Mock
    private LockOperationService lockOperationService;
    
    @InjectMocks
    private LockExpireScanner scanner;
    
    private static LockKey lockKey(String key) {
        return new LockKey(LockConstants.NACOS_LOCK_TYPE, key);
    }
    
    @Test
    public void testScanRemovesEmptyShellLock() {
        ReentrantAtomicLock emptyLock = new ReentrantAtomicLock("empty-key");
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey("empty-key"), emptyLock);
        when(lockManager.showLocks()).thenReturn(locks);
        
        scanner.scanExpiredLocks();
        
        verify(lockManager).removeMutexLock(lockKey("empty-key"));
    }
    
    @Test
    public void testScanSkipsNonEmptyLock() {
        ReentrantAtomicLock activeLock = new ReentrantAtomicLock("active-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        activeLock.tryLock(lockInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey("active-key"), activeLock);
        when(lockManager.showLocks()).thenReturn(locks);
        
        scanner.scanExpiredLocks();
        
        verify(lockManager, never()).removeMutexLock(any(LockKey.class));
        verify(lockOperationService, never()).expire(any(LockInstance.class));
    }
    
    @Test
    public void testScanExpiresTimedOutLock() {
        ReentrantAtomicLock expiredLock = new ReentrantAtomicLock("expired-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setEndTime(System.currentTimeMillis() - 1000);
        expiredLock.tryLock(lockInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey("expired-key"), expiredLock);
        when(lockManager.showLocks()).thenReturn(locks);
        when(lockOperationService.expire(any(LockInstance.class)))
            .thenReturn(LockResult.success(0));
        
        scanner.scanExpiredLocks();
        
        verify(lockOperationService).expire(any(LockInstance.class));
    }
    
    @Test
    public void testScanSkipsEmptyLockWithWaiters() {
        ReentrantAtomicLock lockWithWaiters = new ReentrantAtomicLock("empty-with-waiters");
        LockInfo waiterInfo = new LockInfo();
        waiterInfo.setOwner("waiter-1");
        waiterInfo.setConnectionId("conn-waiter");
        waiterInfo.setWaitTime(5000L);
        waiterInfo.setEndTime(System.currentTimeMillis() + 30000);
        lockWithWaiters.addWaiter(waiterInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey("empty-with-waiters"), lockWithWaiters);
        when(lockManager.showLocks()).thenReturn(locks);
        
        scanner.scanExpiredLocks();
        
        // Should NOT remove because it has waiters
        verify(lockManager, never()).removeMutexLock(any(LockKey.class));
    }
    
    @Test
    public void testScanHandlesShowLocksExceptionGracefully() {
        when(lockManager.showLocks()).thenThrow(new RuntimeException("test error"));
        
        // 不应抛出异常，scanExpiredLocks 内部 catch 了所有异常
        scanner.scanExpiredLocks();
    }
    
    @Test
    public void testScanHandlesExpireFailureGracefully() {
        ReentrantAtomicLock expiredLock = new ReentrantAtomicLock("expired-key");
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner("owner-1");
        lockInfo.setConnectionId("conn-1");
        lockInfo.setEndTime(System.currentTimeMillis() - 1000);
        expiredLock.tryLock(lockInfo);
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey("expired-key"), expiredLock);
        when(lockManager.showLocks()).thenReturn(locks);
        when(lockOperationService.expire(any(LockInstance.class)))
            .thenThrow(new RuntimeException("raft error"));
        
        // expireViaRaft 内部 catch 了异常，不应崩溃
        scanner.scanExpiredLocks();
    }
    
    @Test
    public void testScanSkipsNonAbstractAtomicLockInstance() {
        // AtomicLockService 的非 AbstractAtomicLock 实现应被跳过
        AtomicLockService nonAbstractLock = new AtomicLockService() {
            
            @Override
            public String getKey() {
                return "non-abstract";
            }
            
            @Override
            public Boolean tryLock(com.alibaba.nacos.lock.model.LockInfo lockInfo) {
                return false;
            }
            
            @Override
            public Boolean unLock(com.alibaba.nacos.lock.model.LockInfo lockInfo) {
                return false;
            }
            
            @Override
            public Boolean renew(com.alibaba.nacos.lock.model.LockInfo lockInfo) {
                return false;
            }
            
            @Override
            public Boolean forceRelease() {
                return false;
            }
            
            @Override
            public Boolean autoExpire() {
                return false;
            }
            
            @Override
            public Boolean isClear() {
                return true;
            }
        };
        
        ConcurrentHashMap<LockKey, AtomicLockService> locks = new ConcurrentHashMap<>();
        locks.put(lockKey("non-abstract"), nonAbstractLock);
        when(lockManager.showLocks()).thenReturn(locks);
        
        scanner.scanExpiredLocks();
        
        // 非 AbstractAtomicLock 实例不应触发任何操作
        verify(lockManager, never()).removeMutexLock(any(LockKey.class));
        verify(lockOperationService, never()).expire(any(LockInstance.class));
    }
}
