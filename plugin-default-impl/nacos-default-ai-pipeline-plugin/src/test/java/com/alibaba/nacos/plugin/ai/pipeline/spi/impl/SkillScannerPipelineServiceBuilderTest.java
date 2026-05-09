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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillScannerPipelineServiceBuilder} unit test.
 *
 * @author qiacheng.cxy
 */
class SkillScannerPipelineServiceBuilderTest {
    
    private SkillScannerPipelineServiceBuilder builder;
    
    @BeforeEach
    void setUp() {
        builder = new SkillScannerPipelineServiceBuilder();
    }
    
    @Test
    void pipelineIdTest() {
        assertEquals("skill-scanner", builder.pipelineId());
    }
    
    @Test
    void buildTest() {
        PublishPipelineService service = builder.build(new Properties());
        
        assertNotNull(service);
        assertEquals("skill-scanner", service.pipelineId());
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.SKILL));
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.AGENTSPEC));
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.PROMPT));
        assertEquals(100, service.getPreferOrder());
    }
}
