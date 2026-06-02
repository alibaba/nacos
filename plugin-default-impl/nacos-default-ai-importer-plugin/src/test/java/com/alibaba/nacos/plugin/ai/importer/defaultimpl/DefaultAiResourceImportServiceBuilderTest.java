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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl;

import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpRegistryImportService;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpRegistryImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillWellKnownImportService;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillWellKnownImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillsShImportService;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillsShImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultAiResourceImportServiceBuilderTest {
    
    @Test
    void testMcpRegistryImportServiceBuilder() {
        McpRegistryImportServiceBuilder builder = new McpRegistryImportServiceBuilder();
        
        AiResourceImportService service = builder.build(new Properties());
        
        assertEquals(McpRegistryImportServiceBuilder.IMPORTER_TYPE, builder.importerType());
        assertInstanceOf(McpRegistryImportService.class, service);
    }
    
    @Test
    void testSkillsShImportServiceBuilder() {
        SkillsShImportServiceBuilder builder = new SkillsShImportServiceBuilder();
        
        AiResourceImportService service = builder.build(new Properties());
        
        assertEquals(SkillsShImportServiceBuilder.IMPORTER_TYPE, builder.importerType());
        assertInstanceOf(SkillsShImportService.class, service);
    }
    
    @Test
    void testSkillWellKnownImportServiceBuilder() {
        SkillWellKnownImportServiceBuilder builder = new SkillWellKnownImportServiceBuilder();
        
        AiResourceImportService service = builder.build(new Properties());
        
        assertEquals(SkillWellKnownImportServiceBuilder.IMPORTER_TYPE, builder.importerType());
        assertInstanceOf(SkillWellKnownImportService.class, service);
    }
}
