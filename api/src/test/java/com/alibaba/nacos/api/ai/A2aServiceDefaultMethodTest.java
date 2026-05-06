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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aServiceDefaultMethodTest {
    
    private AtomicBoolean invokeMark;
    
    A2aService a2aService;
    
    @BeforeEach
    void setUp() {
        invokeMark = new AtomicBoolean(false);
        a2aService = new A2aService() {
            @Override
            public AgentCardDetailInfo getAgentCard(String agentName, String version, String registrationType)
                    throws NacosException {
                invokeMark.set(true);
                return null;
            }
            
            @Override
            public void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
                    throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints)
                    throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
                invokeMark.set(true);
            }
            
            @Override
            public AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
                    AbstractNacosAgentCardListener agentCardListener) throws NacosException {
                invokeMark.set(true);
                return null;
            }
            
            @Override
            public void unsubscribeAgentCard(String agentName, String version,
                    AbstractNacosAgentCardListener agentCardListener) throws NacosException {
                invokeMark.set(true);
            }
        };
    }
    
    @Test
    void getAgentCard() throws NacosException {
        a2aService.getAgentCard("");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void getAgentCardWithVersion() throws NacosException {
        a2aService.getAgentCard("", "v1.0");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void releaseAgentCard() throws NacosException {
        AgentCard agentCard = new AgentCard();
        a2aService.releaseAgentCard(agentCard);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void releaseAgentCardWithRegistrationType() throws NacosException {
        AgentCard agentCard = new AgentCard();
        a2aService.releaseAgentCard(agentCard, "SERVICE");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void registerAgentEndpoint() throws NacosException {
        a2aService.registerAgentEndpoint("", "v1.0", "127.0.0.1", 8080);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void registerAgentEndpointWithTransport() throws NacosException {
        a2aService.registerAgentEndpoint("", "v1.0", "127.0.0.1", 8080, "JSONRPC");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void registerAgentEndpointWithTransportAndPath() throws NacosException {
        a2aService.registerAgentEndpoint("", "v1.0", "127.0.0.1", 8080, "JSONRPC", "/test");
        assertTrue(invokeMark.get());
    }
    
    @Test
    void registerAgentEndpointWithFullParams() throws NacosException {
        a2aService.registerAgentEndpoint("", "v1.0", "127.0.0.1", 8080, "JSONRPC", "/test", true);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void deregisterAgentEndpoint() throws NacosException {
        a2aService.deregisterAgentEndpoint("", "v1.0", "127.0.0.1", 8080);
        assertTrue(invokeMark.get());
    }
    
    @Test
    void subscribeAgentCard() throws NacosException {
        a2aService.subscribeAgentCard("", null);
    }
    
    @Test
    void unsubscribeAgentCard() throws NacosException {
        a2aService.unsubscribeAgentCard("", null);
    }
}