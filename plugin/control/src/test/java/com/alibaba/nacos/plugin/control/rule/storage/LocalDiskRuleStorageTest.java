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

package com.alibaba.nacos.plugin.control.rule.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDiskRuleStorageTest {
    
    @TempDir
    private Path tempDir;
    
    private LocalDiskRuleStorage storage;
    
    @BeforeEach
    void setUp() {
        storage = new LocalDiskRuleStorage();
        storage.setLocalRuleBaseDir(tempDir.toString());
    }
    
    @Test
    void testSaveReadAndDeleteTpsRule() throws IOException {
        String content = "{\"pointName\":\"ConfigQuery\"}";
        storage.saveTpsRule("ConfigQuery", content);
        assertEquals(content, storage.getTpsRule("ConfigQuery"));
        storage.saveTpsRule("ConfigQuery", null);
        assertNull(storage.getTpsRule("ConfigQuery"));
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../outside", "nested/rule", "nested\\rule",
        "/absolute"})
    void testRejectsUnsafePointName(String pointName) {
        assertThrows(IllegalArgumentException.class,
            () -> storage.saveTpsRule(pointName, "rule"));
        assertThrows(IllegalArgumentException.class, () -> storage.getTpsRule(pointName));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {".hidden", "rule..name"})
    void testAllowsDotsWithoutDirectoryControlMeaning(String pointName) throws IOException {
        storage.saveTpsRule(pointName, "rule");
        assertEquals("rule", storage.getTpsRule(pointName));
        storage.saveTpsRule(pointName, null);
        assertNull(storage.getTpsRule(pointName));
    }
    
    @Test
    void testDirectoryControlPointNamesCannotDeleteParentDirectories() throws IOException {
        Path tpsMarker = tempDir.resolve("data").resolve("tps").resolve("marker");
        Path dataMarker = tempDir.resolve("data").resolve("marker");
        Files.createDirectories(tpsMarker.getParent());
        Files.write(tpsMarker, "tps".getBytes(StandardCharsets.UTF_8));
        Files.write(dataMarker, "data".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> storage.saveTpsRule(".", null));
        assertThrows(IllegalArgumentException.class, () -> storage.saveTpsRule("..", null));
        assertTrue(Files.isRegularFile(tpsMarker));
        assertTrue(Files.isRegularFile(dataMarker));
    }
}
