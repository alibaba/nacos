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

import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentClientProxyTest {
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void legacyProxyStillSupportsDiscoveryWithOptionalAttribution(boolean watchNotify)
        throws NacosException {
        AgentClientProxy proxy = mock(AgentClientProxy.class, CALLS_REAL_METHODS);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        when(proxy.discoverAgent(request)).thenReturn(result);
        assertSame(result, proxy.discoverAgent(request, watchNotify));
        verify(proxy).discoverAgent(request);
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void legacyProxyPreservesDiscoveryExceptions(boolean watchNotify) throws NacosException {
        AgentClientProxy proxy = mock(AgentClientProxy.class, CALLS_REAL_METHODS);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        NacosException failure = new NacosException(NacosException.SERVER_ERROR, "unavailable");
        when(proxy.discoverAgent(request)).thenThrow(failure);
        assertSame(failure, assertThrows(NacosException.class,
            () -> proxy.discoverAgent(request, watchNotify)));
    }
}
