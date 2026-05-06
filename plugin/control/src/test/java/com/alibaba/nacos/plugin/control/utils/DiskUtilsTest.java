/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskUtilsTest {
    
    private static final String TMP_PATH =
            EnvUtils.getNacosHome() + File.separator + "data" + File.separator + "tmp" + File.separator;
    
    private static File testFile;
    
    @BeforeAll
    static void setup() throws IOException {
        testFile = Files.createTempFile("nacostmp", ".ut").toFile();
        ;
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        testFile.deleteOnExit();
    }
    
    @Test
    void testReadFile() {
        assertNotNull(DiskUtils.readFile(testFile));
    }
    
    @Test
    void testWriteFile() {
        assertTrue(DiskUtils.writeFile(testFile, "unit test".getBytes(StandardCharsets.UTF_8), false));
        assertEquals("unit test", DiskUtils.readFile(testFile));
    }
    
    @Test
    void testDeleteQuietly() throws IOException {
        File tmpFile = Files.createTempFile(UUID.randomUUID().toString(), ".ut").toFile();
        DiskUtils.deleteQuietly(tmpFile);
        assertFalse(tmpFile.exists());
    }
}
