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

import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeConfig;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying Pipeline routing isolation between resource types.
 *
 * <p>Validates: Requirements 3.1, 3.2, 3.3</p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineManagerRoutingTest {
    
    private static final String PLUGIN_A_ID = "plugin-skill-only";
    
    private static final String PLUGIN_B_ID = "plugin-agentspec-only";
    
    private static final String PLUGIN_C_ID = "plugin-dual-support";
    
    private PublishPipelineManager manager;
    
    private List<PipelineNodeConfig> allNodes;
    
    @BeforeEach
    void setUp() {
        manager = new PublishPipelineManager();
        
        List<PublishPipelineServiceBuilder> builders = new ArrayList<>();
        
        // Plugin A: supports only SKILL
        builders.add(createBuilder(PLUGIN_A_ID, 1,
            new PublishPipelineResourceType[] {PublishPipelineResourceType.SKILL}));
        
        // Plugin B: supports only AGENTSPEC
        builders.add(createBuilder(PLUGIN_B_ID, 2,
            new PublishPipelineResourceType[] {PublishPipelineResourceType.AGENTSPEC}));
        
        // Plugin C: supports both SKILL and AGENTSPEC
        builders.add(createBuilder(PLUGIN_C_ID, 3,
            new PublishPipelineResourceType[] {PublishPipelineResourceType.SKILL,
                PublishPipelineResourceType.AGENTSPEC}));
        
        PipelineConfig config = new PipelineConfig();
        config.setEnabled(true);
        config.setNodes(new ArrayList<>());
        
        manager.initWithBuilders(builders, config);
        
        // Build nodes list referencing all three plugins
        allNodes = new ArrayList<>();
        for (String id : new String[] {PLUGIN_A_ID, PLUGIN_B_ID, PLUGIN_C_ID}) {
            PipelineNodeConfig node = new PipelineNodeConfig();
            node.setPipelineId(id);
            node.setProperties(new Properties());
            allNodes.add(node);
        }
    }
    
    /**
     * getPipelineServices(AGENTSPEC, allNodes) returns Plugin B and Plugin C but NOT Plugin A.
     *
     * <p>Validates: Requirement 3.1 — AGENTSPEC requests only return plugins that declare AGENTSPEC support.</p>
     */
    @Test
    void testAgentspecRoutingOnlyReturnsAgentspecPlugins() {
        List<PublishPipelineService> result = manager.getPipelineServices(
            PublishPipelineResourceType.AGENTSPEC, allNodes);
        
        Set<String> resultIds = result.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        
        assertTrue(resultIds.contains(PLUGIN_B_ID),
            "AGENTSPEC-only plugin should be included in AGENTSPEC routing");
        assertTrue(resultIds.contains(PLUGIN_C_ID),
            "Dual-support plugin should be included in AGENTSPEC routing");
        assertFalse(resultIds.contains(PLUGIN_A_ID),
            "SKILL-only plugin should NOT be included in AGENTSPEC routing");
        assertEquals(2, result.size(),
            "Exactly 2 plugins should be returned for AGENTSPEC routing");
    }
    
    /**
     * getPipelineServices(AGENTSPEC, allNodes) does NOT contain Plugin A (SKILL-only).
     *
     * <p>Validates: Requirement 3.2 — SKILL-only plugins are not routed to AGENTSPEC requests.</p>
     */
    @Test
    void testSkillOnlyPluginNotRoutedToAgentspec() {
        List<PublishPipelineService> result = manager.getPipelineServices(
            PublishPipelineResourceType.AGENTSPEC, allNodes);
        
        Set<String> resultIds = result.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        
        assertFalse(resultIds.contains(PLUGIN_A_ID),
            "SKILL-only plugin must NOT be routed to AGENTSPEC requests");
    }
    
    /**
     * Plugin C (dual support) appears in both SKILL and AGENTSPEC routing results.
     * Plugin A appears only in SKILL results. Plugin B appears only in AGENTSPEC results.
     *
     * <p>Validates: Requirement 3.3 — dual-support plugins are routed to both resource types.</p>
     */
    @Test
    void testDualSupportPluginRoutedToBothTypes() {
        List<PublishPipelineService> skillResult = manager.getPipelineServices(
            PublishPipelineResourceType.SKILL, allNodes);
        List<PublishPipelineService> agentspecResult = manager.getPipelineServices(
            PublishPipelineResourceType.AGENTSPEC, allNodes);
        
        Set<String> skillIds = skillResult.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        Set<String> agentspecIds = agentspecResult.stream()
            .map(PublishPipelineService::pipelineId)
            .collect(Collectors.toSet());
        
        // SKILL routing: contains A and C
        assertTrue(skillIds.contains(PLUGIN_A_ID),
            "SKILL-only plugin should be in SKILL routing");
        assertTrue(skillIds.contains(PLUGIN_C_ID),
            "Dual-support plugin should be in SKILL routing");
        
        // AGENTSPEC routing: contains B and C
        assertTrue(agentspecIds.contains(PLUGIN_B_ID),
            "AGENTSPEC-only plugin should be in AGENTSPEC routing");
        assertTrue(agentspecIds.contains(PLUGIN_C_ID),
            "Dual-support plugin should be in AGENTSPEC routing");
        
        // Dual-support plugin C is in both
        assertTrue(skillIds.contains(PLUGIN_C_ID) && agentspecIds.contains(PLUGIN_C_ID),
            "Dual-support plugin must appear in both SKILL and AGENTSPEC routing");
    }
    
    private PublishPipelineServiceBuilder createBuilder(String pipelineId, int order,
        PublishPipelineResourceType[] supportedTypes) {
        return new PublishPipelineServiceBuilder() {
            
            @Override
            public String pipelineId() {
                return pipelineId;
            }
            
            @Override
            public PublishPipelineService build(Properties properties) {
                return new PublishPipelineService() {
                    
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
        };
    }
}
