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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentSpecZipParser}.
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecZipParserPathsTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static List<List<String>> validResourcePathSets() {
        return Arrays.asList(
            Arrays.asList("config/readme.md"),
            Arrays.asList("skills/my-skill/foo.md", "crons/job.md"),
            Arrays.asList("config/a.md", "config/b.md"));
    }
    
    private static List<List<String>> macOsMetadataPathSets() {
        return Arrays.asList(
            Arrays.asList("__MACOSX/._x.json", ".DS_Store"),
            Arrays.asList("config/.DS_Store", "skills/._note.md"),
            Arrays.asList("._foo.txt", "__MACOSX/._bar.json", "crons/.DS_Store"));
    }
    
    private static String[] workerNames() {
        return new String[] {"workerA", "myworker", "testname"};
    }
    
    /**
     * macOS metadata filtering.
     *
     * <p><b>Validates: Requirement 1.5</b></p>
     */
    @Test
    void macOsMetadataFilesAreFilteredOut() throws NacosApiException, IOException {
        for (List<String> resourcePaths : validResourcePathSets()) {
            for (List<String> metadataPaths : macOsMetadataPathSets()) {
                for (String workerName : workerNames()) {
                    String manifestJson = "{\"version\":\"1.0\",\"worker\":{\"suggested_name\":\""
                        + workerName + "\"}}";
                    
                    byte[] zipBytes = buildZip(manifestJson, resourcePaths, metadataPaths);
                    AgentSpec result =
                        AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
                    
                    assertNotNull(result);
                    Map<String, AgentSpecResource> resources = result.getResource();
                    assertNotNull(resources);
                    
                    for (Map.Entry<String, AgentSpecResource> entry : resources.entrySet()) {
                        String key = entry.getKey();
                        AgentSpecResource res = entry.getValue();
                        
                        assertFalse(key.contains("__MACOSX"),
                            "Resource key should not contain __MACOSX: " + key);
                        assertFalse(key.contains(".DS_Store"),
                            "Resource key should not contain .DS_Store: " + key);
                        assertFalse(".DS_Store".equals(res.getName()),
                            "Resource name should not be .DS_Store");
                        assertFalse(res.getName().startsWith("._"),
                            "Resource name should not start with ._: " + res.getName());
                    }
                    
                    for (String path : resourcePaths) {
                        String fileName = path.contains("/")
                            ? path.substring(path.lastIndexOf('/') + 1) : path;
                        boolean found = resources.values().stream()
                            .anyMatch(r -> r.getName().equals(fileName));
                        assertTrue(found, "Valid resource file should be preserved: " + path);
                    }
                }
            }
        }
    }
    
    private static byte[] buildZip(String manifestJson, List<String> resourcePaths,
        List<String> metadataPaths) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "manifest.json",
                manifestJson.getBytes(StandardCharsets.UTF_8));
            for (String path : resourcePaths) {
                addZipEntry(zos, path,
                    ("content of " + path).getBytes(StandardCharsets.UTF_8));
            }
            for (String path : metadataPaths) {
                addZipEntry(zos, path,
                    "macos-metadata".getBytes(StandardCharsets.UTF_8));
            }
        }
        return baos.toByteArray();
    }
    
    private static void addZipEntry(ZipOutputStream zos, String name,
        byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
