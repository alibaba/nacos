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
    void buildWithConfiguredCommandTest() throws Exception {
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
            assertTrue(result.isPassed(), result.getMessage());
        } finally {
            restoreSystemProperty("nacos.home", oldNacosHome);
            restoreSystemProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void buildWithHomeExpandedCommandTest() throws Exception {
        Path home = Files.createTempDirectory("nacos-skillspector-home");
        Path runner = createExecutable(home, "skill-spector");
        String oldUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            Properties properties = new Properties();
            properties.setProperty("command", "~/" + runner.getFileName());

            PublishPipelineService service = builder.build(properties);

            PublishPipelineResult result = service.execute(new PublishPipelineContext());
            assertTrue(result.isPassed(), result.getMessage());
        } finally {
            restoreSystemProperty("user.home", oldUserHome);
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
}
