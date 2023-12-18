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

package com.alibaba.nacos.common.task.engine;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class NacosExecuteTaskExecuteEngineTest {
    
    private NacosExecuteTaskExecuteEngine executeTaskExecuteEngine;
    
    @Mock
    NacosTaskProcessor taskProcessor;
    
    String cachedProcessor;
    
    @Before
    public void setUp() {
        cachedProcessor = System.getProperty("nacos.common.processors");
        System.setProperty("nacos.common.processors", "1");
        executeTaskExecuteEngine = new NacosExecuteTaskExecuteEngine("TEST", null);
    }
    
    @After
    public void tearDown() throws NacosException {
        System.setProperty("nacos.common.processors", null == cachedProcessor ? "" : cachedProcessor);
        executeTaskExecuteEngine.shutdown();
    }
    
    @Mock
    private AbstractExecuteTask task;
    
    @Test
    public void testAddTask() throws InterruptedException {
        executeTaskExecuteEngine.addTask("test", task);
        TimeUnit.SECONDS.sleep(1);
        verify(task).run();
        assertTrue(executeTaskExecuteEngine.isEmpty());
        assertEquals(0, executeTaskExecuteEngine.size());
    }
    
    @Test
    public void testAddTaskByProcessor() throws InterruptedException {
        executeTaskExecuteEngine.addProcessor("test", taskProcessor);
        executeTaskExecuteEngine.addTask("test", task);
        verify(taskProcessor).process(task);
        assertTrue(executeTaskExecuteEngine.isEmpty());
        assertEquals(0, executeTaskExecuteEngine.size());
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveTask() {
        executeTaskExecuteEngine.removeTask(task);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllTaskKeys() {
        executeTaskExecuteEngine.getAllTaskKeys();
    }
    
    @Test
    public void testWorkersStatus() {
        assertEquals("TEST_0%1, pending tasks: 0\n", executeTaskExecuteEngine.workersStatus());
    }
}
