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
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class AiChangeNotifierTest {
    
    AiChangeNotifier changeNotifier;
    
    private AtomicBoolean invokedMark;
    
    private McpServerDetailInfo mcpServerDetailInfo;
    
    @BeforeEach
    void setUp() {
        changeNotifier = new AiChangeNotifier();
        invokedMark = new AtomicBoolean(false);
        mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("test");
        mcpServerDetailInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerDetailInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerDetailInfo.getVersionDetail().setIs_latest(true);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void onEventWithoutListener() {
        assertDoesNotThrow(() -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
    }
    
    @Test
    void onEvent() {
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
                invokedMark.set(true);
            }
        };
        McpServerListenerInvoker invoker = new McpServerListenerInvoker(listener);
        changeNotifier.registerListener("test", null, invoker);
        assertDoesNotThrow(() -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertTrue(invokedMark.get());
        assertTrue(invoker.isInvoked());
    }
    
    @Test
    void onEventNotLatestVersion() {
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
                invokedMark.set(true);
            }
        };
        McpServerListenerInvoker invoker = new McpServerListenerInvoker(listener);
        changeNotifier.registerListener("test", "1.0.0", invoker);
        mcpServerDetailInfo.getVersionDetail().setIs_latest(false);
        assertDoesNotThrow(() -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertTrue(invokedMark.get());
        assertTrue(invoker.isInvoked());
    }
    
    @Test
    void deregisterListener() {
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
                invokedMark.set(true);
            }
        };
        AbstractNacosMcpServerListener listener2 = Mockito.mock(AbstractNacosMcpServerListener.class);
        McpServerListenerInvoker invoker = new McpServerListenerInvoker(listener);
        McpServerListenerInvoker invoker2 = new McpServerListenerInvoker(listener2);
        changeNotifier.registerListener("test", null, invoker);
        changeNotifier.registerListener("test", null, invoker2);
        assertDoesNotThrow(() -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertTrue(invokedMark.get());
        assertTrue(invoker.isInvoked());
        assertTrue(invoker2.isInvoked());
        verify(listener2).onEvent(any(NacosMcpServerEvent.class));
        
        invokedMark.set(false);
        reset(listener2);
        changeNotifier.deregisterListener("test", null, invoker2);
        assertDoesNotThrow(() -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertTrue(invokedMark.get());
        verify(listener2, Mockito.never()).onEvent(any(NacosMcpServerEvent.class));
        
        invokedMark.set(false);
        changeNotifier.deregisterListener("test", null, invoker);
        assertDoesNotThrow(() -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertFalse(invokedMark.get());
    }
    
    @Test
    void registerNullListener() {
        changeNotifier.registerListener("test", null, (McpServerListenerInvoker) null);
        assertFalse(changeNotifier.isMcpServerSubscribed("test", ""));
    }
    
    @Test
    void deregisterNullListener() {
        changeNotifier.deregisterListener("test", null, (McpServerListenerInvoker) null);
        assertFalse(changeNotifier.isMcpServerSubscribed("test", ""));
    }
    
    @Test
    void deregisterNonExistedListener() {
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
                invokedMark.set(true);
            }
        };
        McpServerListenerInvoker invoker = new McpServerListenerInvoker(listener);
        changeNotifier.deregisterListener("test", null, invoker);
        assertFalse(changeNotifier.isMcpServerSubscribed("test", ""));
    }
}