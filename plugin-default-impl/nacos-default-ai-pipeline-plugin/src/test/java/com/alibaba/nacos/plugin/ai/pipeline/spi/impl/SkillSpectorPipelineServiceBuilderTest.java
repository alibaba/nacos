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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillSpectorPipelineServiceBuilder} unit test.
 *
 * @author nacos
 */
class SkillSpectorPipelineServiceBuilderTest {
    
    private SkillSpectorPipelineServiceBuilder builder;
    
    @BeforeEach
    void setUp() {
        builder = new SkillSpectorPipelineServiceBuilder();
    }
    
    @Test
    void pipelineIdTest() {
        assertEquals("skill-spector", builder.pipelineId());
    }
    
    @Test
    void buildTest() {
        PublishPipelineService service = builder.build(null);
        
        assertNotNull(service);
        assertEquals("skill-spector", service.pipelineId());
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.SKILL));
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.AGENTSPEC));
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.PROMPT));
        assertEquals(90, service.getPreferOrder());
    }
    
    @Test
    void buildWithConfiguredPropertiesTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.COMMAND, "/missing/skill-spector");
        properties.setProperty(SkillSpectorPluginConfig.USE_LLM, "true");
        
        PublishPipelineService service = builder.build(properties);
        SkillSpectorPipelineService skillSpector = (SkillSpectorPipelineService) service;
        Map<String, String> config = skillSpector.getCurrentConfig();
        
        assertEquals("/missing/skill-spector",
            config.get(SkillSpectorPluginConfig.COMMAND));
        assertEquals("true", config.get(SkillSpectorPluginConfig.USE_LLM));
    }
}
