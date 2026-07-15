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

package com.alibaba.nacos.api.ai.listener;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Property-based test for NacosAgentSpecEvent construction round-trip.
 *
 * <p><b>Validates: Requirements 3.2, 3.3, 3.4</b></p>
 *
 * @author kiro
 */
class NacosAgentSpecEventPropertyTest {
    
    /**
     * Property 8: For any agentSpecName and AgentSpec object, constructing a NacosAgentSpecEvent
     * and then calling getAgentSpecName() should return the input agentSpecName,
     * and getAgentSpec() should return the same AgentSpec reference (identity equal).
     *
     * <p><b>Validates: Requirements 3.2, 3.3, 3.4</b></p>
     */
    @Property
    void constructorRoundTripPreservesValues(
        @ForAll("arbitraryAgentSpecNames") String agentSpecName) {
        AgentSpec agentSpec = new AgentSpec();
        
        NacosAgentSpecEvent event = new NacosAgentSpecEvent(agentSpecName, agentSpec);
        
        assertEquals(agentSpecName, event.getAgentSpecName(),
            "getAgentSpecName() should return the agentSpecName passed to constructor");
        assertSame(agentSpec, event.getAgentSpec(),
            "getAgentSpec() should return the exact same AgentSpec reference passed to constructor");
    }
    
    @Provide
    Arbitrary<String> arbitraryAgentSpecNames() {
        return Arbitraries.strings().alpha().numeric().withChars('-', '_', '.')
            .ofMinLength(0).ofMaxLength(100);
    }
}
