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

package com.alibaba.nacos.core.distributed.distro.task.execute;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroFailedTaskHandler;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DistroSyncDeleteTask} unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistroSyncDeleteTaskTest {
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    @Mock
    private DistroTransportAgent distroTransportAgent;
    
    private DistroKey distroKey;
    
    @BeforeEach
    void setUp() {
        distroKey = new DistroKey("key", "type", "1.1.1.1:8848");
        when(distroComponentHolder.findTransportAgent("type")).thenReturn(distroTransportAgent);
    }
    
    @Test
    void testGetDataOperation() {
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        assertEquals(DataOperation.DELETE, task.getDataOperation());
    }
    
    @Test
    void testDoExecute() {
        when(distroTransportAgent.syncData(
            any(com.alibaba.nacos.core.distributed.distro.entity.DistroData.class),
            eq("1.1.1.1:8848"))).thenReturn(true);
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        assertTrue(task.doExecute());
        verify(distroTransportAgent).syncData(
            any(com.alibaba.nacos.core.distributed.distro.entity.DistroData.class),
            eq("1.1.1.1:8848"));
    }
    
    @Test
    void testDoExecuteWithCallback() {
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        task.doExecuteWithCallback(new DistroCallback() {
            
            @Override
            public void onSuccess() {
            }
            
            @Override
            public void onFailed(Throwable throwable) {
            }
        });
        verify(distroTransportAgent).syncData(
            any(com.alibaba.nacos.core.distributed.distro.entity.DistroData.class),
            eq("1.1.1.1:8848"), any(DistroCallback.class));
    }
    
    @Test
    void testToString() {
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        assertTrue(task.toString().contains("DistroSyncDeleteTask"));
    }
    
    @Test
    void testRunWhenTransportAgentNull() {
        when(distroComponentHolder.findTransportAgent("type")).thenReturn(null);
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        task.run();
    }
    
    @Test
    void testRunWithCallbackTransport() {
        when(distroTransportAgent.supportCallbackTransport()).thenReturn(true);
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        task.run();
        verify(distroTransportAgent).syncData(
            any(com.alibaba.nacos.core.distributed.distro.entity.DistroData.class),
            eq("1.1.1.1:8848"), any(DistroCallback.class));
    }
    
    @Test
    void testRunWhenDoExecuteReturnsFalse() {
        when(distroTransportAgent.syncData(
            any(com.alibaba.nacos.core.distributed.distro.entity.DistroData.class),
            eq("1.1.1.1:8848"))).thenReturn(false);
        DistroFailedTaskHandler handler = org.mockito.Mockito.mock(DistroFailedTaskHandler.class);
        when(distroComponentHolder.findFailedTaskHandler("type")).thenReturn(handler);
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        task.run();
        verify(handler).retry(distroKey, DataOperation.DELETE);
    }
    
    @Test
    void testRunWhenDoExecuteThrows() {
        when(distroTransportAgent.syncData(
            any(com.alibaba.nacos.core.distributed.distro.entity.DistroData.class),
            eq("1.1.1.1:8848"))).thenThrow(new RuntimeException("sync fail"));
        DistroFailedTaskHandler handler = org.mockito.Mockito.mock(DistroFailedTaskHandler.class);
        when(distroComponentHolder.findFailedTaskHandler("type")).thenReturn(handler);
        DistroSyncDeleteTask task = new DistroSyncDeleteTask(distroKey, distroComponentHolder);
        task.run();
        verify(handler).retry(distroKey, DataOperation.DELETE);
    }
}
