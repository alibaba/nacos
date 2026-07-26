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

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.DistroTaskEngineHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DistroDelayTaskProcessor} unit test.
 */
@ExtendWith(MockitoExtension.class)
class DistroDelayTaskProcessorTest {
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    private DistroTaskEngineHolder distroTaskEngineHolder;
    
    private DistroDelayTaskProcessor processor;
    
    @BeforeEach
    void setUp() {
        distroTaskEngineHolder = new DistroTaskEngineHolder(distroComponentHolder);
        processor = new DistroDelayTaskProcessor(distroTaskEngineHolder, distroComponentHolder);
    }
    
    @Test
    void testProcessNonDistroDelayTask() {
        assertTrue(processor.process(new NacosTask() {
            
            @Override
            public boolean shouldProcess() {
                return true;
            }
        }));
    }
    
    @Test
    void testProcessDeleteAction() {
        DistroKey key = new DistroKey("k", "type", "target");
        DistroDelayTask task = new DistroDelayTask(key, DataOperation.DELETE, 100L);
        assertTrue(processor.process(task));
    }
    
    @Test
    void testProcessChangeAction() {
        DistroKey key = new DistroKey("k", "type", "target");
        DistroDelayTask task = new DistroDelayTask(key, DataOperation.CHANGE, 100L);
        assertTrue(processor.process(task));
    }
    
    @Test
    void testProcessAddAction() {
        DistroKey key = new DistroKey("k", "type", "target");
        DistroDelayTask task = new DistroDelayTask(key, DataOperation.ADD, 100L);
        assertTrue(processor.process(task));
    }
    
    @Test
    void testProcessOtherAction() {
        DistroKey key = new DistroKey("k", "type", "target");
        DistroDelayTask task = new DistroDelayTask(key, DataOperation.QUERY, 100L);
        assertFalse(processor.process(task));
    }
}
