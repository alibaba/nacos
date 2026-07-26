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

package com.alibaba.nacos.config.server.manager;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskManagerTest {
    
    private TaskManager taskManager;
    
    @Mock
    private NacosTaskProcessor taskProcessor;
    
    @Mock
    private NacosTaskProcessor testTaskProcessor;
    
    private AbstractDelayTask abstractTask;
    
    @BeforeEach
    void setUp() {
        taskManager = new TaskManager(TaskManagerTest.class.getName());
        taskManager.setDefaultTaskProcessor(taskProcessor);
        abstractTask = new AbstractDelayTask() {
            
            @Override
            public void merge(AbstractDelayTask task) {
            }
        };
    }
    
    @AfterEach
    void tearDown() {
        taskManager.close();
    }
    
    @Test
    void testSize() {
        assertEquals(0, taskManager.size());
        taskManager.addTask("test", abstractTask);
        assertEquals(1, taskManager.size());
        taskManager.removeTask("test");
        assertEquals(0, taskManager.size());
    }
    
    @Test
    void testIsEmpty() {
        assertTrue(taskManager.isEmpty());
        taskManager.addTask("test", abstractTask);
        assertFalse(taskManager.isEmpty());
        taskManager.removeTask("test");
        assertTrue(taskManager.isEmpty());
    }
    
    @Test
    void testAddProcessor() throws InterruptedException {
        when(testTaskProcessor.process(abstractTask)).thenReturn(true);
        taskManager.addProcessor("test", testTaskProcessor);
        taskManager.addTask("test", abstractTask);
        verify(testTaskProcessor, timeout(2000)).process(abstractTask);
        verify(taskProcessor, never()).process(abstractTask);
    }
    
    @Test
    void testRemoveProcessor() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(true);
        taskManager.addProcessor("test", testTaskProcessor);
        taskManager.removeProcessor("test");
        taskManager.addTask("test", abstractTask);
        verify(taskProcessor, timeout(2000)).process(abstractTask);
        verify(testTaskProcessor, never()).process(abstractTask);
    }
    
    @Test
    void testRetryTaskAfterFail() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(false, true);
        taskManager.addTask("test", abstractTask);
        verify(taskProcessor, timeout(2000).times(2)).process(abstractTask);
    }
    
    @Test
    void testAwaitWhenEmpty() throws InterruptedException {
        assertTrue(taskManager.isEmpty());
        taskManager.await();
    }
    
    @Test
    void testAwaitWaitsForProcessing() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenAnswer(invocation -> {
            TimeUnit.MILLISECONDS.sleep(100);
            return true;
        });
        taskManager.addTask("awaitTest", abstractTask);
        
        taskManager.await();
        
        assertTrue(taskManager.isEmpty());
    }
    
    @Test
    void testAwaitWithTimeoutWhenEmpty() throws InterruptedException {
        assertTrue(taskManager.isEmpty());
        boolean result = taskManager.await(100, TimeUnit.MILLISECONDS);
        assertFalse(result);
    }
    
    @Test
    void testAwaitWithTimeoutWaitsForProcessing() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(true);
        taskManager.addTask("awaitTest", abstractTask);
        boolean result = taskManager.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(taskManager.isEmpty());
    }
    
    @Test
    void testProcessTasksSignalsCondition() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(true);
        taskManager.addTask("signalTest", abstractTask);
        taskManager.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(taskManager.isEmpty());
    }
    
}
