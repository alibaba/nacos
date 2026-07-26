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

package com.alibaba.nacos.ai.pipeline;

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying backward compatibility of SKILL and PROMPT routing
 * after adding the AGENTSPEC resource type.
 *
 * <p>Validates: Requirements 5.1, 5.2</p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineBackwardCompatibilityTest {
    
    private static final String PLUGIN_SKILL_ID = "plugin-skill-only";
    
    private static final String PLUGIN_AGENTSPEC_ID = "plugin-agentspec-only";
    
    private static final String PLUGIN_PROMPT_ID = "plugin-prompt-only";
    
    private static final String PLUGIN_SKILL_LOW_ORDER_ID = "plugin-skill-low";
    
    private static final String PLUGIN_SKILL_HIGH_ORDER_ID = "plugin-skill-high";
    
    private PublishPipelineManager manager;
    
    @BeforeEach
    void setUp() {
        List<PublishPipelineService> services = Arrays.asList(
            createService(PLUGIN_SKILL_ID, 1, PublishPipelineResourceType.SKILL),
            createService(PLUGIN_AGENTSPEC_ID, 2, PublishPipelineResourceType.AGENTSPEC),
            createService(PLUGIN_PROMPT_ID, 3, PublishPipelineResourceType.PROMPT));
        manager = TestAiPipelineSupport.newManager(true,
            Arrays.asList(PLUGIN_SKILL_ID, PLUGIN_AGENTSPEC_ID, PLUGIN_PROMPT_ID), services);
    }
    
    @AfterEach
    void tearDown() {
        TestAiPipelineSupport.clearStateChecker();
    }
    
    /**
     * SKILL routing returns only the SKILL plugin; the AGENTSPEC plugin is NOT included.
     *
     * <p>Validates: Requirement 5.1 — SKILL Pipeline execution flow behavior is unchanged after adding AGENTSPEC.</p>
     */
    @Test
    void testSkillRoutingUnchangedAfterAgentspecAddition() {
        List<PublishPipelineService> result = manager.getPipelineServices(
            PublishPipelineResourceType.SKILL);
        
        Set<String> resultIds = result.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        
        assertEquals(1, result.size(),
            "Exactly 1 plugin should be returned for SKILL routing");
        assertTrue(resultIds.contains(PLUGIN_SKILL_ID),
            "SKILL-only plugin should be included in SKILL routing");
        assertFalse(resultIds.contains(PLUGIN_AGENTSPEC_ID),
            "AGENTSPEC-only plugin should NOT be included in SKILL routing");
        assertFalse(resultIds.contains(PLUGIN_PROMPT_ID),
            "PROMPT-only plugin should NOT be included in SKILL routing");
    }
    
    /**
     * PROMPT routing returns only the PROMPT plugin; the AGENTSPEC plugin is NOT included.
     *
     * <p>Validates: Requirement 5.2 — PROMPT Pipeline execution flow behavior is unchanged after adding AGENTSPEC.</p>
     */
    @Test
    void testPromptRoutingUnchangedAfterAgentspecAddition() {
        List<PublishPipelineService> result = manager.getPipelineServices(
            PublishPipelineResourceType.PROMPT);
        
        Set<String> resultIds = result.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        
        assertEquals(1, result.size(),
            "Exactly 1 plugin should be returned for PROMPT routing");
        assertTrue(resultIds.contains(PLUGIN_PROMPT_ID),
            "PROMPT-only plugin should be included in PROMPT routing");
        assertFalse(resultIds.contains(PLUGIN_AGENTSPEC_ID),
            "AGENTSPEC-only plugin should NOT be included in PROMPT routing");
        assertFalse(resultIds.contains(PLUGIN_SKILL_ID),
            "SKILL-only plugin should NOT be included in PROMPT routing");
    }
    
    /**
     * Multiple SKILL plugins are returned sorted by order ascending, unaffected by AGENTSPEC presence.
     *
     * <p>Validates: Requirement 5.1 — SKILL plugin ordering is preserved after adding AGENTSPEC.</p>
     */
    @Test
    void testSkillPluginOrderPreservedAfterAgentspecAddition() {
        List<PublishPipelineService> services = Arrays.asList(
            createService(PLUGIN_SKILL_HIGH_ORDER_ID, 10, PublishPipelineResourceType.SKILL),
            createService(PLUGIN_AGENTSPEC_ID, 5, PublishPipelineResourceType.AGENTSPEC),
            createService(PLUGIN_SKILL_LOW_ORDER_ID, 1, PublishPipelineResourceType.SKILL));
        PublishPipelineManager orderManager = TestAiPipelineSupport.newManager(true,
            Arrays.asList(PLUGIN_SKILL_HIGH_ORDER_ID, PLUGIN_AGENTSPEC_ID,
                PLUGIN_SKILL_LOW_ORDER_ID),
            services);
        
        List<PublishPipelineService> result = orderManager.getPipelineServices(
            PublishPipelineResourceType.SKILL);
        
        assertEquals(2, result.size(),
            "Exactly 2 SKILL plugins should be returned");
        assertEquals(PLUGIN_SKILL_LOW_ORDER_ID, result.get(0).pipelineId(),
            "Lower-order SKILL plugin should come first");
        assertEquals(PLUGIN_SKILL_HIGH_ORDER_ID, result.get(1).pipelineId(),
            "Higher-order SKILL plugin should come second");
        
        // Verify AGENTSPEC plugin is not in the SKILL results
        Set<String> resultIds = result.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        assertFalse(resultIds.contains(PLUGIN_AGENTSPEC_ID),
            "AGENTSPEC plugin should NOT appear in SKILL routing results");
    }
    
    private PublishPipelineService createService(String pipelineId, int order,
        PublishPipelineResourceType... supportedTypes) {
        return new TestPublishPipelineService() {
            
            @Override
            public String pipelineId() {
                return pipelineId;
            }
            
            @Override
            public PublishPipelineResult execute(PublishPipelineContext context) {
                return null;
            }
            
            @Override
            public int getPreferOrder() {
                return order;
            }
            
            @Override
            public PublishPipelineResourceType[] pipelineResourceTypes() {
                return supportedTypes;
            }
        };
    }
    
}
