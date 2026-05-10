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

package com.alibaba.nacos.core.distributed.distro.task;

import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTaskExecuteEngine;
import com.alibaba.nacos.core.distributed.distro.task.execute.DistroExecuteTaskExecuteEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link DistroTaskEngineHolder} unit test.
 */
@ExtendWith(MockitoExtension.class)
class DistroTaskEngineHolderTest {
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    private DistroTaskEngineHolder holder;
    
    @BeforeEach
    void setUp() {
        holder = new DistroTaskEngineHolder(distroComponentHolder);
    }
    
    @Test
    void testGetDelayTaskExecuteEngine() {
        DistroDelayTaskExecuteEngine engine = holder.getDelayTaskExecuteEngine();
        assertNotNull(engine);
        assertSame(engine, holder.getDelayTaskExecuteEngine());
    }
    
    @Test
    void testGetExecuteWorkersManager() {
        DistroExecuteTaskExecuteEngine manager = holder.getExecuteWorkersManager();
        assertNotNull(manager);
        assertSame(manager, holder.getExecuteWorkersManager());
    }
    
    @Test
    void testRegisterNacosTaskProcessor() {
        NacosTaskProcessor processor = task -> true;
        holder.registerNacosTaskProcessor("testKey", processor);
        assertNotNull(holder.getDelayTaskExecuteEngine().getProcessor("testKey"));
    }
    
    @Test
    void testDestroy() throws Exception {
        holder.destroy();
    }
}
