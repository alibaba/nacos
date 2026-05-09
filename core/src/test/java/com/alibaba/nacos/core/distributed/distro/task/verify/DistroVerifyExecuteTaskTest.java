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

package com.alibaba.nacos.core.distributed.distro.task.verify;

import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DistroVerifyExecuteTask} unit test.
 */
@ExtendWith(MockitoExtension.class)
class DistroVerifyExecuteTaskTest {
    
    @Mock
    private DistroTransportAgent transportAgent;
    
    private DistroVerifyExecuteTask task;
    
    @BeforeEach
    void setUp() {
        DistroData data = new DistroData(new DistroKey("k", "type"), new byte[0]);
        task = new DistroVerifyExecuteTask(transportAgent, Collections.singletonList(data),
            "1.1.1.1:8848", "type");
    }
    
    @Test
    void testRunWithSyncVerifyData() {
        when(transportAgent.supportCallbackTransport()).thenReturn(false);
        when(transportAgent.syncVerifyData(any(DistroData.class), eq("1.1.1.1:8848")))
            .thenReturn(true);
        task.run();
        verify(transportAgent).syncVerifyData(any(DistroData.class), eq("1.1.1.1:8848"));
    }
    
    @Test
    void testRunWithCallbackTransport() {
        when(transportAgent.supportCallbackTransport()).thenReturn(true);
        task.run();
        verify(transportAgent).syncVerifyData(any(DistroData.class), eq("1.1.1.1:8848"), any());
    }
    
    @Test
    void testRunWithCallbackTransportInvokesOnSuccess() {
        when(transportAgent.supportCallbackTransport()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            com.alibaba.nacos.core.distributed.distro.component.DistroCallback cb =
                invocation.getArgument(2);
            cb.onSuccess();
            return null;
        }).when(transportAgent).syncVerifyData(any(DistroData.class), eq("1.1.1.1:8848"), any());
        task.run();
    }
    
    @Test
    void testRunWithCallbackTransportInvokesOnFailed() {
        when(transportAgent.supportCallbackTransport()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            com.alibaba.nacos.core.distributed.distro.component.DistroCallback cb =
                invocation.getArgument(2);
            cb.onFailed(new RuntimeException("verify fail"));
            return null;
        }).when(transportAgent).syncVerifyData(any(DistroData.class), eq("1.1.1.1:8848"), any());
        task.run();
    }
    
    @Test
    void testRunWhenSyncVerifyDataThrows() {
        when(transportAgent.supportCallbackTransport()).thenReturn(false);
        when(transportAgent.syncVerifyData(any(DistroData.class), eq("1.1.1.1:8848")))
            .thenThrow(new RuntimeException("network error"));
        task.run();
    }
}
