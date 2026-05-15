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

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
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
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
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
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
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
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
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
        AbstractNacosMcpServerListener listener2 =
            Mockito.mock(AbstractNacosMcpServerListener.class);
        McpServerListenerInvoker invoker = new McpServerListenerInvoker(listener);
        McpServerListenerInvoker invoker2 = new McpServerListenerInvoker(listener2);
        changeNotifier.registerListener("test", null, invoker);
        changeNotifier.registerListener("test", null, invoker2);
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertTrue(invokedMark.get());
        assertTrue(invoker.isInvoked());
        assertTrue(invoker2.isInvoked());
        verify(listener2).onEvent(any(NacosMcpServerEvent.class));
        
        invokedMark.set(false);
        reset(listener2);
        changeNotifier.deregisterListener("test", null, invoker2);
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
        assertTrue(invokedMark.get());
        verify(listener2, Mockito.never()).onEvent(any(NacosMcpServerEvent.class));
        
        invokedMark.set(false);
        changeNotifier.deregisterListener("test", null, invoker);
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new McpServerChangedEvent(mcpServerDetailInfo)));
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
    
    @Test
    void agentCardRegisterAndOnEvent() {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName("ag");
        detail.setVersion("1.0");
        detail.setLatestVersion(true);
        AbstractNacosAgentCardListener listener = new AbstractNacosAgentCardListener() {
            
            @Override
            public void onEvent(NacosAgentCardEvent event) {
                invokedMark.set(true);
            }
        };
        AgentCardListenerInvoker invoker = new AgentCardListenerInvoker(listener);
        // null invoker is a no-op (early return)
        changeNotifier.registerListener("ag", null, (AgentCardListenerInvoker) null);
        assertFalse(changeNotifier.isAgentCardSubscribed("ag",
            CacheKeyUtils.LATEST_VERSION));
        // real invoker is registered and triggered
        changeNotifier.registerListener("ag", null, invoker);
        assertTrue(changeNotifier.isAgentCardSubscribed("ag", CacheKeyUtils.LATEST_VERSION));
        changeNotifier.onEvent(new AgentCardChangedEvent(detail));
        assertTrue(invokedMark.get());
    }
    
    @Test
    void agentCardOnEventNoListenerEarlyReturn() {
        AgentCardDetailInfo detail = new AgentCardDetailInfo();
        detail.setName("ag-no-sub");
        detail.setVersion("1.0");
        detail.setLatestVersion(true);
        // Without a registered listener, onEvent should early-return
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new AgentCardChangedEvent(detail)));
    }
    
    @Test
    void agentCardDeregisterListener() {
        AbstractNacosAgentCardListener l =
            Mockito.mock(AbstractNacosAgentCardListener.class);
        AgentCardListenerInvoker invoker = new AgentCardListenerInvoker(l);
        // null invoker is no-op
        changeNotifier.deregisterListener("ag", null, (AgentCardListenerInvoker) null);
        // dereg before register also no-op
        changeNotifier.deregisterListener("ag", null, invoker);
        assertFalse(changeNotifier.isAgentCardSubscribed("ag",
            CacheKeyUtils.LATEST_VERSION));
        // register then dereg empties the set
        changeNotifier.registerListener("ag", null, invoker);
        changeNotifier.deregisterListener("ag", null, invoker);
        assertFalse(changeNotifier.isAgentCardSubscribed("ag",
            CacheKeyUtils.LATEST_VERSION));
    }
    
    @Test
    void promptRegisterAndOnEvent() {
        AbstractNacosPromptListener listener = new AbstractNacosPromptListener() {
            
            @Override
            public void onEvent(NacosPromptEvent event) {
                invokedMark.set(true);
            }
        };
        PromptListenerInvoker invoker = new PromptListenerInvoker(listener);
        // null invoker is no-op
        changeNotifier.registerListener("p", "1.0", null, (PromptListenerInvoker) null);
        assertFalse(changeNotifier.isPromptSubscribed("p", "1.0", null));
        changeNotifier.registerListener("p", "1.0", null, invoker);
        assertTrue(changeNotifier.isPromptSubscribed("p", "1.0", null));
        Prompt prompt = new Prompt("p", "1.0", "tpl");
        String cacheKey = CacheKeyUtils.buildPromptKey("p", "1.0", null);
        changeNotifier.onEvent(new PromptChangedEvent("p", cacheKey, prompt));
        assertTrue(invokedMark.get());
    }
    
    @Test
    void promptOnEventNoListenerEarlyReturn() {
        Prompt prompt = new Prompt("p2", "1.0", "tpl");
        String cacheKey = CacheKeyUtils.buildPromptKey("p2", "1.0", null);
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new PromptChangedEvent("p2", cacheKey, prompt)));
    }
    
    @Test
    void promptDeregisterListener() {
        AbstractNacosPromptListener l = Mockito.mock(AbstractNacosPromptListener.class);
        PromptListenerInvoker invoker = new PromptListenerInvoker(l);
        // null invoker no-op
        changeNotifier.deregisterListener("p", "1.0", null, (PromptListenerInvoker) null);
        // dereg before register no-op
        changeNotifier.deregisterListener("p", "1.0", null, invoker);
        assertFalse(changeNotifier.isPromptSubscribed("p", "1.0", null));
        changeNotifier.registerListener("p", "1.0", null, invoker);
        changeNotifier.deregisterListener("p", "1.0", null, invoker);
        assertFalse(changeNotifier.isPromptSubscribed("p", "1.0", null));
    }
    
    @Test
    void agentSpecRegisterAndOnEvent() {
        AbstractNacosAgentSpecListener listener = new AbstractNacosAgentSpecListener() {
            
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
                invokedMark.set(true);
            }
        };
        AgentSpecListenerInvoker invoker = new AgentSpecListenerInvoker(listener);
        // null invoker no-op
        changeNotifier.registerListener("spec", (AgentSpecListenerInvoker) null);
        assertFalse(changeNotifier.isAgentSpecSubscribed("spec"));
        changeNotifier.registerListener("spec", invoker);
        assertTrue(changeNotifier.isAgentSpecSubscribed("spec"));
        AgentSpec spec = new AgentSpec();
        spec.setName("spec");
        changeNotifier.onEvent(new AgentSpecChangedEvent("spec", spec));
        assertTrue(invokedMark.get());
    }
    
    @Test
    void agentSpecOnEventNoListenerEarlyReturn() {
        AgentSpec spec = new AgentSpec();
        spec.setName("spec-no");
        assertDoesNotThrow(
            () -> changeNotifier.onEvent(new AgentSpecChangedEvent("spec-no", spec)));
    }
    
    @Test
    void agentSpecDeregisterListener() {
        AbstractNacosAgentSpecListener l =
            Mockito.mock(AbstractNacosAgentSpecListener.class);
        AgentSpecListenerInvoker invoker = new AgentSpecListenerInvoker(l);
        // null invoker no-op
        changeNotifier.deregisterListener("spec", (AgentSpecListenerInvoker) null);
        // dereg before register no-op
        changeNotifier.deregisterListener("spec", invoker);
        assertFalse(changeNotifier.isAgentSpecSubscribed("spec"));
        changeNotifier.registerListener("spec", invoker);
        changeNotifier.deregisterListener("spec", invoker);
        assertFalse(changeNotifier.isAgentSpecSubscribed("spec"));
    }
    
    @Test
    void subscribeTypesContainsAllFour() {
        assertTrue(changeNotifier.subscribeTypes()
            .contains(McpServerChangedEvent.class));
        assertTrue(changeNotifier.subscribeTypes()
            .contains(AgentCardChangedEvent.class));
        assertTrue(changeNotifier.subscribeTypes()
            .contains(PromptChangedEvent.class));
        assertTrue(changeNotifier.subscribeTypes()
            .contains(AgentSpecChangedEvent.class));
    }
}
