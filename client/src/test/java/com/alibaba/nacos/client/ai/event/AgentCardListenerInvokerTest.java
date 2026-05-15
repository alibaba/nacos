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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentCardListenerInvokerTest {
    
    @Mock
    AbstractNacosAgentCardListener listener;
    
    AgentCardListenerInvoker invoker;
    
    @BeforeEach
    void setUp() {
        invoker = new AgentCardListenerInvoker(listener);
    }
    
    @Test
    void invokeDirect() {
        AgentCardDetailInfo agentCard = new AgentCardDetailInfo();
        agentCard.setName("agent");
        NacosAgentCardEvent event = new NacosAgentCardEvent(agentCard);
        invoker.invoke(event);
        verify(listener).onEvent(any(NacosAgentCardEvent.class));
    }
}
