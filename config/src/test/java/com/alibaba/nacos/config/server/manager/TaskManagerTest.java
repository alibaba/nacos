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

import com.alibaba.nacos.config.server.constant.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.MockitoJUnitRunner;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TaskManagerTest {
    
    private TaskManager taskManager;
    
    @Mock
    private TaskProcessor taskProcessor;
    
    @Mock
    private TaskProcessor testTaskProcessor;
    
    private AbstractTask abstractTask;
    
    @Before
    public void setUp() {
        taskManager = new TaskManager(TaskManagerTest.class.getName());
        taskManager.setDefaultTaskProcessor(taskProcessor);
        abstractTask = new AbstractTask() {
            @Override
            public void merge(AbstractTask task) {
            }
        };
    }
    
    @After
    public void tearDown() {
        taskManager.close();
    }
    
    @Test
    public void testSize() {
        assertEquals(0, taskManager.size());
        taskManager.addTask("test", abstractTask);
        assertEquals(1, taskManager.size());
        taskManager.removeTask("test");
        assertEquals(0, taskManager.size());
    }
    
    @Test
    public void testIsEmpty() {
        assertTrue(taskManager.isEmpty());
        taskManager.addTask("test", abstractTask);
        assertFalse(taskManager.isEmpty());
        taskManager.removeTask("test");
        assertTrue(taskManager.isEmpty());
    }
    
    @Test
    public void addProcessor() throws InterruptedException {
        when(testTaskProcessor.process("test", abstractTask)).thenReturn(true);
        taskManager.addProcessor("test", testTaskProcessor);
        taskManager.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor).process("test", abstractTask);
        verify(taskProcessor, never()).process("test", abstractTask);
    }
    
    @Test
    public void removeProcessor() throws InterruptedException {
        when(taskProcessor.process("test", abstractTask)).thenReturn(true);
        taskManager.addProcessor("test", testTaskProcessor);
        taskManager.removeProcessor("test");
        taskManager.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(200);
        verify(testTaskProcessor, never()).process("test", abstractTask);
        verify(taskProcessor).process("test", abstractTask);
    }
    
    @Test
    public void retryTaskAfterFail() throws InterruptedException {
        when(taskProcessor.process("test", abstractTask)).thenReturn(false, true);
        taskManager.addTask("test", abstractTask);
        TimeUnit.MILLISECONDS.sleep(300);
        verify(taskProcessor, new Times(2)).process("test", abstractTask);
    }
    
    @Test
    public void getTaskInfos() throws InterruptedException {
        taskManager.addProcessor("test", testTaskProcessor);
        when(testTaskProcessor.process("test", abstractTask)).thenReturn(true);
        taskManager.addTask("test", abstractTask);
        assertEquals("test:Thu Jan 01 08:00:00 CST 1970" + Constants.NACOS_LINE_SEPARATOR, taskManager.getTaskInfos());
        TimeUnit.MILLISECONDS.sleep(150);
        assertEquals("test:finished" + Constants.NACOS_LINE_SEPARATOR, taskManager.getTaskInfos());
    }
    
    @Test
    public void testInit() throws Exception {
        taskManager.init();
        ObjectName oName = new ObjectName(TaskManagerTest.class.getName() + ":type=" + TaskManager.class.getSimpleName());
        assertTrue(ManagementFactory.getPlatformMBeanServer().isRegistered(oName));
    }
}
