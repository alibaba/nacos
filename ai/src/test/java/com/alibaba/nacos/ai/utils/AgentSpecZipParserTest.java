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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AgentSpecZipParser} error scenarios.
 *
 * <p>Validates: Requirements 1.6, 1.7, 1.8, 1.9</p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecZipParserTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    // ---- Requirement 1.7: Missing manifest.json ----
    
    @Test
    void testParseZipWithoutManifestJson() throws IOException {
        byte[] zipBytes = createZipWithoutManifest();
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID));
        assertTrue(exception.getMessage().contains("manifest.json"),
            "Error should mention manifest.json, got: " + exception.getMessage());
    }
    
    // ---- Requirement 1.8: Empty or missing worker.suggested_name ----
    
    @Test
    void testParseZipWithEmptySuggestedName() throws IOException {
        byte[] zipBytes = createZipWithManifest(
            "{\"version\":\"1.0\",\"worker\":{\"suggested_name\":\"\"}}");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID));
        assertTrue(exception.getMessage().contains("suggested_name"),
            "Error should mention suggested_name, got: " + exception.getMessage());
    }
    
    @Test
    void testParseZipWithMissingSuggestedName() throws IOException {
        byte[] zipBytes = createZipWithManifest(
            "{\"version\":\"1.0\",\"worker\":{}}");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID));
        assertTrue(exception.getMessage().contains("suggested_name"),
            "Error should mention suggested_name, got: " + exception.getMessage());
    }
    
    @Test
    void testParseZipWithMissingWorkerSection() throws IOException {
        byte[] zipBytes = createZipWithManifest("{\"version\":\"1.0\"}");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID));
        assertTrue(exception.getMessage().contains("suggested_name"),
            "Error should mention suggested_name, got: " + exception.getMessage());
    }
    
    @Test
    void testParseZipWithBlankSuggestedName() throws IOException {
        byte[] zipBytes = createZipWithManifest(
            "{\"version\":\"1.0\",\"worker\":{\"suggested_name\":\"   \"}}");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID));
        assertTrue(exception.getMessage().contains("suggested_name"),
            "Error should mention suggested_name, got: " + exception.getMessage());
    }
    
    // ---- Requirement 1.9: Corrupted / unreadable zip ----
    
    @Test
    void testParseCorruptedZip() {
        byte[] corruptedBytes = "this is not a zip file".getBytes(StandardCharsets.UTF_8);
        
        assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(corruptedBytes, NAMESPACE_ID));
    }
    
    @Test
    void testParseEmptyZipBytes() {
        byte[] emptyBytes = new byte[0];
        
        assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(emptyBytes, NAMESPACE_ID));
    }
    
    @Test
    void testParseNullZipBytes() {
        assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(null, NAMESPACE_ID));
    }
    
    // ---- Requirement 1.6: Zip exceeding 50MB size limit ----
    
    @Test
    void testParseZipExceedsSizeLimit() throws IOException {
        byte[] validZip = createValidAgentSpecZip();
        int overSize = (int) (Constants.AgentSpecs.MAX_UPLOAD_ZIP_BYTES + 1024);
        byte[] largeZip = new byte[overSize];
        System.arraycopy(validZip, 0, largeZip, 0, validZip.length);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentSpecZipParser.parseAgentSpecFromZip(largeZip, NAMESPACE_ID));
        assertTrue(exception.getMessage().contains("must not exceed"),
            "Error should mention size limit, got: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("50"),
            "Error should mention 50MB, got: " + exception.getMessage());
    }
    
    // ---- Positive baseline: valid zip parses successfully ----
    
    @Test
    void testParseValidZip() throws Exception {
        byte[] zipBytes = createValidAgentSpecZip();
        
        var result = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
        
        assertNotNull(result);
        assertNotNull(result.getName());
        assertEquals("Test worker description", result.getDescription());
        assertEquals("[\"design\",\"research\"]", result.getBizTags());
        assertNotNull(result.getContent());
    }
    
    /** HiClaw-style worker package with config/, crons/, tool-analysis.json (see data/agentspec sample layout). */
    @Test
    void testParseValidZipWithHiClawLayout() throws Exception {
        String manifest = "{\"version\":\"1.0\",\"description\":\"Sample worker description\","
            + "\"tags\":[\"game-development\",\"unreal-engine\"],"
            + "\"worker\":{\"suggested_name\":\"Unreal 技术美术\"}}";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "config/SOUL.md", "soul".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "config/AGENTS.md", "agents".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "config/IDENTITY.md", "id".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "config/MEMORY.md", "memory".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "crons/jobs.json", "[]".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "tool-analysis.json", "{}".getBytes(StandardCharsets.UTF_8));
        }
        byte[] zipBytes = baos.toByteArray();
        
        var result = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
        
        assertNotNull(result);
        assertEquals("Unreal 技术美术", result.getName());
        assertEquals("Sample worker description", result.getDescription());
        assertEquals("[\"game-development\",\"unreal-engine\"]", result.getBizTags());
        assertNotNull(result.getResource());
        assertTrue(result.getResource().size() >= 6);
    }
    
    // ---- Helper methods ----
    
    private byte[] createValidAgentSpecZip() throws IOException {
        String manifest = "{\"version\":\"1.0\",\"description\":\"Test worker description\","
            + "\"tags\":[\"design\",\"research\"],"
            + "\"worker\":{\"suggested_name\":\"UX 研究员\"}}";
        return createZipWithManifest(manifest);
    }
    
    private byte[] createZipWithManifest(String manifestContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json", manifestContent.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithoutManifest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "config/SOUL.md", "some content".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "README.md", "readme".getBytes(StandardCharsets.UTF_8));
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
