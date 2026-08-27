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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosAgentDiscoveryCacheHolderTest {
    
    private static final String DIGEST_A =
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    
    private static final String DIGEST_B =
        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    
    @Test
    void compatibilityHolderKeepsPollingSubscriptionBehavior() throws Exception {
        AgentClientProxy proxy = mock(AgentClientProxy.class);
        ScheduledExecutorService polling = mock(ScheduledExecutorService.class);
        ExecutorService callbacks = mock(ExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        List<Runnable> pollingTasks = new ArrayList<Runnable>();
        List<Runnable> callbackTasks = new ArrayList<Runnable>();
        when(polling.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(invocation -> {
            pollingTasks.add(invocation.getArgument(0));
            return future;
        });
        doAnswer(invocation -> {
            callbackTasks.add(invocation.getArgument(0));
            return null;
        }).when(callbacks).execute(any(Runnable.class));
        when(proxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenReturn(result("2.0.0", DIGEST_B));
        NacosAgentDiscoveryCacheHolder holder = new NacosAgentDiscoveryCacheHolder("public",
            proxy, 10L, polling, callbacks);
        TestListener listener = new TestListener();
        
        assertEquals("1.0.0", holder.subscribe(reference(), null, listener).getVersion());
        pollingTasks.get(0).run();
        pollingTasks.get(1).run();
        callbackTasks.get(0).run();
        assertEquals("2.0.0", listener.last.getAgentDiscoveryResult().getVersion());
        holder.shutdown();
        verify(polling).shutdownNow();
        verify(callbacks).shutdownNow();
    }
    
    @Test
    void allCompatibilityConstructorsAndCapacityValidationRemainAvailable() {
        AgentClientProxy proxy = mock(AgentClientProxy.class);
        NacosAgentDiscoveryCacheHolder defaults =
            new NacosAgentDiscoveryCacheHolder("public", proxy);
        NacosAgentDiscoveryCacheHolder configured =
            new NacosAgentDiscoveryCacheHolder("public", proxy, 1);
        defaults.shutdown();
        configured.shutdown();
        ScheduledExecutorService polling = mock(ScheduledExecutorService.class);
        ExecutorService callbacks = mock(ExecutorService.class);
        assertThrows(IllegalArgumentException.class,
            () -> new NacosAgentDiscoveryCacheHolder("public", proxy, 10L, polling,
                callbacks, 0));
    }
    
    private AgentReference reference() {
        AgentReference result = new AgentReference();
        result.setAgentName("agent-a");
        return result;
    }
    
    private AgentDiscoveryResult result(String version, String digest) {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("agent-a");
        result.setVersion(version);
        result.setContentDigest(digest);
        result.setCallInterfaces(Collections.emptyList());
        return result;
    }
    
    private static final class TestListener extends AbstractNacosAgentDiscoveryListener {
        
        private NacosAgentDiscoveryEvent last;
        
        @Override
        public void onEvent(NacosAgentDiscoveryEvent event) {
            last = event;
        }
    }
}
