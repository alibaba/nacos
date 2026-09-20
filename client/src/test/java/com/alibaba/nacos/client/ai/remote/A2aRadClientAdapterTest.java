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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class A2aRadClientAdapterTest {
    
    private final AgentClientProxy proxy = mock(AgentClientProxy.class);
    
    private final A2aRadClientAdapter adapter = new A2aRadClientAdapter("public", proxy);
    
    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500, 503, 50105})
    void discoveryErrorsArePreservedWithoutSupplementaryReadsOrFallback(int code) throws Exception {
        NacosException expected = new NacosException(code, "original failure");
        when(proxy.discoverAgent(any())).thenThrow(expected);
        assertSame(expected, assertThrows(NacosException.class,
            () -> adapter.getAgentCard("demo", null, null)));
        ArgumentCaptor<AgentDiscoveryRequest> captor =
            ArgumentCaptor.forClass(AgentDiscoveryRequest.class);
        verify(proxy).discoverAgent(captor.capture());
        assertEquals("latest", captor.getValue().getReference().getLabel());
        assertNull(captor.getValue().getFilter().getEndpointSources());
        verifyNoMoreInteractions(proxy);
    }
    
    @ParameterizedTest
    @ValueSource(ints = {403, 409, 500, 503, 50105})
    void publishErrorsNeverTriggerPreReadsOrReplay(int code) throws Exception {
        NacosException expected = new NacosException(code, "original failure");
        when(proxy.publishAgent(any())).thenThrow(expected);
        assertSame(expected, assertThrows(NacosException.class,
            () -> adapter.releaseAgentCard(card(), "SERVICE", true)));
        verify(proxy).publishAgent(any());
        verifyNoMoreInteractions(proxy);
    }
    
    @Test
    void publicationPassesFlagAndCompleteContentWithOneCall() throws Exception {
        AgentCard card = card();
        adapter.releaseAgentCard(card, null, false);
        ArgumentCaptor<AgentPublishRequest> captor =
            ArgumentCaptor.forClass(AgentPublishRequest.class);
        verify(proxy).publishAgent(captor.capture());
        assertFalse(captor.getValue().isAutoSubmit());
        assertEquals("demo", captor.getValue().getAgentName());
        assertEquals("1.0.0", captor.getValue().getVersion());
        assertEquals(1, captor.getValue().getCallInterfaces().size());
        assertNull(card.getSupportedInterfaces());
        verifyNoMoreInteractions(proxy);
    }
    
    @Test
    void invalidInputCannotStartNetworkWork() {
        assertThrows(NacosException.class, () -> adapter.getAgentCard("demo", null, "other"));
        assertThrows(NacosException.class, () -> adapter.getAgentCard("", null, null));
        assertThrows(NacosException.class, () -> adapter.releaseAgentCard(null, null, false));
        verifyNoInteractions(proxy);
    }
    
    private AgentCard card() {
        AgentCard card = new AgentCard();
        card.setName("demo");
        card.setVersion("1.0.0");
        card.setProtocolVersion("0.3");
        card.setUrl("https://example.com/rpc");
        card.setPreferredTransport("JSONRPC");
        return card;
    }
}
