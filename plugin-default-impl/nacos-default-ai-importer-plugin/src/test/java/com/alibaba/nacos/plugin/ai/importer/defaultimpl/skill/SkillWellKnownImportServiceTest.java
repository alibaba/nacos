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
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.ImportHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
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
import java.util.LinkedHashMap;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
    void testSearchHandlesCursorAndUnsupportedEntries() throws Exception {
        SkillWellKnownImportService service = serviceWithResponses(
            responseMap("https://registry.example.com/.well-known/agent-skills/index.json",
                importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":[null,"
                    + "{\"name\":\"\"},"
                    + "{\"name\":\"unsupported\",\"type\":\"unknown\"},"
                    + "{\"name\":\"matched\",\"description\":\"Matched skill\",\"type\":\"skill-md\","
                    + "\"url\":\"matched/SKILL.md\",\"digest\":\"sha256:abc\"}]}")));
        AiResourceImportContext context = newContext("https://registry.example.com");
        context.setQuery("matched");
        context.setCursor("abc");
        context.setLimit(1);
        
        AiResourceImportCandidatePage result = service.search(context);
        
        assertEquals(1, result.getItems().size());
        assertEquals("matched", result.getItems().get(0).getExternalId());
        
        context.setCursor("0");
        assertEquals(1, service.search(context).getItems().size());
    }
    
    @Test
    void testSearchReturnsEmptyForEmptyIndexAndWrapsFailures() throws Exception {
        SkillWellKnownImportService emptyService = serviceWithResponses(
            responseMap("https://registry.example.com/.well-known/agent-skills/index.json",
                importResponse(200, "{\"skills\":[]}")));
        assertTrue(emptyService.search(newContext("https://registry.example.com")).getItems()
            .isEmpty());
        
        SkillWellKnownImportService nullIndexService = serviceWithResponses(
            responseMap("https://registry.example.com/.well-known/agent-skills/index.json",
                importResponse(200, "null")));
        assertThrows(NacosException.class,
            () -> nullIndexService.search(newContext("https://registry.example.com")));
        
        SkillWellKnownImportService badSchemaService = serviceWithResponses(
            responseMap("https://registry.example.com/.well-known/agent-skills/index.json",
                importResponse(200,
                    "{\"$schema\":\"https://example.com/unsupported\",\"skills\":[]}")));
        assertThrows(NacosException.class,
            () -> badSchemaService.search(newContext("https://registry.example.com")));
        
        DefaultImportHttpClient client = Mockito.mock(DefaultImportHttpClient.class);
        when(client.get(any(AiResourceImportSource.class), any(String.class), eq(20), eq("*/*")))
            .thenThrow(new IllegalStateException("boom"));
        assertThrows(NacosException.class,
            () -> new SkillWellKnownImportService(client).search(
                newContext("https://registry.example.com")));
        
        SkillWellKnownImportService allFailService = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(404, ""),
            "https://registry.example.com/.well-known/skills/index.json",
            importResponse(404, "")));
        assertThrows(NacosException.class,
            () -> allFailService.search(newContext("https://registry.example.com")));
    }
    
    @Test
    void testFetchRejectsInvalidItemsAndEndpoint() {
        assertThrows(NacosException.class, () -> importService.fetch(newContext(), null));
        assertThrows(NacosException.class, () -> importService.fetch(newContext(), item(" ")));
        
        AiResourceImportContext context = newContext(" ");
        assertThrows(NacosException.class, () -> importService.search(context));
    }
    
    @Test
    void testFetchVersion020RejectsBlankUrlAndUnsupportedType() throws Exception {
        SkillWellKnownImportService blankUrlService = serviceWithResponses(
            responseMap("https://registry.example.com/.well-known/agent-skills/index.json",
                importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                    + "{\"name\":\"blank-url\",\"type\":\"skill-md\","
                    + "\"digest\":\"sha256:abc\"}]}")));
        assertThrows(NacosException.class,
            () -> blankUrlService.fetch(newContext("https://registry.example.com"),
                item("blank-url")));
        
        byte[] markdown = skillMarkdown("unknown-type").getBytes(StandardCharsets.UTF_8);
        SkillWellKnownImportService unsupportedTypeService = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                + "{\"name\":\"unknown-type\",\"type\":\"unknown\","
                + "\"url\":\"unknown-type/SKILL.md\","
                + "\"digest\":\"sha256:" + sha256Hex(markdown) + "\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/unknown-type/SKILL.md",
            importResponse(200, markdown, "text/markdown")));
        assertThrows(NacosException.class,
            () -> unsupportedTypeService.fetch(newContext("https://registry.example.com"),
                item("unknown-type")));
    }
    
    @Test
    void testFetchVersion020RejectsArtifactHttpErrorAndDigest() throws Exception {
        SkillWellKnownImportService httpErrorService = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                + "{\"name\":\"md-skill\",\"type\":\"skill-md\","
                + "\"url\":\"md-skill/SKILL.md\",\"digest\":\"sha256:abc\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/md-skill/SKILL.md",
            importResponse(500, "", "text/markdown")));
        assertThrows(NacosException.class,
            () -> httpErrorService.fetch(newContext("https://registry.example.com"),
                item("md-skill")));
        
        SkillWellKnownImportService blankDigestService = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                + "{\"name\":\"md-skill\",\"type\":\"skill-md\","
                + "\"url\":\"md-skill/SKILL.md\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/md-skill/SKILL.md",
            importResponse(200, skillMarkdown("md-skill").getBytes(StandardCharsets.UTF_8),
                "text/markdown")));
        assertThrows(NacosException.class,
            () -> blankDigestService.fetch(newContext("https://registry.example.com"),
                item("md-skill")));
        
        SkillWellKnownImportService invalidDigestService = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                + "{\"name\":\"md-skill\",\"type\":\"skill-md\","
                + "\"url\":\"md-skill/SKILL.md\",\"digest\":\"md5:abc\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/md-skill/SKILL.md",
            importResponse(200, skillMarkdown("md-skill").getBytes(StandardCharsets.UTF_8),
                "text/markdown")));
        assertThrows(NacosException.class,
            () -> invalidDigestService.fetch(newContext("https://registry.example.com"),
                item("md-skill")));
    }
    
    @Test
    void testFetchVersion020ZipAndTarArchives() throws Exception {
        byte[] zip = zipBytes("zip-skill/SKILL.md", skillMarkdown("zip-skill"));
        byte[] tar = tarSkillArchive();
        SkillWellKnownImportService service = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                + "{\"name\":\"zip-skill\",\"type\":\"archive\",\"url\":\"zip-skill.zip\","
                + "\"digest\":\"sha256:" + sha256Hex(zip) + "\"},"
                + "{\"name\":\"tar-skill\",\"type\":\"archive\",\"url\":\"tar-skill.tar\","
                + "\"digest\":\"sha256:" + sha256Hex(tar) + "\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/zip-skill.zip",
            importResponse(200, zip, "application/zip"),
            "https://registry.example.com/.well-known/agent-skills/tar-skill.tar",
            importResponse(200, tar, "application/x-tar")));
        
        assertZipEntryContains(service.fetch(newContext("https://registry.example.com"),
            item("zip-skill")).getPayload(), "zip-skill/SKILL.md", "name: zip-skill");
        assertZipEntryContains(service.fetch(newContext("https://registry.example.com"),
            item("tar-skill")).getPayload(), "tar-skill/SKILL.md", "name: tar-skill");
    }
    
    @Test
    void testFetchVersion020RejectsUnsupportedArchiveAndOversizedArtifact() throws Exception {
        byte[] bytes = skillMarkdown("archive-skill").getBytes(StandardCharsets.UTF_8);
        SkillWellKnownImportService service = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"$schema\":\"" + SCHEMA_0_2 + "\",\"skills\":["
                + "{\"name\":\"archive-skill\",\"type\":\"archive\",\"url\":\"archive-skill.bin\","
                + "\"digest\":\"sha256:" + sha256Hex(bytes) + "\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/archive-skill.bin",
            importResponse(200, bytes, "application/octet-stream")));
        assertThrows(NacosException.class,
            () -> service.fetch(newContext("https://registry.example.com"),
                item("archive-skill")));
        
        AiResourceImportContext smallContext = newContext("https://registry.example.com");
        smallContext.getSource().setMaxArtifactSize(1);
        assertThrows(NacosException.class,
            () -> service.fetch(smallContext, item("archive-skill")));
    }
    
    @Test
    void testArchiveConversionBoundaryBranches() throws Exception {
        Method convert = SkillWellKnownImportService.class.getDeclaredMethod("convertTarToZip",
            byte[].class, boolean.class);
        convert.setAccessible(true);
        byte[] zip = (byte[]) convert.invoke(importService, tarWithDirectoryAndDuplicate(), false);
        assertZipEntryContains(zip, "archive-skill/SKILL.md", "name: archive-skill");
        
        assertThrows(Exception.class, () -> convert.invoke(importService, tarWithManyEntries(),
            false));
        assertThrows(Exception.class, () -> convert.invoke(importService, tarWithLargeEntry(),
            false));
        byte[] blankNameZip = (byte[]) convert.invoke(importService, tarWithBlankEntryName(),
            false);
        assertEquals(0, countZipEntries(blankNameZip));
        
        Method normalize = SkillWellKnownImportService.class.getDeclaredMethod(
            "normalizeArchiveEntryName", String.class);
        normalize.setAccessible(true);
        assertNull(normalize.invoke(importService, " "));
    }
    
    @Test
    void testFetchVersion010DefaultFilesAndEndpointVariants() throws Exception {
        SkillWellKnownImportService service = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/skills/index.json",
            importResponse(200, "{\"skills\":[{\"name\":\"default-file\"}]}"),
            "https://registry.example.com/.well-known/skills/default-file/SKILL.md",
            importResponse(200, skillMarkdown("default-file"), "text/markdown")));
        assertZipEntryContains(service.fetch(
            newContext("https://registry.example.com/.well-known/skills/"), item("default-file"))
            .getPayload(), "default-file/SKILL.md", "name: default-file");
        
        SkillWellKnownImportService byHttpClient = new SkillWellKnownImportService(httpClient);
        assertEquals(SkillWellKnownImportServiceBuilder.IMPORTER_TYPE,
            byHttpClient.importerType());
        
        SkillWellKnownImportService indexFileService = serviceWithResponses(responseMap(
            "https://registry.example.com/index.json",
            importResponse(200, "{\"skills\":[{\"name\":\"index-file\"}]}"),
            "https://registry.example.com/index-file/SKILL.md",
            importResponse(200, skillMarkdown("index-file"), "text/markdown")));
        assertZipEntryContains(indexFileService.fetch(
            newContext("https://registry.example.com/index.json"), item("index-file"))
            .getPayload(), "index-file/SKILL.md", "name: index-file");
        
        Method trim = SkillWellKnownImportService.class.getDeclaredMethod("trimTrailingSlash",
            String.class);
        trim.setAccessible(true);
        assertThrows(Exception.class, () -> trim.invoke(importService, " "));
        
        Method base = SkillWellKnownImportService.class.getDeclaredMethod("wellKnownBase",
            String.class);
        base.setAccessible(true);
        assertEquals("https://registry.example.com/custom",
            base.invoke(importService, "https://registry.example.com/custom-index.json"));
        assertEquals("https://registry.example.com/plain",
            base.invoke(importService, "https://registry.example.com/plain"));
        
        SkillWellKnownImportService missingFileService = serviceWithResponses(responseMap(
            "https://registry.example.com/.well-known/agent-skills/index.json",
            importResponse(200, "{\"skills\":[{\"name\":\"missing-file\"}]}"),
            "https://registry.example.com/.well-known/agent-skills/missing-file/SKILL.md",
            importResponse(404, "", "text/markdown")));
        assertThrows(NacosException.class,
            () -> missingFileService.fetch(newContext("https://registry.example.com"),
                item("missing-file")));
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
            + "\"url\":\"md-skill/SKILL.md\",\"version\":\"1.0.0\","
            + "\"digest\":\"sha256:" + sha256Hex(markdown) + "\"},"
            + "{\"name\":\"archive-skill\",\"type\":\"archive\","
            + "\"description\":\"Archive skill\","
            + "\"url\":\"archive-skill.tar.gz\",\"version\":\"1.0.0\","
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
    
    private Map<String, ImportHttpResponse> responseMap(Object... keyValues) {
        Map<String, ImportHttpResponse> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], (ImportHttpResponse) keyValues[i + 1]);
        }
        return result;
    }
    
    private SkillWellKnownImportService serviceWithResponses(
        Map<String, ImportHttpResponse> responses) throws Exception {
        DefaultImportHttpClient client = Mockito.mock(DefaultImportHttpClient.class);
        when(client.get(any(AiResourceImportSource.class), any(String.class), eq(20), eq("*/*")))
            .thenAnswer(invocation -> {
                String url = invocation.getArgument(1);
                ImportHttpResponse response = responses.get(url);
                if (response == null) {
                    return importResponse(404, "", "text/plain");
                }
                return response;
            });
        return new SkillWellKnownImportService(client);
    }
    
    private ImportHttpResponse importResponse(int status, byte[] bytes, String contentType) {
        Map<String, java.util.List<String>> headers = new HashMap<>(1);
        headers.put("Content-Type", Collections.singletonList(contentType));
        return new ImportHttpResponse("https://registry.example.com/artifact", status,
            HttpHeaders.of(headers, (key, value) -> true), bytes);
    }
    
    private ImportHttpResponse importResponse(int status, String body) {
        return importResponse(status, body.getBytes(StandardCharsets.UTF_8), "application/json");
    }
    
    private ImportHttpResponse importResponse(int status, String body, String contentType) {
        return importResponse(status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }
    
    private byte[] zipBytes(String name, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip =
            new java.util.zip.ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
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
    
    private byte[] tarSkillArchive() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            addTarEntry(tar, "./tar-skill/SKILL.md",
                skillMarkdown("tar-skill").getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
    
    private byte[] tarWithDirectoryAndDuplicate() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            TarArchiveEntry directory = new TarArchiveEntry("archive-skill/docs/");
            directory.setSize(0);
            tar.putArchiveEntry(directory);
            tar.closeArchiveEntry();
            addTarEntry(tar, "./archive-skill/SKILL.md",
                skillMarkdown("archive-skill").getBytes(StandardCharsets.UTF_8));
            addTarEntry(tar, "archive-skill/SKILL.md",
                "duplicate".getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
    
    private byte[] tarWithManyEntries() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            for (int i = 0; i < 501; i++) {
                addTarEntry(tar, "archive-skill/file-" + i + ".txt",
                    "x".getBytes(StandardCharsets.UTF_8));
            }
        }
        return output.toByteArray();
    }
    
    private byte[] tarWithLargeEntry() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            byte[] bytes = new byte[50 * 1024 * 1024 + 1];
            addTarEntry(tar, "archive-skill/large.bin", bytes);
        }
        return output.toByteArray();
    }
    
    private byte[] tarWithBlankEntryName() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            addTarEntry(tar, "./\t", new byte[0]);
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
    
    private int countZipEntries(byte[] zipBytes) throws Exception {
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes),
            StandardCharsets.UTF_8)) {
            while (zip.getNextEntry() != null) {
                count++;
            }
        }
        return count;
    }
}
