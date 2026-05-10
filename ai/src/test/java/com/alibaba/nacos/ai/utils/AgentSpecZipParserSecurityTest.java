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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Security tests: verify that {@link AgentSpecZipParser} is hardened against malicious Zip uploads.
 *
 * <p>Covers three attack vectors:
 * <ul>
 *   <li>Zip Slip (path-traversal attack): entry name contains "../" sequences</li>
 *   <li>Zip bomb (decompressed-size attack): decompressed total exceeds the configured limit</li>
 *   <li>Entry-count attack: too many entries inside a single zip</li>
 * </ul>
 *
 * @author nacos
 */
class AgentSpecZipParserSecurityTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String VALID_MANIFEST = "{\"version\":\"1.0\",\"description\":\"test\","
        + "\"worker\":{\"suggested_name\":\"test-worker\"}}";
    
    // ---- Test 1: path-traversal attack (Zip Slip) ----
    
    @Test
    void testPathTraversalAttackShouldBeRejected() throws IOException {
        // Build a malicious zip containing a path-traversal entry.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // Legitimate manifest.json.
            addZipEntry(zos, "manifest.json", VALID_MANIFEST.getBytes(StandardCharsets.UTF_8));
            // Malicious entry: path traversal.
            addZipEntry(zos, "../../etc/passwd", "root:x:0:0".getBytes(StandardCharsets.UTF_8));
        }
        byte[] zipBytes = baos.toByteArray();
        
        // Expectation: parsing should fail and reject the traversal entry.
        try {
            AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
            fail(
                "Expected the path-traversal zip to be rejected, but parsing succeeded (security hole!)");
        } catch (SecurityException | NacosApiException e) {
            // Expected: traversal rejected.
            System.out
                .println("[PASS] Path-traversal attack correctly rejected: " + e.getMessage());
        }
    }
    
    @Test
    void testAbsolutePathEntryShouldBeRejected() throws IOException {
        // Build a malicious zip containing an absolute-path entry.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json", VALID_MANIFEST.getBytes(StandardCharsets.UTF_8));
            // Malicious entry: absolute path.
            addZipEntry(zos, "/etc/shadow", "malicious".getBytes(StandardCharsets.UTF_8));
        }
        byte[] zipBytes = baos.toByteArray();
        
        try {
            AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
            fail(
                "Expected the absolute-path zip to be rejected, but parsing succeeded (security hole!)");
        } catch (SecurityException | NacosApiException e) {
            System.out.println("[PASS] Absolute-path attack correctly rejected: " + e.getMessage());
        }
    }
    
    // ---- Test 2: zip bomb (decompressed-size attack) ----
    
    @Test
    void testZipBombShouldBeRejected() throws IOException {
        // Build a zip whose decompressed size is huge: 110 entries x 1 MB = 110 MB,
        // which exceeds the 50 MB MAX_TOTAL_UNCOMPRESSED_BYTES limit inside the parser.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json", VALID_MANIFEST.getBytes(StandardCharsets.UTF_8));
            // Create many large entries.
            byte[] largeContent = new byte[1024 * 1024]; // 1 MB of zeros (compresses extremely well).
            for (int i = 0; i < 110; i++) {
                addZipEntry(zos, "config/large_file_" + i + ".txt", largeContent);
            }
        }
        byte[] zipBytes = baos.toByteArray();
        
        try {
            AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
            fail("Expected the zip bomb to be rejected, but parsing succeeded (security hole!)");
        } catch (NacosApiException e) {
            // Verify the rejection reason is the decompressed-size limit.
            boolean isDecompressSizeError = e.getMessage().contains("decompressed size")
                || e.getMessage().contains("exceeds limit");
            boolean isZipSizeError = e.getMessage().contains("must not exceed");
            assertTrue(isDecompressSizeError || isZipSizeError,
                "Expected rejection due to the decompressed-size limit, actual error: "
                    + e.getMessage());
            System.out.println("[PASS] Zip-bomb attack correctly rejected: " + e.getMessage());
        } catch (Exception e) {
            // OutOfMemoryError or any other exception means the guard is missing.
            fail("Zip bomb was not properly guarded, unexpected exception: "
                + e.getClass().getName() + ": " + e.getMessage());
        }
    }
    
    // ---- Test 3: entry-count attack ----
    
    @Test
    void testTooManyEntriesShouldBeRejected() throws IOException {
        // Build a zip with more than 500 entries (the MAX_ZIP_ENTRIES cap).
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json", VALID_MANIFEST.getBytes(StandardCharsets.UTF_8));
            byte[] smallContent = "x".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 600; i++) {
                addZipEntry(zos, "config/entry_" + i + ".txt", smallContent);
            }
        }
        byte[] zipBytes = baos.toByteArray();
        
        try {
            AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
            fail(
                "Expected the too-many-entries zip to be rejected, but parsing succeeded (security hole!)");
        } catch (NacosApiException e) {
            boolean isEntryCountError = e.getMessage().contains("too many entries")
                || e.getMessage().contains("max");
            assertTrue(isEntryCountError,
                "Expected rejection due to the entry-count limit, actual error: " + e.getMessage());
            System.out.println("[PASS] Entry-count attack correctly rejected: " + e.getMessage());
        }
    }
    
    // ---- Helpers ----
    
    private static void addZipEntry(ZipOutputStream zos, String name, byte[] data)
        throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
