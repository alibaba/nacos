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
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
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
 * {@link DistroSyncChangeTask} unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistroSyncChangeTaskTest {
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    @Mock
    private DistroDataStorage distroDataStorage;
    
    @Mock
    private DistroTransportAgent distroTransportAgent;
    
    private DistroKey distroKey;
    
    @BeforeEach
    void setUp() {
        distroKey = new DistroKey("key", "type", "1.1.1.1:8848");
        when(distroComponentHolder.findDataStorage("type")).thenReturn(distroDataStorage);
        when(distroComponentHolder.findTransportAgent("type")).thenReturn(distroTransportAgent);
    }
    
    @Test
    void testGetDataOperation() {
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        assertEquals(DataOperation.CHANGE, task.getDataOperation());
    }
    
    @Test
    void testDoExecuteWhenDataNull() {
        when(distroDataStorage.getDistroData(distroKey)).thenReturn(null);
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        assertTrue(task.doExecute());
    }
    
    @Test
    void testDoExecuteWhenDataPresent() {
        DistroData data = new DistroData(distroKey, new byte[] {1});
        when(distroDataStorage.getDistroData(distroKey)).thenReturn(data);
        when(distroTransportAgent.syncData(any(DistroData.class), eq("1.1.1.1:8848")))
            .thenReturn(true);
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        assertTrue(task.doExecute());
        verify(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"));
    }
    
    @Test
    void testDoExecuteWithCallbackWhenDataNull() {
        when(distroDataStorage.getDistroData(distroKey)).thenReturn(null);
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.doExecuteWithCallback(new DistroCallback() {
            
            @Override
            public void onSuccess() {
            }
            
            @Override
            public void onFailed(Throwable throwable) {
            }
        });
    }
    
    @Test
    void testDoExecuteWithCallbackWhenDataPresent() {
        DistroData data = new DistroData(distroKey, new byte[] {1});
        when(distroDataStorage.getDistroData(distroKey)).thenReturn(data);
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.doExecuteWithCallback(new DistroCallback() {
            
            @Override
            public void onSuccess() {
            }
            
            @Override
            public void onFailed(Throwable throwable) {
            }
        });
        verify(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"),
            any(DistroCallback.class));
    }
    
    @Test
    void testToString() {
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        assertTrue(task.toString().contains("DistroSyncChangeTask"));
        assertTrue(task.toString().contains("key"));
    }
    
    @Test
    void testRunWhenTransportAgentNull() {
        when(distroComponentHolder.findTransportAgent("type")).thenReturn(null);
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.run();
    }
    
    @Test
    void testRunWithCallbackTransport() {
        when(distroTransportAgent.supportCallbackTransport()).thenReturn(true);
        when(distroDataStorage.getDistroData(distroKey))
            .thenReturn(new DistroData(distroKey, new byte[] {1}));
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.run();
        verify(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"),
            any(DistroCallback.class));
    }
    
    @Test
    void testRunWithCallbackTransportCallbackOnSuccess() {
        when(distroTransportAgent.supportCallbackTransport()).thenReturn(true);
        when(distroDataStorage.getDistroData(distroKey))
            .thenReturn(new DistroData(distroKey, new byte[] {1}));
        org.mockito.Mockito.doAnswer(inv -> {
            DistroCallback cb = inv.getArgument(2);
            cb.onSuccess();
            return null;
        }).when(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"),
            any(DistroCallback.class));
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.run();
    }
    
    @Test
    void testRunWithCallbackTransportCallbackOnFailed() {
        when(distroTransportAgent.supportCallbackTransport()).thenReturn(true);
        when(distroDataStorage.getDistroData(distroKey))
            .thenReturn(new DistroData(distroKey, new byte[] {1}));
        com.alibaba.nacos.core.distributed.distro.component.DistroFailedTaskHandler handler =
            org.mockito.Mockito.mock(
                com.alibaba.nacos.core.distributed.distro.component.DistroFailedTaskHandler.class);
        when(distroComponentHolder.findFailedTaskHandler("type")).thenReturn(handler);
        org.mockito.Mockito.doAnswer(inv -> {
            DistroCallback cb = inv.getArgument(2);
            cb.onFailed(new RuntimeException("fail"));
            return null;
        }).when(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"),
            any(DistroCallback.class));
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.run();
        verify(handler).retry(distroKey, DataOperation.CHANGE);
    }
    
    @Test
    void testRunWithCallbackOnFailedWhenNoFailedTaskHandler() {
        when(distroTransportAgent.supportCallbackTransport()).thenReturn(true);
        when(distroDataStorage.getDistroData(distroKey))
            .thenReturn(new DistroData(distroKey, new byte[] {1}));
        when(distroComponentHolder.findFailedTaskHandler("type")).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            DistroCallback cb = inv.getArgument(2);
            cb.onFailed(new RuntimeException("fail"));
            return null;
        }).when(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"),
            any(DistroCallback.class));
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.run();
    }
    
    @Test
    void testRunWithCallbackOnFailedWithoutThrowable() {
        when(distroTransportAgent.supportCallbackTransport()).thenReturn(true);
        when(distroDataStorage.getDistroData(distroKey))
            .thenReturn(new DistroData(distroKey, new byte[] {1}));
        when(distroComponentHolder.findFailedTaskHandler("type")).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            DistroCallback cb = inv.getArgument(2);
            cb.onFailed(null);
            return null;
        }).when(distroTransportAgent).syncData(any(DistroData.class), eq("1.1.1.1:8848"),
            any(DistroCallback.class));
        DistroSyncChangeTask task = new DistroSyncChangeTask(distroKey, distroComponentHolder);
        task.run();
    }
}
