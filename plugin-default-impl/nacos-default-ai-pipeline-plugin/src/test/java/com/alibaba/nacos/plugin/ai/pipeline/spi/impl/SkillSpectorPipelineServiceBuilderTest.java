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

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        PublishPipelineService service = builder.build(new Properties());
        
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
    void buildIgnoresConfiguredCommandTest() throws Exception {
        Path runner = createExecutable(Files.createTempDirectory("nacos-skillspector"),
                "skillspector");
        Path emptyHome = Files.createTempDirectory("nacos-home");
        String oldNacosHome = System.getProperty("nacos.home");
        String oldUserDir = System.getProperty("user.dir");
        Properties properties = new Properties();
        properties.setProperty("command", runner.toString());
        try {
            System.setProperty("nacos.home", emptyHome.toString());
            System.setProperty("user.dir", emptyHome.toString());
            
            PublishPipelineService service = builder.build(properties);
            
            PublishPipelineResult result = service.execute(new PublishPipelineContext());
            assertFalse(result.isPassed());
            assertTrue(result.getMessage().contains("SkillSpector 内置运行时不可用"));
        } finally {
            restoreSystemProperty("nacos.home", oldNacosHome);
            restoreSystemProperty("user.dir", oldUserDir);
        }
    }
    
    @Test
    void buildWithBuiltinRunnerTest() throws Exception {
        Path nacosHome = Files.createTempDirectory("nacos-home");
        Path runner = nacosHome.resolve("plugins").resolve("ai-pipeline")
                .resolve("skill-spector").resolve("bin").resolve("skill-spector");
        createPlatformRuntime(nacosHome.resolve("plugins").resolve("ai-pipeline")
                .resolve("skill-spector"));
        createExecutable(runner.getParent(), runner.getFileName().toString());
        String oldNacosHome = System.getProperty("nacos.home");
        System.setProperty("nacos.home", nacosHome.toString());
        try {
            PublishPipelineService service = builder.build(new Properties());
            
            PublishPipelineResult result = service.execute(new PublishPipelineContext());
            assertTrue(result.isPassed(), result.getMessage());
        } finally {
            restoreSystemProperty("nacos.home", oldNacosHome);
        }
    }
    
    @Test
    void buildRejectsRunnerWithoutPlatformRuntimeTest() throws Exception {
        Path nacosHome = Files.createTempDirectory("nacos-home");
        Path runner = nacosHome.resolve("plugins").resolve("ai-pipeline")
                .resolve("skill-spector").resolve("bin").resolve("skill-spector");
        createExecutable(runner.getParent(), runner.getFileName().toString());
        String oldNacosHome = System.getProperty("nacos.home");
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("nacos.home", nacosHome.toString());
            System.setProperty("user.dir", nacosHome.toString());
            
            PublishPipelineService service = builder.build(new Properties());
            
            PublishPipelineResult result = service.execute(new PublishPipelineContext());
            assertFalse(result.isPassed());
            assertTrue(result.getMessage().contains("SkillSpector 内置运行时不可用"));
        } finally {
            restoreSystemProperty("nacos.home", oldNacosHome);
            restoreSystemProperty("user.dir", oldUserDir);
        }
    }
    
    private void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
    
    private Path createExecutable(Path dir, String name) throws Exception {
        Files.createDirectories(dir);
        Path runner = dir.resolve(name);
        Files.write(runner, Arrays.asList("#!/bin/sh", "exit 0"));
        assertTrue(runner.toFile().setExecutable(true));
        return runner;
    }
    
    private void createPlatformRuntime(Path runtimeRoot) throws Exception {
        Path python = runtimeRoot.resolve("runtime").resolve(builder.platformKey())
                .resolve("python").resolve("bin").resolve("python3");
        createExecutable(python.getParent(), python.getFileName().toString());
    }
}
