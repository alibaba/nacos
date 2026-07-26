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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpecSeedArchiveReaderTest {
    
    @Test
    void shouldBuildStandaloneAgentSpecZipWithNestedLayout() throws Exception {
        String manifest = "{\"version\":\"1.0\",\"tags\":[\"vendor\"],"
            + "\"worker\":{\"suggested_name\":\"演示坐席\"}}";
        byte[] archive = buildArchive(
            new ArchiveEntry("vendor/demo-agent/manifest.json", manifest),
            new ArchiveEntry("vendor/demo-agent/config/SOUL.md", "# soul\n"));
        
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> actual =
            AgentSpecSeedArchiveReader.read(new ByteArrayInputStream(archive));
        
        assertEquals(1, actual.size());
        assertEquals("演示坐席", actual.get(0).getAgentSpecName());
        assertEquals("vendor", actual.get(0).getFrom());
        
        AgentSpec spec =
            AgentSpecZipParser.parseAgentSpecFromZip(actual.get(0).getZipBytes(), "public");
        assertEquals("演示坐席", spec.getName());
        assertEquals("[\"vendor\"]", spec.getBizTags());
        assertNotNull(spec.getResource());
        assertFalse(spec.getResource().isEmpty());
    }
    
    @Test
    void shouldSkipDuplicateAgentSpecNames() throws Exception {
        String manifest = "{\"version\":\"1.0\",\"worker\":{\"suggested_name\":\"同名坐席\"}}";
        byte[] archive = buildArchive(
            new ArchiveEntry("source-a/demo/manifest.json", manifest),
            new ArchiveEntry("source-b/demo/manifest.json", manifest));
        
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> actual =
            AgentSpecSeedArchiveReader.read(new ByteArrayInputStream(archive));
        
        assertEquals(1, actual.size());
        assertEquals("同名坐席", actual.get(0).getAgentSpecName());
    }
    
    @Test
    void shouldParseNestedFromPath() throws Exception {
        String manifest =
            "{\"version\":\"1.0\",\"worker\":{\"suggested_name\":\"find-agentspec\"}}";
        byte[] archive = buildArchive(
            new ArchiveEntry("github.com/nacos/find-agentspec/manifest.json", manifest));
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> actual =
            AgentSpecSeedArchiveReader.read(new ByteArrayInputStream(archive));
        assertEquals(1, actual.size());
        assertEquals("github.com/nacos", actual.get(0).getFrom());
    }
    
    @Test
    void shouldParseBundledAgentspecArchive() throws Exception {
        ClassPathResource resource = new ClassPathResource("bootstrap/agentspec-data.zip");
        Assumptions.assumeTrue(resource.exists(),
            "bootstrap/agentspec-data.zip is not bundled in this test runtime");
        try (InputStream inputStream = resource.getInputStream()) {
            List<AgentSpecSeedArchiveReader.AgentSpecPackage> actual =
                AgentSpecSeedArchiveReader.read(inputStream);
            
            assertTrue(actual.size() > 100);
            Set<String> names = new HashSet<>();
            for (AgentSpecSeedArchiveReader.AgentSpecPackage each : actual) {
                names.add(each.getAgentSpecName());
                AgentSpec spec =
                    AgentSpecZipParser.parseAgentSpecFromZip(each.getZipBytes(), "public");
                assertNotNull(spec);
                assertEquals(each.getAgentSpecName(), spec.getName());
                assertTrue(spec.getDescription() != null && !spec.getDescription().isBlank());
            }
            assertTrue(names.contains("前端开发者"));
            assertTrue(names.contains("UX 研究员"));
            assertTrue(names.contains("销售教练"));
            assertTrue(names.contains("广告创意策略师"));
            assertTrue(names.contains("Unreal 技术美术"));
            assertTrue(names.contains("招聘专家"));
            assertTrue(names.contains("招聘专家（specialized）"));
            assertTrue(names.contains("B站内容策略师"));
            assertTrue(names.contains("B站内容策略师（marketing）"));
            assertFalse(names.contains("find-skills"));
        }
    }
    
    private static byte[] buildArchive(ArchiveEntry... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (ArchiveEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.path));
                zos.write(entry.content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
    
    private static final class ArchiveEntry {
        
        private final String path;
        
        private final String content;
        
        private ArchiveEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }
}
