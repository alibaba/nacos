/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerListenerInvokerTest {
    
    @Mock
    Executor executor;
    
    @Mock
    AbstractNacosMcpServerListener listener;
    
    McpServerListenerInvoker invoker;
    
    @BeforeEach
    void setUp() {
        invoker = new McpServerListenerInvoker(listener);
    }
    
    @Test
    void invokerByExecutor() {
        when(listener.getExecutor()).thenReturn(executor);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        invoker.invoke(new NacosMcpServerEvent(new McpServerDetailInfo()));
        verify(executor).execute(any(Runnable.class));
        verify(listener).onEvent(any(NacosMcpServerEvent.class));
    }
    
    @Test
    void testEquals() {
        assertEquals(invoker, invoker);
        assertNotEquals(invoker, null);
        assertNotEquals(invoker, new Object());
        assertEquals(invoker, new McpServerListenerInvoker(listener));
        assertNotEquals(invoker, new McpServerListenerInvoker(new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
            }
        }));
    }
}