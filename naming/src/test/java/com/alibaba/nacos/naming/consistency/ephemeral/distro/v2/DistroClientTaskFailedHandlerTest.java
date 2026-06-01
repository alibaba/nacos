/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.DistroTaskEngineHolder;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTask;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTaskExecuteEngine;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistroClientTaskFailedHandlerTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testRetryAddsDelayTask() {
        DistroTaskEngineHolder taskEngineHolder = mock(DistroTaskEngineHolder.class);
        DistroDelayTaskExecuteEngine delayTaskExecuteEngine =
            mock(DistroDelayTaskExecuteEngine.class);
        DistroKey distroKey = new DistroKey("resource", "type", "target");
        DistroClientTaskFailedHandler handler = new DistroClientTaskFailedHandler(taskEngineHolder);
        when(taskEngineHolder.getDelayTaskExecuteEngine()).thenReturn(delayTaskExecuteEngine);
        
        handler.retry(distroKey, DataOperation.ADD);
        
        verify(delayTaskExecuteEngine).addTask(eq(distroKey), any(DistroDelayTask.class));
    }
}
