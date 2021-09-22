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

import java.util.concurrent.ExecutorService;

public class ThreadPoolManagerTest {
    
    @Test
    public void test() {
        ThreadPoolManager manager = ThreadPoolManager.getInstance();
        ExecutorService executor = ExecutorFactory.newSingleExecutorService();
        String namespace = "test";
        String group = "test";
        
        manager.register(namespace, group, executor);
        Assert.assertTrue(manager.getResourcesManager().containsKey(namespace));
        Assert.assertEquals(1, manager.getResourcesManager().get(namespace).get(group).size());
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.register(namespace, group, ExecutorFactory.newSingleExecutorService());
        Assert.assertEquals(2, manager.getResourcesManager().get(namespace).get(group).size());
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.destroy(namespace, group);
        Assert.assertFalse(manager.getResourcesManager().get(namespace).containsKey(group));
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.register(namespace, group, executor);
        manager.destroy(namespace);
        Assert.assertFalse(manager.getResourcesManager().containsKey(namespace));
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.register(namespace, group, executor);
        manager.deregister(namespace, group, ExecutorFactory.newSingleExecutorService());
        Assert.assertEquals(1, manager.getResourcesManager().get(namespace).get(group).size());
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.deregister(namespace, group, executor);
        Assert.assertEquals(0, manager.getResourcesManager().get(namespace).get(group).size());
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.register(namespace, group, executor);
        manager.deregister(namespace, group);
        Assert.assertFalse(manager.getResourcesManager().get(namespace).containsKey(group));
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.register(namespace, group, executor);
        manager.register(namespace, group, ExecutorFactory.newSingleExecutorService());
        ThreadPoolManager.shutdown();
        Assert.assertFalse(manager.getResourcesManager().containsKey(namespace));
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
        
        manager.destroy(namespace);
        manager.destroy(namespace, group);
        Assert.assertFalse(manager.getResourcesManager().containsKey(namespace));
        Assert.assertTrue(manager.getLockers().containsKey(namespace));
    }
}
