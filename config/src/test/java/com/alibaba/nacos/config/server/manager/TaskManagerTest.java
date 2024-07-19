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
import com.alibaba.nacos.config.server.constant.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
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
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor).process(abstractTask);
        verify(taskProcessor, never()).process(abstractTask);
    }
    
    @Test
    void testRemoveProcessor() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(true);
        taskManager.addProcessor("test", testTaskProcessor);
        taskManager.removeProcessor("test");
        taskManager.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor, never()).process(abstractTask);
        verify(taskProcessor).process(abstractTask);
    }
    
    @Test
    void testRetryTaskAfterFail() throws InterruptedException {
        when(taskProcessor.process(abstractTask)).thenReturn(false, true);
        taskManager.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(300);
        verify(taskProcessor, new Times(2)).process(abstractTask);
    }
    
    @Test
    void testGetTaskInfos() throws InterruptedException {
        taskManager.addProcessor("test", testTaskProcessor);
        when(testTaskProcessor.process(abstractTask)).thenReturn(true);
        taskManager.addTask("test", abstractTask);
        assertEquals("test:" + new Date(0) + Constants.NACOS_LINE_SEPARATOR, taskManager.getTaskInfos());
        TimeUnit.MILLISECONDS.sleep(150);
        assertEquals("test:finished" + Constants.NACOS_LINE_SEPARATOR, taskManager.getTaskInfos());
    }
    
    @Test
    void testInit() throws Exception {
        taskManager.init();
        ObjectName oName = new ObjectName(TaskManagerTest.class.getName() + ":type=" + TaskManager.class.getSimpleName());
        assertTrue(ManagementFactory.getPlatformMBeanServer().isRegistered(oName));
    }
}
