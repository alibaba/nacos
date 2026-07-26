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

package com.alibaba.nacos.client.ai.utils;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based test for CacheKeyUtils AgentSpec key equivalence.
 *
 * <p><b>Validates: Requirements 9.1, 9.2</b></p>
 *
 * @author kiro
 */
class CacheKeyUtilsPropertyTest {
    
    /**
     * Property 2: For any agentSpecName string, buildAgentSpecKey(agentSpecName) should
     * return agentSpecName itself (i.e. buildAgentSpecKey(name).equals(name) always holds).
     *
     * <p><b>Validates: Requirements 9.1, 9.2</b></p>
     */
    @Property
    void buildAgentSpecKeyAlwaysReturnsNameItself(@ForAll String agentSpecName) {
        String result = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        assertEquals(agentSpecName, result,
            "buildAgentSpecKey should return agentSpecName itself, got: " + result);
    }
}
