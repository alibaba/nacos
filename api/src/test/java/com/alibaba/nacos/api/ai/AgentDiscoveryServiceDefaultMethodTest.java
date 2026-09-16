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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import java.util.Collections;
import java.util.List;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDiscoveryServiceDefaultMethodTest {
    
    @Test
    void compatibilityDefaultsReportNotImplemented() {
        AgentDiscoveryService service = new AgentDiscoveryService() {
        };
        assertNotImplemented(() -> service.searchAgents(new AgentSearchRequest()));
        assertNotImplemented(() -> service.discoverAgent(new AgentReference(),
            new AgentDiscoveryFilter()));
        assertNotImplemented(() -> service.subscribeAgent(new AgentReference(),
            new AgentDiscoveryFilter(), listener()));
        assertNotImplemented(() -> service.unsubscribeAgent(new AgentReference(),
            new AgentDiscoveryFilter(), listener()));
        assertNotImplemented(() -> service.registerAgentEndpoints(
            new AgentEndpointRegistrationBatch()));
        assertNotImplemented(() -> service.deregisterAgentEndpoints(
            "demo", "a2a", Collections.emptyList()));
    }
    
    @Test
    void convenienceOverloadsDelegateWithNullFilter() throws NacosException {
        AtomicInteger invocationCount = new AtomicInteger();
        AgentReference reference = new AgentReference();
        AbstractNacosAgentDiscoveryListener listener = listener();
        AgentDiscoveryService service = new AgentDiscoveryService() {
            
            @Override
            public AgentDiscoveryResult discoverAgent(AgentReference actualReference,
                AgentDiscoveryFilter filter) {
                assertSame(reference, actualReference);
                assertNull(filter);
                invocationCount.incrementAndGet();
                return null;
            }
            
            @Override
            public AgentDiscoveryResult subscribeAgent(AgentReference actualReference,
                AgentDiscoveryFilter filter,
                AbstractNacosAgentDiscoveryListener actualListener) {
                assertSame(reference, actualReference);
                assertNull(filter);
                assertSame(listener, actualListener);
                invocationCount.incrementAndGet();
                return null;
            }
            
            @Override
            public void unsubscribeAgent(AgentReference actualReference,
                AgentDiscoveryFilter filter,
                AbstractNacosAgentDiscoveryListener actualListener) {
                assertSame(reference, actualReference);
                assertNull(filter);
                assertSame(listener, actualListener);
                invocationCount.incrementAndGet();
            }
        };
        
        service.discoverAgent(reference);
        service.subscribeAgent(reference, listener);
        service.unsubscribeAgent(reference, listener);
        
        assertEquals(3, invocationCount.get());
    }
    
    @Test
    void publicAgentInputsDoNotExposeNamespace() throws Exception {
        Class<?>[] inputs =
            {AgentSearchRequest.class, AgentEndpointRegistrationBatch.class,
                AgentReference.class,
                AgentDiscoveryFilter.class,
                AgentPublishRequest.class};
        for (Class<?> input : inputs) {
            for (PropertyDescriptor property : Introspector.getBeanInfo(input)
                .getPropertyDescriptors()) {
                assertNotEquals("namespaceId", property.getName(), input.getName());
            }
        }
        assertEquals(Page.class, AgentDiscoveryService.class.getMethod(
            "searchAgents", AgentSearchRequest.class).getReturnType());
        assertEquals(Void.TYPE, AgentDiscoveryService.class.getMethod(
            "registerAgentEndpoints", AgentEndpointRegistrationBatch.class).getReturnType());
        assertEquals(Void.TYPE, AgentDiscoveryService.class.getMethod(
            "deregisterAgentEndpoints", String.class, String.class, List.class).getReturnType());
    }
    
    private void assertNotImplemented(ThrowingOperation operation) {
        NacosException exception = assertThrows(NacosException.class, operation::run);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode());
    }
    
    private AbstractNacosAgentDiscoveryListener listener() {
        return new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
            }
        };
    }
    
    private interface ThrowingOperation {
        
        void run() throws NacosException;
    }
}
