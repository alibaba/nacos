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

package com.alibaba.nacos.test.sdk.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Java SDK {@link LockService}.
 *
 * <p>The full scenario matrix and remaining gaps are recorded in
 * {@code test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: acquire and release a distributed Nacos mutex lock through the
 *     public Java SDK factory and through direct remote methods.</li>
 *     <li>Boundary/validation: a second SDK client cannot acquire the same held lock, release
 *     clears the lock, released or expired keys can be acquired again, repeated release returns
 *     false, and null lock instances fail at the SDK boundary.</li>
 *     <li>Error handling: unsupported lock type is returned as a controlled
 *     {@link NacosException} instead of an uncontrolled client runtime failure; missing lock key
 *     is also mapped to a controlled server error.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class LockServiceJavaSdkITCase extends JavaSdkBaseITCase {
    
    @Test
    public void testAcquireCompeteReleaseAndReacquireLock() throws Exception {
        LockService owner = createLockService();
        LockService contender = createLockService();
        LockInstance lock = newLockInstance("lifecycle", LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> owner.unLock(lock));
        addCleanup(() -> contender.unLock(lock));
        
        assertTrue(owner.lock(lock));
        assertFalse(contender.lock(lock));
        assertTrue(owner.unLock(lock));
        assertTrue(contender.lock(lock));
        assertTrue(contender.unLock(lock));
        assertFalse(owner.unLock(lock));
    }
    
    @Test
    public void testDirectRemoteTryLockAndReleaseLock() throws Exception {
        LockService lockService = createLockService();
        LockInstance lock = newLockInstance("remote", LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> lockService.remoteReleaseLock(lock));
        
        assertTrue(lockService.remoteTryLock(lock));
        assertFalse(lockService.remoteTryLock(lock));
        assertTrue(lockService.remoteReleaseLock(lock));
        assertFalse(lockService.remoteReleaseLock(lock));
    }
    
    @Test
    public void testExpiredLockCanBeAcquiredByAnotherClient() throws Exception {
        LockService owner = createLockService();
        LockService contender = createLockService();
        LockInstance lock = new LockInstance("java-sdk-it-lock-expire-"
                + randomServiceName("lock"), 500L, LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> owner.unLock(lock));
        addCleanup(() -> contender.unLock(lock));
        
        assertTrue(owner.lock(lock));
        assertFalse(contender.lock(lock));
        waitUntil("expired lock should be acquirable by another client",
                () -> contender.lock(lock));
        assertTrue(contender.unLock(lock));
    }
    
    @Test
    public void testInvalidLockInputThrowsControlledException() throws Exception {
        LockService lockService = createLockService();
        LockInstance unsupported = newLockInstance("unsupported-type", "UNKNOWN_LOCK_TYPE");
        LockInstance missingKey = new LockInstance(null, 30000L, LockConstants.NACOS_LOCK_TYPE);
        
        assertThrows(NacosException.class, () -> lockService.lock(unsupported));
        assertThrows(NacosException.class, () -> lockService.lock(missingKey));
        assertThrows(NullPointerException.class, () -> lockService.lock(null));
    }
    
    private LockInstance newLockInstance(String scenario, String lockType) {
        return new LockInstance("java-sdk-it-lock-" + scenario + "-" + randomServiceName("lock"),
                30000L, lockType);
    }
}
