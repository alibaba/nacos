/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * For Understand this test case, Please Read {@link com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine#addTask(Object,
 * AbstractDelayTask)}.
 *
 * @author xiweng.yy
 */
public class PushDelayTaskTest {
    
    private final Service service = Service.newService("N", "G", "S");
    
    private final String singleTargetClientId = "testClientId";
    
    private PushDelayTask pushToAllTask;
    
    private PushDelayTask singlePushTask;
    
    @Before
    public void setUp() throws Exception {
        pushToAllTask = new PushDelayTask(service, 0L);
        singlePushTask = new PushDelayTask(service, 0L, singleTargetClientId);
    }
    
    @Test
    public void testMergeAllToSingle() {
        PushDelayTask newTask = singlePushTask;
        PushDelayTask oldTask = pushToAllTask;
        newTask.merge(oldTask);
        assertTrue(newTask.isPushToAll());
        assertNull(newTask.getTargetClients());
    }
    
    @Test
    public void testMergeSingleToAll() {
        PushDelayTask newTask = pushToAllTask;
        PushDelayTask oldTask = singlePushTask;
        newTask.merge(oldTask);
        assertTrue(newTask.isPushToAll());
        assertNull(newTask.getTargetClients());
    }
    
    @Test
    public void testMergeSingleToSingle() {
        PushDelayTask oldTask = singlePushTask;
        PushDelayTask newTask = new PushDelayTask(service, 0L, "newClient");
        newTask.merge(oldTask);
        assertFalse(newTask.isPushToAll());
        assertNotNull(newTask.getTargetClients());
        assertFalse(newTask.getTargetClients().isEmpty());
        assertEquals(2, newTask.getTargetClients().size());
        assertTrue(newTask.getTargetClients().contains(singleTargetClientId));
        assertTrue(newTask.getTargetClients().contains("newClient"));
    }
    
    @Test
    public void testMergeAllToAll() throws InterruptedException {
        PushDelayTask oldTask = pushToAllTask;
        TimeUnit.MILLISECONDS.sleep(10);
        PushDelayTask newTask = new PushDelayTask(service, 0L);
        newTask.merge(oldTask);
        newTask.merge(oldTask);
        assertTrue(newTask.isPushToAll());
        assertEquals(oldTask.getLastProcessTime(), newTask.getLastProcessTime());
    }
}
