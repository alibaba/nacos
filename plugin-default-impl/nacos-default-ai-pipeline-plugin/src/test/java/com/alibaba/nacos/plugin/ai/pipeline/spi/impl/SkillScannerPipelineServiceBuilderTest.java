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

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    
    @Test
    void buildWithConfiguredExecutablePathTest() throws Exception {
        Path scanner = createExecutable("skill-scanner-path");
        Properties properties = new Properties();
        properties.setProperty("command", " " + scanner + " ");
        
        PublishPipelineService service = builder.build(properties);
        
        assertServiceAvailable(service);
    }
    
    @Test
    void buildWithConfiguredCommandFromPathTest() {
        Properties properties = new Properties();
        properties.setProperty("command", "java");
        
        PublishPipelineService service = builder.build(properties);
        
        assertServiceAvailable(service);
    }
    
    @Test
    void buildWithMissingConfiguredPathTest() {
        Properties properties = new Properties();
        properties.setProperty("command", "/path/to/missing-skill-scanner");
        
        PublishPipelineService service = builder.build(properties);
        
        assertNotNull(service);
        assertEquals("skill-scanner", service.pipelineId());
    }
    
    @Test
    void buildWithLlmConfiguredExecutablePathTest() throws Exception {
        Path scanner = createExecutable("skill-scanner-llm");
        Properties properties = new Properties();
        properties.setProperty("command", scanner.toString());
        properties.setProperty(SkillScannerScanOptions.PROP_USE_LLM, "true");
        
        PublishPipelineService service = builder.build(properties);
        
        assertServiceAvailable(service);
    }
    
    @Test
    void buildWithHomeExpandedExecutablePathTest() throws Exception {
        Path home = Files.createTempDirectory("nacos-skill-scanner-home");
        Path scanner = createExecutable(home, "scanner");
        String oldUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            Properties properties = new Properties();
            properties.setProperty("command", "~/" + scanner.getFileName());
            
            PublishPipelineService service = builder.build(properties);
            
            assertServiceAvailable(service);
        } finally {
            System.setProperty("user.home", oldUserHome);
        }
    }
    
    @Test
    void resolveBlankCandidateAndBlankPathTest() throws Exception {
        Method resolveCandidate =
            SkillScannerPipelineServiceBuilder.class.getDeclaredMethod("resolveCandidate",
                String.class);
        resolveCandidate.setAccessible(true);
        assertNull(resolveCandidate.invoke(builder, " "));
        
        SkillScannerPipelineServiceBuilder blankPathBuilder =
            new SkillScannerPipelineServiceBuilder() {
                
                @Override
                String getPathEnv() {
                    return "";
                }
            };
        Method findExecutableInPath =
            SkillScannerPipelineServiceBuilder.class.getDeclaredMethod("findExecutableInPath",
                String.class);
        findExecutableInPath.setAccessible(true);
        assertNull(findExecutableInPath.invoke(blankPathBuilder, "skill-scanner"));
    }
    
    private Path createExecutable(String name) throws Exception {
        return createExecutable(Files.createTempDirectory("nacos-skill-scanner"), name);
    }
    
    private Path createExecutable(Path dir, String name) throws Exception {
        Path scanner = dir.resolve(name);
        Files.write(scanner, Arrays.asList("#!/bin/sh", "exit 0"));
        assertTrue(scanner.toFile().setExecutable(true));
        return scanner;
    }
    
    private void assertServiceAvailable(PublishPipelineService service) {
        assertNotNull(service);
        PublishPipelineResult result = service.execute(new PublishPipelineContext());
        assertTrue(result.isPassed(), result.getMessage());
    }
}
