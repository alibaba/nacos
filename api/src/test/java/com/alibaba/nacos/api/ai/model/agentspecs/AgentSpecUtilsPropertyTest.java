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

package com.alibaba.nacos.api.ai.model.agentspecs;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for AgentSpecUtils group prefix consistency.
 *
 * <p><b>Validates: Requirements 4.2, 4.3</b></p>
 *
 * @author kiro
 */
class AgentSpecUtilsPropertyTest {
    
    private static final String PREFIX = "agentspec__";
    
    /**
     * Property 1a: For any non-blank agentSpecName, buildAgentSpecGroup starts with "agentspec__"
     * and decodes back to the original name.
     *
     * <p><b>Validates: Requirements 4.2</b></p>
     */
    @Property
    void buildAgentSpecGroupStartsWithPrefixAndDecodes(
        @ForAll("nonBlankStrings") String agentSpecName) {
        String result = AgentSpecUtils.buildAgentSpecGroup(agentSpecName);
        assertTrue(result.startsWith(PREFIX),
            "buildAgentSpecGroup result should start with 'agentspec__', got: " + result);
        String[] parts = AgentSpecUtils.decodeAgentSpecGroupToNameAndVersion(result);
        assertEquals(agentSpecName, parts[0]);
        assertNull(parts[1]);
    }
    
    /**
     * Property 1b: For any non-blank agentSpecName and version, buildAgentSpecVersionGroup
     * starts with "agentspec__" and decodes back to the original name and version.
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property
    void buildAgentSpecVersionGroupStartsWithPrefixAndDecodes(
        @ForAll("nonBlankStrings") String agentSpecName,
        @ForAll("nonBlankStrings") String version) {
        String result = AgentSpecUtils.buildAgentSpecVersionGroup(agentSpecName, version);
        assertTrue(result.startsWith(PREFIX),
            "buildAgentSpecVersionGroup result should start with 'agentspec__', got: " + result);
        String[] parts = AgentSpecUtils.decodeAgentSpecGroupToNameAndVersion(result);
        assertEquals(agentSpecName, parts[0]);
        assertEquals(version, parts[1]);
    }
    
    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings().alpha().numeric().withChars('-', '_', '.')
            .ofMinLength(1).ofMaxLength(50)
            .filter(s -> !s.trim().isEmpty());
    }
}
