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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.DefaultImportHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import javax.net.ssl.SSLSession;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link SkillWellKnownImportService}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class SkillWellKnownImportServiceTest {
    
    private static final String ENDPOINT = "https://registry.example.com/registry/public";
    
    private static final String LEGACY_ENDPOINT = "https://registry.example.com/legacy";
    
    private static final String VERSION_020_ENDPOINT = "https://registry.example.com/v2";
    
    private static final String BAD_VERSION_020_ENDPOINT = "https://registry.example.com/bad-v2";
    
    private static final String SCHEMA_0_2 =
        "https://schemas.agentskills.io/discovery/0.2.0/schema.json";
    
    @Mock
    private HttpClient httpClient;
    
    private SkillWellKnownImportService importService;
    
    @BeforeEach
    void setUp() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
        importService = new SkillWellKnownImportService(new DefaultImportHttpClient(httpClient,
            host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")}));
    }
    
    @Test
    void testSearchReturnsFilteredSkillCandidates() throws Exception {
        AiResourceImportContext context = newContext();
        context.setQuery("demo");
        context.setLimit(10);
        
        AiResourceImportCandidatePage result = importService.search(context);
        
        assertEquals(1, result.getItems().size());
        assertEquals("demo-skill", result.getItems().get(0).getExternalId());
        assertEquals(SkillWellKnownImportService.RESOURCE_TYPE_SKILL,
            result.getItems().get(0).getResourceType());
        assertEquals("0.1.0", result.getItems().get(0).getMetadata().get("schemaVersion"));
        assertEquals("2", result.getItems().get(0).getMetadata().get("fileCount"));
        assertFalse(result.isHasMore());
    }
    
    @Test
    void testFetchReturnsSkillZipArtifact() throws Exception {
        AiResourceImportArtifact result = importService.fetch(newContext(), item("demo-skill"));
        
        assertEquals(SkillWellKnownImportService.RESOURCE_TYPE_SKILL, result.getResourceType());
        assertEquals(AiResourceImportPayloadKind.SKILL_ZIP, result.getPayloadKind());
        assertEquals("demo-skill", result.getName());
        assertNull(result.getVersion());
        assertZipEntryContains(result.getPayload(), "demo-skill/SKILL.md", "name: demo-skill");
        assertZipEntryContains(result.getPayload(), "demo-skill/docs/guide.md", "# Guide");
    }
    
    @Test
    void testSearchFallsBackToLegacyWellKnownSkillsPath() throws Exception {
        AiResourceImportContext context = newContext(LEGACY_ENDPOINT);
        
        AiResourceImportCandidatePage result = importService.search(context);
        
        assertEquals(1, result.getItems().size());
        assertEquals("legacy-skill", result.getItems().get(0).getExternalId());
        assertEquals("https://registry.example.com/legacy/.well-known/skills",
            result.getItems().get(0).getMetadata().get("source"));
    }
    
    @Test
    void testFetchVersion020SkillMdReturnsSkillZipArtifact() throws Exception {
        AiResourceImportArtifact result = importService.fetch(newContext(VERSION_020_ENDPOINT),
            item("md-skill"));
        
        assertEquals(SkillWellKnownImportService.RESOURCE_TYPE_SKILL, result.getResourceType());
        assertEquals(AiResourceImportPayloadKind.SKILL_ZIP, result.getPayloadKind());
        assertEquals("md-skill", result.getName());
        assertEquals("skill-md", result.getSourceMetadata().get("distributionType"));
        assertZipEntryContains(result.getPayload(), "md-skill/SKILL.md", "name: md-skill");
    }
    
    @Test
    void testFetchVersion020TarGzArchiveReturnsSkillZipArtifact() throws Exception {
        AiResourceImportArtifact result = importService.fetch(newContext(VERSION_020_ENDPOINT),
            item("archive-skill"));
        
        assertEquals("archive-skill", result.getName());
        assertEquals("archive", result.getSourceMetadata().get("distributionType"));
        assertZipEntryContains(result.getPayload(), "archive-skill/SKILL.md",
            "name: archive-skill");
        assertZipEntryContains(result.getPayload(), "archive-skill/docs/guide.md", "# Guide");
    }
    
    @Test
    void testFetchVersion020RejectsDigestMismatch() {
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(BAD_VERSION_020_ENDPOINT), item("md-skill")));
    }
    
    @Test
    void testFetchRejectsMissingSkill() {
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("missing-skill")));
    }
    
    @Test
    void testSearchRejectsMissingEndpoint() {
        AiResourceImportContext context = newContext();
        context.getSource().setEndpoint(null);
        
        assertThrows(NacosException.class, () -> importService.search(context));
    }
    
    @Test
    void testSupportedResourceTypeAndImporterType() {
        assertEquals(SkillWellKnownImportServiceBuilder.IMPORTER_TYPE,
            importService.importerType());
        assertFalse(importService.supportedResourceTypes().isEmpty());
    }
    
    private AiResourceImportContext newContext() {
        return newContext(ENDPOINT);
    }
    
    private AiResourceImportContext newContext(String endpoint) {
        AiResourceImportContext context = new AiResourceImportContext();
        context.setNamespaceId("public");
        AiResourceImportSource source = new AiResourceImportSource();
        source.setEndpoint(endpoint);
        source.setMaxArtifactSize(10L * 1024L * 1024L);
        context.setSource(source);
        return context;
    }
    
    private AiResourceImportItem item(String skillName) {
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId(skillName);
        item.setName(skillName);
        return item;
    }
    
    private String indexJson() {
        return "{\"skills\":["
            + "{\"name\":\"demo-skill\",\"description\":\"Demo skill\","
            + "\"files\":[\"SKILL.md\",\"docs/guide.md\"]},"
            + "{\"name\":\"other-skill\",\"description\":\"Other skill\","
            + "\"files\":[\"SKILL.md\"]}"
            + "]}";
    }
    
    private String legacyIndexJson() {
        return "{\"skills\":["
            + "{\"name\":\"legacy-skill\",\"description\":\"Legacy skill\","
            + "\"files\":[\"SKILL.md\"]}"
            + "]}";
    }
    
    private String version020IndexJson() throws Exception {
        byte[] markdown = skillMarkdown("md-skill").getBytes(StandardCharsets.UTF_8);
        byte[] archive = tarGzSkillArchive();
        return "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
            + "{\"name\":\"md-skill\",\"type\":\"skill-md\","
            + "\"description\":\"Markdown skill\","
            + "\"url\":\"md-skill/SKILL.md\","
            + "\"digest\":\"sha256:" + sha256Hex(markdown) + "\"},"
            + "{\"name\":\"archive-skill\",\"type\":\"archive\","
            + "\"description\":\"Archive skill\","
            + "\"url\":\"archive-skill.tar.gz\","
            + "\"digest\":\"sha256:" + sha256Hex(archive) + "\"}"
            + "]}";
    }
    
    private String badVersion020IndexJson() {
        return "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
            + "{\"name\":\"md-skill\",\"type\":\"skill-md\","
            + "\"description\":\"Markdown skill\","
            + "\"url\":\"md-skill/SKILL.md\","
            + "\"digest\":\"sha256:0000000000000000000000000000000000000000000000000000000000000000\"}"
            + "]}";
    }
    
    private String skillMarkdown(String name) {
        String description = "Demo skill";
        if ("other-skill".equals(name)) {
            description = "Other skill";
        } else if ("legacy-skill".equals(name)) {
            description = "Legacy skill";
        } else if ("md-skill".equals(name)) {
            description = "Markdown skill";
        } else if ("archive-skill".equals(name)) {
            description = "Archive skill";
        }
        return "---\nname: " + name + "\ndescription: " + description
            + "\nversion: 1.2.3\n---\n\nUse this skill.";
    }
    
    private HttpResponse<byte[]> responseFor(HttpRequest request) throws Exception {
        String path = request.uri().getPath();
        if ("/registry/public/.well-known/agent-skills/index.json".equals(path)) {
            return response(200, indexJson());
        }
        if ("/registry/public/.well-known/agent-skills/demo-skill/SKILL.md".equals(path)) {
            return response(200, skillMarkdown("demo-skill"));
        }
        if ("/registry/public/.well-known/agent-skills/demo-skill/docs/guide.md"
            .equals(path)) {
            return response(200, "# Guide");
        }
        if ("/registry/public/.well-known/agent-skills/other-skill/SKILL.md".equals(path)) {
            return response(200, skillMarkdown("other-skill"));
        }
        if ("/legacy/.well-known/agent-skills/index.json".equals(path)) {
            return response(404, "");
        }
        if ("/legacy/.well-known/skills/index.json".equals(path)) {
            return response(200, legacyIndexJson());
        }
        if ("/legacy/.well-known/skills/legacy-skill/SKILL.md".equals(path)) {
            return response(200, skillMarkdown("legacy-skill"));
        }
        if ("/v2/.well-known/agent-skills/index.json".equals(path)) {
            return response(200, version020IndexJson());
        }
        if ("/v2/.well-known/agent-skills/md-skill/SKILL.md".equals(path)) {
            return response(200, skillMarkdown("md-skill"));
        }
        if ("/v2/.well-known/agent-skills/archive-skill.tar.gz".equals(path)) {
            return response(200, tarGzSkillArchive(), "application/gzip");
        }
        if ("/bad-v2/.well-known/agent-skills/index.json".equals(path)) {
            return response(200, badVersion020IndexJson());
        }
        if ("/bad-v2/.well-known/agent-skills/md-skill/SKILL.md".equals(path)) {
            return response(200, skillMarkdown("md-skill"));
        }
        return response(404, "");
    }
    
    private HttpResponse<byte[]> response(int status, String body) {
        return response(status, body.getBytes(StandardCharsets.UTF_8), "application/json");
    }
    
    private HttpResponse<byte[]> response(int status, byte[] bytes, String contentType) {
        Map<String, java.util.List<String>> headers = new HashMap<>(1);
        headers.put("Content-Type", Collections.singletonList(contentType));
        return new HttpResponse<>() {
            
            @Override
            public int statusCode() {
                return status;
            }
            
            @Override
            public HttpRequest request() {
                return null;
            }
            
            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }
            
            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (key, value) -> true);
            }
            
            @Override
            public byte[] body() {
                return bytes;
            }
            
            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }
            
            @Override
            public java.net.URI uri() {
                return null;
            }
            
            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
    
    private byte[] tarGzSkillArchive() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(output);
            TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            addTarEntry(tar, "archive-skill/SKILL.md",
                skillMarkdown("archive-skill").getBytes(StandardCharsets.UTF_8));
            addTarEntry(tar, "archive-skill/docs/guide.md",
                "# Guide".getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
    
    private void addTarEntry(TarArchiveOutputStream tar, String name, byte[] bytes)
        throws Exception {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tar.putArchiveEntry(entry);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            input.transferTo(tar);
        }
        tar.closeArchiveEntry();
    }
    
    private String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte each : hash) {
            result.append(String.format("%02x", each & 0xff));
        }
        return result.toString();
    }
    
    private void assertZipEntryContains(byte[] zipBytes, String entryName, String expected)
        throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes),
            StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    zip.transferTo(output);
                    assertTrue(output.toString(StandardCharsets.UTF_8).contains(expected));
                    return;
                }
            }
        }
        throw new AssertionError("Zip entry not found: " + entryName);
    }
}
