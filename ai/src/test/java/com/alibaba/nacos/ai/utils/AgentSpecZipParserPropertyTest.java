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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for {@link AgentSpecZipParser}.
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecZipParserPropertyTest {

    private static final String NAMESPACE_ID = "test-ns";
    
    /**
     * Property 2: macOS metadata filtering.
     *
     * <p>For any zip containing a mix of valid resource files and macOS metadata entries
     * ({@code __MACOSX/*}, {@code .DS_Store}, {@code ._*} prefixed files), parsing via
     * {@link AgentSpecZipParser#parseAgentSpecFromZip(byte[], String)} should produce a result
     * that contains none of the macOS metadata entries while preserving all other valid
     * resource files.</p>
     *
     * <p><b>Validates: Requirement 1.5</b></p>
     */
    @Property
    void macOsMetadataFilesAreFilteredOut(
            @ForAll("validResourcePaths") List<String> resourcePaths,
            @ForAll("macOsMetadataPaths") List<String> metadataPaths,
            @ForAll("workerNames") String workerName) throws NacosApiException, IOException {
        
        String manifestJson = "{\"version\":\"1.0\",\"worker\":{\"suggested_name\":\""
                + workerName + "\"}}";
        
        byte[] zipBytes = buildZip(manifestJson, resourcePaths, metadataPaths);
        AgentSpec result = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, NAMESPACE_ID);
        
        assertNotNull(result);
        Map<String, AgentSpecResource> resources = result.getResource();
        assertNotNull(resources);
        
        // No resource key or value should reference any macOS metadata
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
        
        // All non-metadata resource files should be present
        for (String path : resourcePaths) {
            String fileName = path.contains("/")
                    ? path.substring(path.lastIndexOf('/') + 1) : path;
            boolean found = resources.values().stream()
                    .anyMatch(r -> r.getName().equals(fileName));
            assertTrue(found, "Valid resource file should be preserved: " + path);
        }
    }
    
    @Provide
    Arbitrary<String> workerNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15);
    }
    
    @Provide
    Arbitrary<List<String>> validResourcePaths() {
        Arbitrary<String> dirs = Arbitraries.of("config", "skills/my-skill", "crons");
        Arbitrary<String> fileNames = Arbitraries.strings().alpha()
                .ofMinLength(2).ofMaxLength(8).map(s -> s + ".md");
        Arbitrary<String> paths = Combinators.combine(dirs, fileNames)
                .as((dir, file) -> dir + "/" + file);
        return paths.list().ofMinSize(1).ofMaxSize(5).uniqueElements();
    }
    
    @Provide
    Arbitrary<List<String>> macOsMetadataPaths() {
        Arbitrary<String> macosxEntries = Arbitraries.strings().alpha()
                .ofMinLength(2).ofMaxLength(8)
                .map(s -> "__MACOSX/._" + s + ".json");
        Arbitrary<String> dsStoreRoot = Arbitraries.just(".DS_Store");
        Arbitrary<String> dsStoreSubdir = Arbitraries.of("config", "skills", "crons")
                .map(dir -> dir + "/.DS_Store");
        Arbitrary<String> appleDoubleRoot = Arbitraries.strings().alpha()
                .ofMinLength(2).ofMaxLength(8)
                .map(s -> "._" + s + ".txt");
        Arbitrary<String> appleDoubleSubdir = Arbitraries.of("config", "skills")
                .flatMap(dir -> Arbitraries.strings().alpha()
                        .ofMinLength(2).ofMaxLength(8)
                        .map(s -> dir + "/._" + s + ".md"));
        
        return Arbitraries.oneOf(macosxEntries, dsStoreRoot, dsStoreSubdir,
                appleDoubleRoot, appleDoubleSubdir)
                .list().ofMinSize(1).ofMaxSize(6).uniqueElements();
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
