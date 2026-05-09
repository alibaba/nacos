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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.NotBlank;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for AiChangeNotifier AgentSpec event dispatch correctness.
 *
 * <p>Property 4: For any registered agentSpecName and corresponding AgentSpecListenerInvoker,
 * when AiChangeNotifier receives an AgentSpecChangedEvent for that agentSpecName,
 * the invoker's isInvoked() should return true.</p>
 *
 * <p><b>Validates: Requirements 8.2, 8.7</b></p>
 *
 * @author kiro
 */
class AiChangeNotifierAgentSpecDispatchPropertyTest {
    
    /**
     * Property 4: AgentSpec event dispatch correctness.
     *
     * <p><b>Validates: Requirements 8.2, 8.7</b></p>
     */
    @Property
    void registeredInvokerIsInvokedOnMatchingEvent(@ForAll @NotBlank String agentSpecName) {
        AiChangeNotifier notifier = new AiChangeNotifier();
        
        AbstractNacosAgentSpecListener listener = new AbstractNacosAgentSpecListener() {
            
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
                // no-op for property test
            }
        };
        AgentSpecListenerInvoker invoker = new AgentSpecListenerInvoker(listener);
        
        // Before registration and event, invoker should not be invoked
        assertFalse(invoker.isInvoked(), "Invoker should not be invoked before registration");
        
        // Register the listener
        notifier.registerListener(agentSpecName, invoker);
        
        // Fire an AgentSpecChangedEvent for the same agentSpecName
        AgentSpec agentSpec = new AgentSpec();
        notifier.onEvent(new AgentSpecChangedEvent(agentSpecName, agentSpec));
        
        // After receiving the matching event, invoker should have been invoked
        assertTrue(invoker.isInvoked(),
            "Invoker should be invoked after receiving matching AgentSpecChangedEvent");
    }
}
