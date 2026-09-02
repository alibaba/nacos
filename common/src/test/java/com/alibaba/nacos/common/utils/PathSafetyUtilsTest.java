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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathSafetyUtilsTest {
    
    @TempDir
    private Path tempDir;
    
    @ParameterizedTest
    @ValueSource(strings = {"config", ".hidden", "config.json", "foo..bar", "..."})
    void testResolveDirectChild(String childName) {
        assertEquals(tempDir.resolve(childName).toAbsolutePath().normalize(),
            PathSafetyUtils.resolveDirectChild(tempDir, childName));
    }
    
    @Test
    void testResolveDirectChildPreservesRelativeBase() {
        assertEquals(Paths.get("base", "config"),
            PathSafetyUtils.resolveDirectChild(Paths.get("base"), "config"));
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../target", "..\\target", "/target",
        "\\target"})
    void testRejectUnsafeDirectChild(String childName) {
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(tempDir, childName));
    }
    
    @Test
    void testRejectNullBasePath() {
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(null, "config"));
    }
    
    @Test
    void testResolveArchiveEntryWithPortableSeparators() {
        Path expected = tempDir.resolve("snapshot").resolve("data").toAbsolutePath().normalize();
        assertEquals(expected,
            PathSafetyUtils.resolveArchiveEntry(tempDir, "snapshot\\data"));
        assertEquals(expected,
            PathSafetyUtils.resolveArchiveEntry(tempDir, "snapshot/data/"));
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../target", "snapshot/../target",
        "snapshot/./target", "/target", "\\target", "C:/target", "C:target",
        "snapshot//target"})
    void testRejectUnsafeArchiveEntry(String entryName) {
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(tempDir, entryName));
    }
}
