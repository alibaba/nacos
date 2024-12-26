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

import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.mutex.ClientAtomicLock;
import com.alibaba.nacos.lock.exception.NacosLockException;
import com.alibaba.nacos.lock.factory.ClientLockFactory;
import com.alibaba.nacos.lock.factory.LockFactory;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lock manager test.
 *
 * @author 985492783@qq.com
 * @date 2023/8/28 13:26
 */
public class LockManagerTest {
    
    private LockManager lockManager = new NacosLockManager();
    
    @Test
    public void testLockManagerError() {
        String emptyType = "testLockFactory_lock";
        assertThrows(NacosLockException.class, () -> {
            lockManager.getMutexLock(new LockKey(emptyType, "key"));
        });
        
        assertThrows(NacosLockException.class, () -> {
            lockManager.getMutexLock(new LockKey(emptyType, null));
        });
        
        assertThrows(NacosLockException.class, () -> {
            lockManager.getMutexLock(new LockKey(null, "key"));
        });
    }
    
    @Test
    public void testLockFactory() throws NoSuchFieldException, IllegalAccessException {
        Field factoryMap = NacosLockManager.class.getDeclaredField("factoryMap");
        factoryMap.setAccessible(true);
        Map<String, LockFactory> map = (Map<String, LockFactory>) factoryMap.get(lockManager);
        assertEquals(2, map.size());
    }
    
    @Test
    public void testClientLockFactory() {
        AtomicLockService lock = lockManager.getMutexLock(new LockKey(ClientLockFactory.TYPE, "key"));
        assertEquals(ClientAtomicLock.class, lock.getClass());
        assertEquals("key", lock.getKey());
        
        LockInfo lockInfo = new ClientLockFactory.ClientLockInstance();
        lockInfo.setParams(new HashMap() {
            {
                put("nacosClientId", "123456");
            }
        });
        
        assertTrue(lock.tryLock(lockInfo));
        assertTrue(lock.tryLock(lockInfo));
        assertTrue(lock.unLock(lockInfo));
    }
}
