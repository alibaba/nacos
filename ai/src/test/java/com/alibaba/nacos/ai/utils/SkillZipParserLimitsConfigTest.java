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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that {@link SkillZipParser} honors the configurable upper bounds for
 * ZIP entry count and total decompressed size while preserving the historical defaults
 * (500 entries / 50 MB) when no override is set. The configurability was added in response
 * to legitimately-large skill uploads that hit the previously hard-coded limits.
 */
class SkillZipParserLimitsConfigTest {
    
    private static final String VALID_SKILL_MD =
        "---\nname: sample\ndescription: sample skill\n---\n\nbody\n";
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private ConfigurableEnvironment previousEnvironment;
    
    @BeforeEach
    void setUp() {
        previousEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(previousEnvironment);
    }
    
    @Test
    void resolveMaxZipEntriesReturnsDefaultWhenUnset() {
        assertEquals(SkillZipParser.DEFAULT_MAX_ZIP_ENTRIES, SkillZipParser.resolveMaxZipEntries());
    }
    
    @Test
    void resolveMaxZipEntriesHonorsPositiveOverride() {
        ((MockEnvironment) EnvUtil.getEnvironment())
            .setProperty(SkillZipParser.CONFIG_MAX_ZIP_ENTRIES, "2000");
        assertEquals(2000, SkillZipParser.resolveMaxZipEntries());
    }
    
    @Test
    void resolveMaxZipEntriesIgnoresNonPositiveOverride() {
        MockEnvironment env = (MockEnvironment) EnvUtil.getEnvironment();
        env.setProperty(SkillZipParser.CONFIG_MAX_ZIP_ENTRIES, "0");
        assertEquals(SkillZipParser.DEFAULT_MAX_ZIP_ENTRIES, SkillZipParser.resolveMaxZipEntries());
        
        env.setProperty(SkillZipParser.CONFIG_MAX_ZIP_ENTRIES, "-10");
        assertEquals(SkillZipParser.DEFAULT_MAX_ZIP_ENTRIES, SkillZipParser.resolveMaxZipEntries());
    }
    
    @Test
    void resolveMaxZipEntriesFallsBackToDefaultWhenEnvironmentMissing() {
        EnvUtil.setEnvironment(null);
        assertEquals(SkillZipParser.DEFAULT_MAX_ZIP_ENTRIES, SkillZipParser.resolveMaxZipEntries());
    }
    
    @Test
    void resolveMaxUncompressedBytesReturnsDefaultWhenUnset() {
        long expectedBytes =
            (long) SkillZipParser.DEFAULT_MAX_UNCOMPRESSED_SIZE_MB * 1024L * 1024L;
        assertEquals(expectedBytes, SkillZipParser.resolveMaxUncompressedBytes());
    }
    
    @Test
    void resolveMaxUncompressedBytesHonorsPositiveOverride() {
        ((MockEnvironment) EnvUtil.getEnvironment())
            .setProperty(SkillZipParser.CONFIG_MAX_UNCOMPRESSED_SIZE_MB, "200");
        assertEquals(200L * 1024L * 1024L, SkillZipParser.resolveMaxUncompressedBytes());
    }
    
    @Test
    void resolveMaxUncompressedBytesIgnoresNonPositiveOverride() {
        MockEnvironment env = (MockEnvironment) EnvUtil.getEnvironment();
        env.setProperty(SkillZipParser.CONFIG_MAX_UNCOMPRESSED_SIZE_MB, "0");
        long expectedBytes =
            (long) SkillZipParser.DEFAULT_MAX_UNCOMPRESSED_SIZE_MB * 1024L * 1024L;
        assertEquals(expectedBytes, SkillZipParser.resolveMaxUncompressedBytes());
    }
    
    @Test
    void parseSkillFromZipUsesConfiguredEntryLimit() throws IOException {
        ((MockEnvironment) EnvUtil.getEnvironment())
            .setProperty(SkillZipParser.CONFIG_MAX_ZIP_ENTRIES, "3");
        byte[] zipBytes = buildZipWithEntryCount(5);
        
        try {
            SkillZipParser.parseSkillFromZip(zipBytes, NAMESPACE_ID);
            fail("Expected the configured-entry-limit guard to reject this zip");
        } catch (NacosApiException e) {
            assertTrue(e.getMessage().contains("too many entries"),
                "NacosApiException should describe the entry-count violation, got: "
                    + e.getMessage());
            assertTrue(e.getMessage().contains("max 3"),
                "Error message should report the configured maximum (3), got: " + e.getMessage());
        }
    }
    
    @Test
    void parseSkillFromZipAcceptsLargeEntryCountWhenLimitRaised() throws Exception {
        // 600 entries exceeds DEFAULT_MAX_ZIP_ENTRIES (=500), but a raised limit must accept it.
        ((MockEnvironment) EnvUtil.getEnvironment())
            .setProperty(SkillZipParser.CONFIG_MAX_ZIP_ENTRIES, "1000");
        byte[] zipBytes = buildZipWithEntryCount(600);
        
        // parseSkillFromZip succeeds (returns a skill) or fails on a non-limit reason.
        // We only assert that NO entry-count exception is raised at the configured higher limit.
        try {
            SkillZipParser.parseSkillFromZip(zipBytes, NAMESPACE_ID);
        } catch (NacosApiException e) {
            assertTrue(!e.getMessage().contains("too many entries"),
                "Raising the limit must lift the entry-count guard, but message was: "
                    + e.getMessage());
        }
    }
    
    @Test
    void parseSkillFromZipUsesConfiguredUncompressedSizeLimit() throws IOException {
        ((MockEnvironment) EnvUtil.getEnvironment())
            .setProperty(SkillZipParser.CONFIG_MAX_UNCOMPRESSED_SIZE_MB, "1");
        // ~2 MB of decompressed payload — should be rejected at the 1 MB configured limit.
        byte[] zipBytes = buildZipWithOneMbEntries(2);
        
        try {
            SkillZipParser.parseSkillFromZip(zipBytes, NAMESPACE_ID);
            fail("Expected the configured uncompressed-size limit to reject this zip");
        } catch (NacosApiException e) {
            assertNotNull(e.getMessage(), "NacosApiException message must not be null");
            assertTrue(e.getMessage().contains("decompressed size exceeds limit"),
                "Error message should describe the decompressed-size violation, got: "
                    + e.getMessage());
            assertTrue(e.getMessage().contains("1MB"),
                "Error message should report the configured maximum (1MB), got: "
                    + e.getMessage());
        }
    }
    
    // -------- Helpers --------
    
    private static byte[] buildZipWithEntryCount(int entryCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "SKILL.md", VALID_SKILL_MD.getBytes(StandardCharsets.UTF_8));
            byte[] small = "x".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < entryCount; i++) {
                addZipEntry(zos, "assets/entry_" + i + ".txt", small);
            }
        }
        return baos.toByteArray();
    }
    
    private static byte[] buildZipWithOneMbEntries(int count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "SKILL.md", VALID_SKILL_MD.getBytes(StandardCharsets.UTF_8));
            byte[] oneMegabyteOfZeros = new byte[1024 * 1024];
            for (int i = 0; i < count; i++) {
                addZipEntry(zos, "assets/blob_" + i + ".bin", oneMegabyteOfZeros);
            }
        }
        return baos.toByteArray();
    }
    
    private static void addZipEntry(ZipOutputStream zos, String name, byte[] data)
        throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
