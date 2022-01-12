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

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class ExecutorFactoryTest {
    
    private final NameThreadFactory threadFactory = new NameThreadFactory("test");
    
    @Test
    public void test() {
        ExecutorService executorService;
        ThreadPoolExecutor threadPoolExecutor;
        
        executorService = ExecutorFactory.newSingleExecutorService();
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(1, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newFixedExecutorService(10);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(10, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newSingleExecutorService(threadFactory);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(1, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newFixedExecutorService(10, threadFactory);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(10, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
    
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
        
        executorService = ExecutorFactory.newSingleScheduledExecutorService(threadFactory);
        Assert.assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        Assert.assertEquals(1, scheduledThreadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        executorService = ExecutorFactory.newScheduledExecutorService(10, threadFactory);
        Assert.assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        Assert.assertEquals(10, scheduledThreadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        
        threadPoolExecutor = ExecutorFactory.newCustomerThreadExecutor(10, 20, 1000, threadFactory);
        Assert.assertEquals(10, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(20, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
    }
    
    @Test
    public void testManaged() {
        String testGroup = "test";
        ExecutorService executorService;
        ThreadPoolExecutor threadPoolExecutor;
        ThreadPoolManager manager = ExecutorFactory.Managed.getThreadPoolManager();
        final Map<String, Map<String, Set<ExecutorService>>> resourcesManager = manager.getResourcesManager();
        
        executorService = ExecutorFactory.Managed.newSingleExecutorService(testGroup);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(1, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(1, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newFixedExecutorService(testGroup, 10);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(10, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertNotEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(2, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newSingleExecutorService(testGroup, threadFactory);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(1, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(1, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(3, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newFixedExecutorService(testGroup, 10, threadFactory);
        Assert.assertTrue(executorService instanceof ThreadPoolExecutor);
        threadPoolExecutor = (ThreadPoolExecutor) executorService;
        Assert.assertEquals(10, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(10, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(4, resourcesManager.get("nacos").get(testGroup).size());
    
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
        
        executorService = ExecutorFactory.Managed.newSingleScheduledExecutorService(testGroup, threadFactory);
        Assert.assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        Assert.assertEquals(1, scheduledThreadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(5, resourcesManager.get("nacos").get(testGroup).size());
        
        executorService = ExecutorFactory.Managed.newScheduledExecutorService(testGroup, 10, threadFactory);
        Assert.assertTrue(executorService instanceof ScheduledThreadPoolExecutor);
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        Assert.assertEquals(10, scheduledThreadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(Integer.MAX_VALUE, scheduledThreadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(6, resourcesManager.get("nacos").get(testGroup).size());
        
        threadPoolExecutor = ExecutorFactory.Managed.newCustomerThreadExecutor(testGroup, 10, 20, 1000, threadFactory);
        Assert.assertEquals(10, threadPoolExecutor.getCorePoolSize());
        Assert.assertEquals(20, threadPoolExecutor.getMaximumPoolSize());
        Assert.assertEquals(threadFactory, threadPoolExecutor.getThreadFactory());
        Assert.assertEquals(7, resourcesManager.get("nacos").get(testGroup).size());
    }
}
