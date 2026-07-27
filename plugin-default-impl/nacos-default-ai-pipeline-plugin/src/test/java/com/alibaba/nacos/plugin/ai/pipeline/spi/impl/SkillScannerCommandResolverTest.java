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
 * {@link SkillScannerCommandResolver} unit test.
 *
 * @author Nacos
 */
class SkillScannerCommandResolverTest {
    
    @Test
    void resolveConfiguredAbsolutePathTest() throws Exception {
        Path scanner = createExecutable(Files.createTempDirectory("scanner-absolute"), "scanner");
        
        assertEquals(scanner.toString(), SkillScannerCommandResolver.resolve(scanner.toString()));
    }
    
    @Test
    void resolveConfiguredHomePathTest() throws Exception {
        Path home = Files.createTempDirectory("scanner-home");
        Path scanner = createExecutable(home, "scanner");
        
        assertEquals(scanner.toString(),
            SkillScannerCommandResolver.resolve("~/scanner", "unused", home.toString()));
        assertNull(SkillScannerCommandResolver.resolve("~/missing", "", ""));
    }
    
    @Test
    void resolveConfiguredCommandFromPathTest() throws Exception {
        Path directory = Files.createTempDirectory("scanner-path");
        Path scanner = createExecutable(directory, "scanner");
        String pathEnv = " " + File.pathSeparator + directory;
        
        assertEquals(scanner.toString(),
            SkillScannerCommandResolver.resolve("scanner", pathEnv, ""));
    }
    
    @Test
    void resolveCommandFromUserLocalBinTest() throws Exception {
        Path pathDirectory = Files.createTempDirectory("scanner-empty-path");
        Path home = Files.createTempDirectory("scanner-local-home");
        Path scanner = createExecutable(home.resolve(".local/bin"), "scanner");
        
        assertEquals(scanner.toString(), SkillScannerCommandResolver.resolve("scanner",
            pathDirectory.toString(), home.toString()));
    }
    
    @Test
    void resolveMissingConfiguredCommandFallsBackToDefaultTest() throws Exception {
        Path directory = Files.createTempDirectory("scanner-default");
        Path scanner = createExecutable(directory,
            SkillScannerPipelineService.DEFAULT_SKILL_SCANNER_CMD);
        
        assertEquals(scanner.toString(), SkillScannerCommandResolver.resolve("/missing/scanner",
            directory.toString(), ""));
        assertEquals(scanner.toString(),
            SkillScannerCommandResolver.resolve(" ", directory.toString(), ""));
    }
    
    @Test
    void resolveMissingCommandTest() {
        assertNull(SkillScannerCommandResolver.resolve("skill-scanner", "", "/tmp/home"));
        assertNull(SkillScannerCommandResolver.resolve("missing\\scanner", "", ""));
    }
    
    private Path createExecutable(Path directory, String name) throws Exception {
        Files.createDirectories(directory);
        Path scanner = directory.resolve(name);
        Files.write(scanner, Arrays.asList("#!/bin/sh", "exit 0"));
        assertTrue(scanner.toFile().setExecutable(true));
        return scanner;
    }
}
