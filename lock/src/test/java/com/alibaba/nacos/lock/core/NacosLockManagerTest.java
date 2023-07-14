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

package com.alibaba.nacos.lock.core;

import com.alibaba.nacos.lock.model.Service;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * nacos lock manager test.
 *
 * @author 985492783@qq.com
 * @description NacosLockManagerTest
 * @date 2023/7/13 19:52
 */
public class NacosLockManagerTest {
    
    private NacosLockManager nacosLockManager;
    
    @Before
    public void setUp() {
        nacosLockManager = new NacosLockManager();
    }
    
    @Test
    public void testNacosLockManager() {
        Set<String> set = new HashSet<>();
        set.add("Hello");
        set.add("Hello2");
        Service service = new Service("1.1.1.1", 8080);
        
        Service singletonService = nacosLockManager.getSingletonService(service);
        nacosLockManager.getMutexLock("Hello").tryLock(singletonService);
        nacosLockManager.getMutexLock("Hello2").tryLock(singletonService);
        Assert.assertEquals(singletonService.getKeysSet(), set);
    }
    
    @Test
    public void testNacosLockDisConnected() {
        Service service = new Service("1.1.1.1", 8080);
        
        Service singletonService = nacosLockManager.getSingletonService(service);
        nacosLockManager.getMutexLock("Hello").tryLock(singletonService);
        nacosLockManager.getMutexLock("Hello2").tryLock(singletonService);
        nacosLockManager.acquireLock("test", service);
        
        nacosLockManager.disConnected("test");
        Assert.assertEquals(singletonService.getKeysSet(), new HashSet<>());
    }
}
