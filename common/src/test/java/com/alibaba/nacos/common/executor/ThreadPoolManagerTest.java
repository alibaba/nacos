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

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolManagerTest {
    
    @Test
    void test() {
        ThreadPoolManager manager = ThreadPoolManager.getInstance();
        ExecutorService executor = ExecutorFactory.newSingleExecutorService();
        String namespace = "test";
        String group = "test";
        
        manager.register(namespace, group, executor);
        assertTrue(manager.getResourcesManager().containsKey(namespace));
        assertEquals(1, manager.getResourcesManager().get(namespace).get(group).size());
        
        manager.register(namespace, group, ExecutorFactory.newSingleExecutorService());
        assertEquals(2, manager.getResourcesManager().get(namespace).get(group).size());
        
        manager.destroy(namespace, group);
        assertFalse(manager.getResourcesManager().get(namespace).containsKey(group));
        
        manager.register(namespace, group, executor);
        manager.destroy(namespace);
        assertFalse(manager.getResourcesManager().containsKey(namespace));
        
        manager.register(namespace, group, executor);
        manager.deregister(namespace, group, ExecutorFactory.newSingleExecutorService());
        assertEquals(1, manager.getResourcesManager().get(namespace).get(group).size());
        
        manager.deregister(namespace, group, executor);
        assertEquals(0, manager.getResourcesManager().get(namespace).get(group).size());
        
        manager.register(namespace, group, executor);
        manager.deregister(namespace, group);
        assertFalse(manager.getResourcesManager().get(namespace).containsKey(group));
        
        manager.register(namespace, group, executor);
        manager.register(namespace, group, ExecutorFactory.newSingleExecutorService());
        ThreadPoolManager.shutdown();
        assertFalse(manager.getResourcesManager().containsKey(namespace));
        
        manager.destroy(namespace);
        manager.destroy(namespace, group);
        assertFalse(manager.getResourcesManager().containsKey(namespace));
    }
    
    @Test
    void testDestroyWithNull() {
        ThreadPoolManager.getInstance().register("t", "g", ExecutorFactory.newFixedExecutorService(1));
        try {
            ThreadPoolManager.getInstance().destroy("null");
            assertTrue(ThreadPoolManager.getInstance().getResourcesManager().containsKey("t"));
            ThreadPoolManager.getInstance().destroy("null", "g");
            assertTrue(ThreadPoolManager.getInstance().getResourcesManager().containsKey("t"));
        } finally {
            ThreadPoolManager.getInstance().destroy("t", "g");
        }
    }
}
