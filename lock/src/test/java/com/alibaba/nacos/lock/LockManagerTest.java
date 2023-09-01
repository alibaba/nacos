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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.mutex.ClientAtomicLock;
import com.alibaba.nacos.lock.factory.ClientLockFactory;
import com.alibaba.nacos.lock.factory.LockFactory;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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
        Assert.assertThrows(NacosRuntimeException.class, () -> {
            lockManager.getMutexLock(emptyType, "key");
        });
    }
    
    @Test
    public void testLockFactory() throws NoSuchFieldException, IllegalAccessException {
        Field factoryMap = NacosLockManager.class.getDeclaredField("factoryMap");
        Map<String, LockFactory> map = (Map<String, LockFactory>) factoryMap.get(lockManager);
        Assert.assertEquals(map.size(), 2);
    }
    
    @Test
    public void testClientLockFactory() {
        AtomicLockService key = lockManager.getMutexLock(ClientLockFactory.TYPE, "key");
        Assert.assertEquals(key.getClass(), ClientAtomicLock.class);
        Assert.assertEquals(key.getKey(), "key");
        
        LockInstance lockInstance = new ClientLockFactory.ClinetLockInstance();
        lockInstance.setParams(new HashMap() {
            {
                put("nacosClientId", "123456");
            }
        });
        
        Assert.assertTrue(key.tryLock(lockInstance));
        Assert.assertTrue(key.tryLock(lockInstance));
        Assert.assertTrue(key.unLock(lockInstance));
    }
}