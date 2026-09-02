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
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    
    @ParameterizedTest
    @ValueSource(strings = {"config", ".hidden", "config.json", "foo..bar", "..."})
    void testValidateDirectChildName(String childName) {
        assertDoesNotThrow(() -> PathSafetyUtils.validateDirectChildName(childName));
    }
    
    @Test
    void testValidateDirectChildNameRejectsUnexpectedProviderSemantics() {
        Path absolutePath = Mockito.mock(Path.class);
        Mockito.when(absolutePath.isAbsolute()).thenReturn(true);
        Path rootedPath = Mockito.mock(Path.class);
        Mockito.when(rootedPath.getRoot()).thenReturn(Mockito.mock(Path.class));
        Path nestedPath = Mockito.mock(Path.class);
        Mockito.when(nestedPath.getNameCount()).thenReturn(2);
        InvalidPathException invalidPath = new InvalidPathException("invalid", "mock failure");
        try (MockedStatic<Paths> pathsMock =
            Mockito.mockStatic(Paths.class, Mockito.CALLS_REAL_METHODS)) {
            pathsMock.when(() -> Paths.get("absolute")).thenReturn(absolutePath);
            pathsMock.when(() -> Paths.get("rooted")).thenReturn(rootedPath);
            pathsMock.when(() -> Paths.get("nested")).thenReturn(nestedPath);
            pathsMock.when(() -> Paths.get("invalid")).thenThrow(invalidPath);
            
            assertThrows(IllegalArgumentException.class,
                () -> PathSafetyUtils.validateDirectChildName("absolute"));
            assertThrows(IllegalArgumentException.class,
                () -> PathSafetyUtils.validateDirectChildName("rooted"));
            assertThrows(IllegalArgumentException.class,
                () -> PathSafetyUtils.validateDirectChildName("nested"));
            IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> PathSafetyUtils.validateDirectChildName("invalid"));
            assertEquals(invalidPath, actual.getCause());
        }
    }
    
    @Test
    void testResolveDirectChildRejectsProviderEscapeAndInvalidPath() {
        Path basePath = Mockito.mock(Path.class);
        Path escapingChildPath = Mockito.mock(Path.class);
        Path absoluteChildPath = Mockito.mock(Path.class);
        Path rootedChildPath = Mockito.mock(Path.class);
        Path nestedChildPath = Mockito.mock(Path.class);
        Path targetPath = Mockito.mock(Path.class);
        FileSystem fileSystem = Mockito.mock(FileSystem.class);
        Mockito.when(basePath.toAbsolutePath()).thenReturn(basePath);
        Mockito.when(basePath.normalize()).thenReturn(basePath);
        Mockito.when(basePath.getFileSystem()).thenReturn(fileSystem);
        Mockito.when(fileSystem.getPath("escape")).thenReturn(escapingChildPath);
        Mockito.when(fileSystem.getPath("absolute")).thenReturn(absoluteChildPath);
        Mockito.when(fileSystem.getPath("rooted")).thenReturn(rootedChildPath);
        Mockito.when(fileSystem.getPath("nested")).thenReturn(nestedChildPath);
        Mockito.when(basePath.resolve(escapingChildPath)).thenReturn(targetPath);
        Mockito.when(basePath.resolve(absoluteChildPath)).thenReturn(targetPath);
        Mockito.when(basePath.resolve(rootedChildPath)).thenReturn(targetPath);
        Mockito.when(basePath.resolve(nestedChildPath)).thenReturn(targetPath);
        Mockito.when(targetPath.normalize()).thenReturn(targetPath);
        Mockito.when(targetPath.toAbsolutePath()).thenReturn(targetPath);
        Mockito.when(escapingChildPath.getNameCount()).thenReturn(1);
        Mockito.when(absoluteChildPath.isAbsolute()).thenReturn(true);
        Mockito.when(rootedChildPath.getRoot()).thenReturn(Mockito.mock(Path.class));
        Mockito.when(nestedChildPath.getNameCount()).thenReturn(2);
        
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(basePath, "escape"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(basePath, "absolute"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(basePath, "rooted"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(basePath, "nested"));
        
        InvalidPathException invalidPath = new InvalidPathException("invalid", "mock failure");
        Mockito.when(fileSystem.getPath("invalid")).thenThrow(invalidPath);
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(basePath, "invalid"));
        assertEquals(invalidPath, actual.getCause());
    }
    
    @Test
    void testResolveArchiveEntryRejectsProviderEscapeAndInvalidPath() {
        Path basePath = Mockito.mock(Path.class);
        Path escapingRelativePath = Mockito.mock(Path.class);
        Path absoluteRelativePath = Mockito.mock(Path.class);
        Path rootedRelativePath = Mockito.mock(Path.class);
        Path equalRelativePath = Mockito.mock(Path.class);
        Path targetPath = Mockito.mock(Path.class);
        FileSystem fileSystem = Mockito.mock(FileSystem.class);
        Mockito.when(basePath.toAbsolutePath()).thenReturn(basePath);
        Mockito.when(basePath.normalize()).thenReturn(basePath);
        Mockito.when(basePath.getFileSystem()).thenReturn(fileSystem);
        Mockito.when(fileSystem.getPath("escape/file")).thenReturn(escapingRelativePath);
        Mockito.when(fileSystem.getPath("absolute/file")).thenReturn(absoluteRelativePath);
        Mockito.when(fileSystem.getPath("rooted/file")).thenReturn(rootedRelativePath);
        Mockito.when(fileSystem.getPath("equal/file")).thenReturn(equalRelativePath);
        Mockito.when(basePath.resolve(escapingRelativePath)).thenReturn(targetPath);
        Mockito.when(basePath.resolve(absoluteRelativePath)).thenReturn(targetPath);
        Mockito.when(basePath.resolve(rootedRelativePath)).thenReturn(targetPath);
        Mockito.when(basePath.resolve(equalRelativePath)).thenReturn(basePath);
        Mockito.when(targetPath.normalize()).thenReturn(targetPath);
        Mockito.when(targetPath.toAbsolutePath()).thenReturn(targetPath);
        Mockito.when(absoluteRelativePath.isAbsolute()).thenReturn(true);
        Mockito.when(rootedRelativePath.getRoot()).thenReturn(Mockito.mock(Path.class));
        
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(basePath, "escape/file"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(basePath, "absolute/file"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(basePath, "rooted/file"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(basePath, "equal/file"));
        
        InvalidPathException invalidPath = new InvalidPathException("invalid/file", "mock failure");
        Mockito.when(fileSystem.getPath("invalid/file")).thenThrow(invalidPath);
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(basePath, "invalid/file"));
        assertEquals(invalidPath, actual.getCause());
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
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.validateDirectChildName(childName));
    }
    
    @Test
    void testRejectNullBasePath() {
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveDirectChild(null, "config"));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.resolveArchiveEntry(null, "snapshot/config"));
    }
    
    @Test
    void testRejectNullCharacter() {
        String unsafeName = "unsafe" + (char) 0 + "name";
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.validateDirectChildName(unsafeName));
        assertThrows(IllegalArgumentException.class,
            () -> PathSafetyUtils.normalizeArchiveEntryName(unsafeName));
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
