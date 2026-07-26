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

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.NotBlank;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for AiChangeNotifier subscribe/unsubscribe round-trip consistency.
 *
 * <p>Property 3: For any agentSpecName and AgentSpecListenerInvoker, after registering the listener
 * {@code isAgentSpecSubscribed(agentSpecName)} returns true; after deregistering that listener
 * (with no other listeners), {@code isAgentSpecSubscribed(agentSpecName)} returns false.</p>
 *
 * <p><b>Validates: Requirements 8.3, 8.4, 8.5, 8.8</b></p>
 *
 * @author kiro
 */
class AiChangeNotifierAgentSpecPropertyTest {
    
    /**
     * Property 3: subscribe/unsubscribe round-trip consistency.
     *
     * <p><b>Validates: Requirements 8.3, 8.4, 8.5, 8.8</b></p>
     */
    @Property
    void subscribeUnsubscribeRoundTrip(@ForAll @NotBlank String agentSpecName) {
        AiChangeNotifier notifier = new AiChangeNotifier();
        
        AbstractNacosAgentSpecListener listener = new AbstractNacosAgentSpecListener() {
            
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
                // no-op for property test
            }
        };
        AgentSpecListenerInvoker invoker = new AgentSpecListenerInvoker(listener);
        
        // Before registration, should not be subscribed
        assertFalse(notifier.isAgentSpecSubscribed(agentSpecName),
            "Should not be subscribed before registration");
        
        // After registration, should be subscribed
        notifier.registerListener(agentSpecName, invoker);
        assertTrue(notifier.isAgentSpecSubscribed(agentSpecName),
            "Should be subscribed after registration");
        
        // After deregistration, should not be subscribed
        notifier.deregisterListener(agentSpecName, invoker);
        assertFalse(notifier.isAgentSpecSubscribed(agentSpecName),
            "Should not be subscribed after deregistration");
    }
}
