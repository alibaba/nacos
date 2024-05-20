/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.executor;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorFactoryTest {
    
    private final NameThreadFactory threadFactory = new NameThreadFactory("test");
    
    @Test
    void test() {
        ExecutorService executorService;
        ThreadPoolExecutor threadPoolExecutor;
        
        executorService = ExecutorFactory.newSingleExecutorService();
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(1, threadPoolExecutor.getCorePoolSize());
        assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newFixedExecutorService(10);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(10, threadPoolExecutor.getCorePoolSize());
        assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newSingleExecutorService(threadFactory);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(1, threadPoolExecutor.getCorePoolSize());
        assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newFixedExecutorService(10, threadFactory);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(10, threadPoolExecutor.getCorePoolSize());
        assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
        
        executorService = ExecutorFactory.newSingleScheduledExecutorService(threadFactory);
        assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        assertEquals(1, scheduledThreadPoolExecutor.getCorePoolSize());
        assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newScheduledExecutorService(10, threadFactory);
        assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        assertEquals(10, scheduledThreadPoolExecutor.getCorePoolSize());
        assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        threadPoolExecutor = ExecutorFactory.newCustomerThreadExecutor(10, 20, 1000, threadFactory);
        assertEquals(10, threadPoolExecutor.getCorePoolSize());
        assertEquals(20, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
    }
    
    @Test
    void testManaged() {
        String testGroup = "test";
        ExecutorService executorService;
        ThreadPoolExecutor threadPoolExecutor;
        ThreadPoolManager manager = ExecutorFactory.Managed.getThreadPoolManager();
        final Map<String, Map<String, Set<ExecutorService>>> resourcesManager = manager.getResourcesManager();
        
        executorService = ExecutorFactory.Managed.newSingleExecutorService(testGroup);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(1, threadPoolExecutor.getCorePoolSize());
        assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(1, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newFixedExecutorService(testGroup, 10);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(10, threadPoolExecutor.getCorePoolSize());
        assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(2, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newSingleExecutorService(testGroup, threadFactory);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(1, threadPoolExecutor.getCorePoolSize());
        assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(3, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newFixedExecutorService(testGroup, 10, threadFactory);
        assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        assertEquals(10, threadPoolExecutor.getCorePoolSize());
        assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(4, resourcesManager.get("nacos").get(testGroup).size());
        
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
        
        executorService = ExecutorFactory.Managed.newSingleScheduledExecutorService(testGroup, threadFactory);
        assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        assertEquals(1, scheduledThreadPoolExecutor.getCorePoolSize());
        assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(5, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newScheduledExecutorService(testGroup, 10, threadFactory);
        assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        assertEquals(10, scheduledThreadPoolExecutor.getCorePoolSize());
        assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(6, resourcesManager.get("nacos").get(testGroup).size());
        
        threadPoolExecutor = ExecutorFactory.Managed.newCustomerThreadExecutor(testGroup, 10, 20, 1000, threadFactory);
        assertEquals(10, threadPoolExecutor.getCorePoolSize());
        assertEquals(20, threadPoolExecutor.getMaximumPoolSize());
        assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        assertEquals(7, resourcesManager.get("nacos").get(testGroup).size());
    }
}
