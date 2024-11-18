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

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosDelayTaskExecuteEngineTest {
    
    private NacosDelayTaskExecuteEngine nacosDelayTaskExecuteEngine;
    
    @Mock
    private NacosTaskProcessor taskProcessor;
    
    @Mock
    private NacosTaskProcessor testTaskProcessor;
    
    private AbstractDelayTask abstractTask;
    
    @BeforeEach
    void setUp() throws Exception {
        nacosDelayTaskExecuteEngine = new NacosDelayTaskExecuteEngine(NacosDelayTaskExecuteEngineTest.class.getName());
        nacosDelayTaskExecuteEngine.setDefaultTaskProcessor(taskProcessor);
        abstractTask = new AbstractDelayTask() {
            @Override
            public void merge(AbstractDelayTask task) {
            }
        };
    }
    
    @AfterEach
    void tearDown() throws Exception {
        nacosDelayTaskExecuteEngine.shutdown();
    }
    
    @Test
    void testSize() {
        assertEquals(0, nacosDelayTaskExecuteEngine.size());
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        assertEquals(1, nacosDelayTaskExecuteEngine.size());
        nacosDelayTaskExecuteEngine.removeTask("test");
        assertEquals(0, nacosDelayTaskExecuteEngine.size());
    }
    
    @Test
    void testIsEmpty() {
        assertTrue(nacosDelayTaskExecuteEngine.isEmpty());
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        assertFalse(nacosDelayTaskExecuteEngine.isEmpty());
        nacosDelayTaskExecuteEngine.removeTask("test");
        assertTrue(nacosDelayTaskExecuteEngine.isEmpty());
    }
    
    @Test
    void testAddProcessor() throws InterruptedException {
        when(testTaskProcessor.process(abstractTask)).thenReturn(true);
        nacosDelayTaskExecuteEngine.addProcessor("test", testTaskProcessor);
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor).process(abstractTask);
        verify(taskProcessor, never()).process(abstractTask);
        assertEquals(1, nacosDelayTaskExecuteEngine.getAllProcessorKey().size());
    }
    
    @Test
    void testRemoveProcessor() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(true);
        nacosDelayTaskExecuteEngine.addProcessor("test", testTaskProcessor);
        nacosDelayTaskExecuteEngine.removeProcessor("test");
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor, never()).process(abstractTask);
        verify(taskProcessor).process(abstractTask);
    }
    
    @Test
    void testRetryTaskAfterFail() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(false, true);
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(300);
        verify(taskProcessor, new Times(2)).process(abstractTask);
    }
    
    @Test
    void testProcessorWithException() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenThrow(new RuntimeException("test"));
        nacosDelayTaskExecuteEngine.addProcessor("test", testTaskProcessor);
        nacosDelayTaskExecuteEngine.removeProcessor("test");
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(200);
        assertEquals(1, nacosDelayTaskExecuteEngine.size());
    }
    
    @Test
    void testTaskShouldNotExecute() throws InterruptedException {
        nacosDelayTaskExecuteEngine.addProcessor("test", testTaskProcessor);
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        abstractTask.setTaskInterval(10000L);
        abstractTask.setLastProcessTime(System.currentTimeMillis());
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor, never()).process(abstractTask);
        assertEquals(1, nacosDelayTaskExecuteEngine.size());
    }
    
    @Test
    void testTaskMerge() {
        nacosDelayTaskExecuteEngine.addProcessor("test", testTaskProcessor);
        nacosDelayTaskExecuteEngine.addTask("test", abstractTask);
        nacosDelayTaskExecuteEngine.addTask("test", new AbstractDelayTask() {
            @Override
            public void merge(AbstractDelayTask task) {
                setLastProcessTime(task.getLastProcessTime());
                setTaskInterval(task.getTaskInterval());
            }
        });
        assertEquals(1, nacosDelayTaskExecuteEngine.size());
    }
}
