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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillSpectorCommandResolver} unit test.
 *
 * @author Nacos
 */
class SkillSpectorCommandResolverTest {
    
    @Test
    void resolveConfiguredAbsolutePathTest() throws Exception {
        Path command = createExecutable(Files.createTempDirectory("spector-absolute"), "spector");
        
        assertEquals(command.toString(), SkillSpectorCommandResolver.resolve(command.toString()));
    }
    
    @Test
    void resolveConfiguredHomePathTest() throws Exception {
        Path home = Files.createTempDirectory("spector-home");
        Path command = createExecutable(home, "spector");
        
        assertEquals(command.toString(),
            SkillSpectorCommandResolver.resolve("~/spector", "unused", home.toString()));
        assertNull(SkillSpectorCommandResolver.resolve("~/missing", "", ""));
    }
    
    @Test
    void resolveConfiguredCommandFromPathTest() throws Exception {
        Path directory = Files.createTempDirectory("spector-path");
        Path command = createExecutable(directory, "spector");
        String pathEnv = " " + File.pathSeparator + directory;
        
        assertEquals(command.toString(),
            SkillSpectorCommandResolver.resolve("spector", pathEnv, ""));
    }
    
    @Test
    void resolveCommandFromAiPipelineBinTest() throws Exception {
        Path home = Files.createTempDirectory("spector-ai-home");
        Path command = createExecutable(home.resolve("ai-infra/ai-pipeline/bin"), "spector");
        
        assertEquals(command.toString(),
            SkillSpectorCommandResolver.resolve("spector", "", home.toString()));
    }
    
    @Test
    void resolveCommandFromUserLocalBinTest() throws Exception {
        Path emptyPath = Files.createTempDirectory("spector-empty-path");
        Path home = Files.createTempDirectory("spector-local-home");
        Path command = createExecutable(home.resolve(".local/bin"), "spector");
        
        assertEquals(command.toString(), SkillSpectorCommandResolver.resolve("spector",
            emptyPath.toString(), home.toString()));
    }
    
    @Test
    void resolveMissingConfiguredCommandFallsBackToDefaultTest() throws Exception {
        Path directory = Files.createTempDirectory("spector-default");
        Path command = createExecutable(directory,
            SkillSpectorPipelineService.DEFAULT_SKILL_SPECTOR_CMD);
        
        assertEquals(command.toString(), SkillSpectorCommandResolver.resolve("/missing/spector",
            directory.toString(), ""));
        assertEquals(command.toString(),
            SkillSpectorCommandResolver.resolve(" ", directory.toString(), ""));
    }
    
    @Test
    void resolveMissingCommandTest() {
        assertNull(SkillSpectorCommandResolver.resolve("skill-spector", "", "/tmp/home"));
        assertNull(SkillSpectorCommandResolver.resolve("missing\\spector", "", ""));
    }
    
    private Path createExecutable(Path directory, String name) throws Exception {
        Files.createDirectories(directory);
        Path command = directory.resolve(name);
        Files.write(command, Arrays.asList("#!/bin/sh", "exit 0"));
        assertTrue(command.toFile().setExecutable(true));
        return command;
    }
}
