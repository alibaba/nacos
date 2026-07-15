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

package com.alibaba.nacos.core.distributed.distro.task.delay;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link DistroDelayTask} unit test.
 */
class DistroDelayTaskTest {
    
    private DistroKey distroKey;
    
    @BeforeEach
    void setUp() {
        distroKey = new DistroKey("resourceKey", "resourceType", "targetServer");
    }
    
    @Test
    void testConstructorWithKeyAndDelay() {
        DistroDelayTask task = new DistroDelayTask(distroKey, 1000L);
        assertEquals(distroKey, task.getDistroKey());
        assertEquals(DataOperation.CHANGE, task.getAction());
        assertEquals(1000L, task.getTaskInterval());
        assertNotNull(task.getCreateTime());
    }
    
    @Test
    void testConstructorWithKeyActionAndDelay() {
        DistroDelayTask task = new DistroDelayTask(distroKey, DataOperation.DELETE, 2000L);
        assertEquals(distroKey, task.getDistroKey());
        assertEquals(DataOperation.DELETE, task.getAction());
        assertEquals(2000L, task.getTaskInterval());
    }
    
    @Test
    void testMergeWithNonDistroDelayTask() {
        DistroDelayTask task = new DistroDelayTask(distroKey, DataOperation.CHANGE, 1000L);
        long createTime = task.getCreateTime();
        task.merge(new com.alibaba.nacos.common.task.AbstractDelayTask() {
            
            @Override
            public void merge(com.alibaba.nacos.common.task.AbstractDelayTask task) {
            }
        });
        assertEquals(DataOperation.CHANGE, task.getAction());
        assertEquals(createTime, task.getCreateTime());
    }
    
    @Test
    void testMergeWithDistroDelayTaskSameAction() {
        DistroDelayTask task = new DistroDelayTask(distroKey, DataOperation.CHANGE, 1000L);
        DistroKey otherKey = new DistroKey("k", "t", "s");
        DistroDelayTask other = new DistroDelayTask(otherKey, DataOperation.CHANGE, 500L);
        task.merge(other);
        assertEquals(DataOperation.CHANGE, task.getAction());
    }
    
    @Test
    void testMergeWithDistroDelayTaskDifferentActionOlderCreateTime() {
        DistroDelayTask task = new DistroDelayTask(distroKey, DataOperation.DELETE, 1000L);
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        DistroKey otherKey = new DistroKey("k", "t", "s");
        DistroDelayTask other = new DistroDelayTask(otherKey, DataOperation.CHANGE, 500L);
        task.merge(other);
        assertEquals(DataOperation.CHANGE, task.getAction());
    }
}
