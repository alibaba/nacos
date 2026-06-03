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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SkillsShImportService}.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class SkillsShImportServiceTest {
    
    private static final String ENDPOINT = "https://skills.sh";
    
    @Mock
    private HttpClient httpClient;
    
    private SkillsShImportService importService;
    
    @BeforeEach
    void setUp() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
        importService = new SkillsShImportService(new DefaultImportHttpClient(httpClient,
            host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")}));
    }
    
    @Test
    void testSearchReturnsSkillsShCandidates() throws Exception {
        AiResourceImportContext context = newContext();
        context.setQuery("pdf");
        context.setLimit(2);
        
        AiResourceImportCandidatePage result = importService.search(context);
        
        assertEquals(1, result.getItems().size());
        assertFalse(result.isHasMore());
        assertEquals("openai/skills/pdf", result.getItems().get(0).getExternalId());
        assertEquals(SkillsShImportService.RESOURCE_TYPE_SKILL,
            result.getItems().get(0).getResourceType());
        assertEquals("pdf", result.getItems().get(0).getName());
        assertEquals("https://skills.sh/openai/skills/pdf",
            result.getItems().get(0).getMetadata().get("artifactUrl"));
        assertEquals("https://github.com/openai/skills",
            result.getItems().get(0).getMetadata().get("repository"));
        assertEquals("3330", result.getItems().get(0).getMetadata().get("installs"));
    }
    
    @Test
    void testSearchUsesDefaultQueryWhenQueryIsBlank() throws Exception {
        AiResourceImportContext context = newContext();
        context.setLimit(12);
        
        AiResourceImportCandidatePage result = importService.search(context);
        
        assertEquals(1, result.getItems().size());
        assertEquals("openai/skills/pdf", result.getItems().get(0).getExternalId());
    }
    
    @Test
    void testSearchSkipsUnsupportedRepositorySource() throws Exception {
        AiResourceImportContext context = newContext();
        context.setQuery("skill");
        context.setLimit(30);
        
        AiResourceImportCandidatePage result = importService.search(context);
        
        assertEquals(1, result.getItems().size());
        assertEquals("openai/skills/pdf", result.getItems().get(0).getExternalId());
    }
    
    @Test
    void testSearchRejectsOneCharacterQuery() {
        AiResourceImportContext context = newContext();
        context.setQuery("p");
        
        assertThrows(NacosException.class, () -> importService.search(context));
        verifyNoInteractions(httpClient);
    }
    
    @Test
    void testFetchReturnsSkillZipArtifact() throws Exception {
        AiResourceImportArtifact result = importService.fetch(newContext(),
            item("openai/skills/pdf"));
        
        assertEquals(SkillsShImportService.RESOURCE_TYPE_SKILL, result.getResourceType());
        assertEquals(AiResourceImportPayloadKind.SKILL_ZIP, result.getPayloadKind());
        assertEquals("openai/skills/pdf", result.getExternalId());
        assertEquals("https://skills.sh/openai/skills/pdf",
            result.getSourceMetadata().get("artifactUrl"));
        assertEquals("https://github.com/openai/skills",
            result.getSourceMetadata().get("repository"));
        assertEquals("snapshot-hash", result.getSourceMetadata().get("hash"));
        assertZipEntryContains(result.getPayload(), "pdf/SKILL.md", "name: pdf");
        assertZipEntryContains(result.getPayload(), "pdf/agents/openai.yaml", "PDF Skill");
    }
    
    @Test
    void testFetchUsesSelectedItemMetadata() throws Exception {
        AiResourceImportItem item = new AiResourceImportItem();
        item.setName("PDF Skill");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("repositorySource", "openai/skills");
        metadata.put("skillId", "pdf");
        item.setMetadata(metadata);
        
        AiResourceImportArtifact result = importService.fetch(newContext(), item);
        
        assertEquals("PDF Skill", result.getName());
        assertEquals("openai/skills/pdf", result.getExternalId());
        assertZipEntryContains(result.getPayload(), "pdf/SKILL.md", "name: pdf");
    }
    
    @Test
    void testSearchReturnsEmptyWhenResponseHasNoSkills() throws Exception {
        SkillsShImportService service = serviceWithResponse(200, "{}");
        
        AiResourceImportCandidatePage result = service.search(newContext());
        
        assertTrue(result.getItems().isEmpty());
    }
    
    @Test
    void testSearchSkipsInvalidItemsAndStopsAtLimit() throws Exception {
        SkillsShImportService service = serviceWithResponse(200, "{\"skills\":[null,"
            + "{\"id\":\"\"},"
            + "{\"id\":\"owner/repo/one\",\"skillId\":\"one\",\"source\":\"owner/repo\"},"
            + "{\"id\":\"owner/repo/two\",\"skillId\":\"two\",\"source\":\"\"}]}");
        AiResourceImportContext context = newContext();
        context.setLimit(2);
        
        AiResourceImportCandidatePage result = service.search(context);
        
        assertEquals(2, result.getItems().size());
        assertEquals("owner/repo/one", result.getItems().get(0).getExternalId());
    }
    
    @Test
    void testSearchRejectsHttpError() throws Exception {
        SkillsShImportService service = serviceWithResponse(500, "{}");
        
        assertThrows(NacosException.class, () -> service.search(newContext()));
    }
    
    @Test
    void testSearchWrapsHttpClientFailure() throws Exception {
        DefaultImportHttpClient client = Mockito.mock(DefaultImportHttpClient.class);
        when(client.get(any(AiResourceImportSource.class), any(String.class), eq(20),
            eq("application/json"))).thenThrow(new IllegalStateException("boom"));
        
        assertThrows(NacosException.class,
            () -> new SkillsShImportService(client).search(newContext()));
    }
    
    @Test
    void testFetchRejectsHttpErrorAndWrapsClientFailure() throws Exception {
        SkillsShImportService service = serviceWithResponse(500, "{}");
        assertThrows(NacosException.class,
            () -> service.fetch(newContext(), item("owner/repo/one")));
        
        DefaultImportHttpClient client = Mockito.mock(DefaultImportHttpClient.class);
        when(client.get(any(AiResourceImportSource.class), any(String.class), eq(20),
            eq("application/json"))).thenThrow(new IllegalStateException("boom"));
        assertThrows(NacosException.class,
            () -> new SkillsShImportService(client).fetch(newContext(), item("owner/repo/one")));
    }
    
    @Test
    void testFetchRejectsInvalidItems() {
        assertThrows(NacosException.class, () -> importService.fetch(newContext(), null));
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("missing")));
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("bad-source/repo/skill")));
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("owner/repo/../skill")));
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("owner/repo/.hidden")));
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("owner/repo/path\\")));
        
        AiResourceImportItem metadataItem = new AiResourceImportItem();
        metadataItem.setExternalId("owner/repo/skill");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("repositorySource", "invalid");
        metadata.put("skillId", "skill");
        metadataItem.setMetadata(metadata);
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), metadataItem));
    }
    
    @Test
    void testFetchRejectsInvalidDownloadResponse() throws Exception {
        assertThrows(NacosException.class,
            () -> serviceWithResponse(200, "{}").fetch(newContext(), item("owner/repo/one")));
        assertThrows(NacosException.class,
            () -> serviceWithResponse(200, "{\"files\":[{\"path\":\" \",\"contents\":\"x\"}]}")
                .fetch(newContext(), item("owner/repo/one")));
    }
    
    @Test
    void testFetchRejectsFileCountAndSizeLimit() throws Exception {
        AiResourceImportContext countContext = newContext();
        countContext.getSource().setMaxItemCount(1);
        String tooManyFiles = "{\"files\":[{\"path\":\"SKILL.md\",\"contents\":\"x\"},"
            + "{\"path\":\"README.md\",\"contents\":\"y\"}]}";
        assertThrows(NacosException.class,
            () -> serviceWithResponse(200, tooManyFiles).fetch(countContext,
                item("owner/repo/one")));
        
        AiResourceImportContext sizeContext = newContext();
        sizeContext.getSource().setMaxArtifactSize(1);
        assertThrows(NacosException.class,
            () -> serviceWithResponse(200, downloadJson()).fetch(sizeContext,
                item("owner/repo/one")));
    }
    
    @Test
    void testFetchNormalizesAndSkipsDuplicateFiles() throws Exception {
        String json = "{\"files\":["
            + "{\"path\":\"./SKILL.md\",\"contents\":\"name: one\"},"
            + "{\"path\":\"SKILL.md\",\"contents\":\"duplicate\"}"
            + "]}";
        
        AiResourceImportArtifact result =
            serviceWithResponse(200, json).fetch(newContext(), item("owner/repo/one"));
        
        assertZipEntryContains(result.getPayload(), "one/SKILL.md", "name: one");
    }
    
    @Test
    void testEndpointVariantsAndConstructor() throws Exception {
        SkillsShImportService byHttpClient = new SkillsShImportService(httpClient);
        assertEquals(SkillsShImportServiceBuilder.IMPORTER_TYPE, byHttpClient.importerType());
        
        AiResourceImportContext searchEndpoint = newContext();
        searchEndpoint.getSource().setEndpoint(ENDPOINT + "/api/search/");
        assertEquals(1, importService.search(searchEndpoint).getItems().size());
        
        AiResourceImportContext downloadEndpoint = newContext();
        downloadEndpoint.getSource().setEndpoint(ENDPOINT + "/api/download/");
        assertZipEntryContains(importService.fetch(downloadEndpoint, item("openai/skills/pdf"))
            .getPayload(), "pdf/SKILL.md", "name: pdf");
        
        AiResourceImportContext blankEndpoint = newContext();
        blankEndpoint.getSource().setEndpoint(" ");
        assertThrows(NacosException.class, () -> importService.search(blankEndpoint));
        
        Method method = SkillsShImportService.class.getDeclaredMethod("trimTrailingSlash",
            String.class);
        method.setAccessible(true);
        assertThrows(Exception.class, () -> method.invoke(importService, " "));
    }
    
    @Test
    void testFetchRejectsMissingSkillMarkdown() {
        assertThrows(NacosException.class,
            () -> importService.fetch(newContext(), item("openai/skills/missing-md")));
    }
    
    @Test
    void testSearchRejectsMissingEndpoint() {
        AiResourceImportContext context = newContext();
        context.getSource().setEndpoint(null);
        
        assertThrows(NacosException.class, () -> importService.search(context));
    }
    
    @Test
    void testSupportedResourceTypeAndImporterType() {
        assertEquals(SkillsShImportServiceBuilder.IMPORTER_TYPE, importService.importerType());
        assertFalse(importService.supportedResourceTypes().isEmpty());
    }
    
    private AiResourceImportContext newContext() {
        AiResourceImportContext context = new AiResourceImportContext();
        context.setNamespaceId("public");
        AiResourceImportSource source = new AiResourceImportSource();
        source.setEndpoint(ENDPOINT);
        source.setMaxArtifactSize(10L * 1024L * 1024L);
        source.setMaxItemCount(10);
        context.setSource(source);
        return context;
    }
    
    private AiResourceImportItem item(String externalId) {
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId(externalId);
        item.setName("pdf");
        return item;
    }
    
    private String searchJson() {
        return "{\"query\":\"pdf\",\"skills\":["
            + "{\"id\":\"openai/skills/pdf\","
            + "\"skillId\":\"pdf\","
            + "\"name\":\"pdf\","
            + "\"installs\":3330,"
            + "\"source\":\"openai/skills\"},"
            + "{\"id\":\"skills.volces.com/find-skills-skill\","
            + "\"skillId\":\"find-skills-skill\","
            + "\"name\":\"find-skills-skill\","
            + "\"installs\":123,"
            + "\"source\":\"skills.volces.com\"}"
            + "]}";
    }
    
    private String downloadJson() {
        return "{\"files\":["
            + "{\"path\":\"SKILL.md\","
            + "\"contents\":\"---\\nname: pdf\\ndescription: Read PDFs\\n---\\n# PDF\"},"
            + "{\"path\":\"agents/openai.yaml\","
            + "\"contents\":\"interface:\\n  display_name: PDF Skill\\n\"}"
            + "],\"hash\":\"snapshot-hash\"}";
    }
    
    private String missingMarkdownDownloadJson() {
        return "{\"files\":["
            + "{\"path\":\"README.md\","
            + "\"contents\":\"# Missing markdown\"}"
            + "],\"hash\":\"snapshot-hash\"}";
    }
    
    private HttpResponse<byte[]> responseFor(HttpRequest request) {
        URI uri = request.uri();
        if ("/api/search".equals(uri.getPath())) {
            assertTrue("q=pdf&limit=30".equals(uri.getQuery())
                || "q=skill&limit=30".equals(uri.getQuery()));
            return response(200, searchJson());
        }
        if ("/api/download/openai/skills/pdf".equals(uri.getPath())) {
            return response(200, downloadJson());
        }
        if ("/api/download/openai/skills/missing-md".equals(uri.getPath())) {
            return response(200, missingMarkdownDownloadJson());
        }
        return response(404, "");
    }
    
    private HttpResponse<byte[]> response(int status, String body) {
        Map<String, java.util.List<String>> headers = new HashMap<>(1);
        headers.put("Content-Type", Collections.singletonList("application/json"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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
            public URI uri() {
                return null;
            }
            
            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
    
    private SkillsShImportService serviceWithResponse(int status, String body) throws Exception {
        DefaultImportHttpClient client = Mockito.mock(DefaultImportHttpClient.class);
        when(client.get(any(AiResourceImportSource.class), any(String.class), eq(20),
            eq("application/json"))).thenReturn(importResponse(status, body));
        return new SkillsShImportService(client);
    }
    
    private ImportHttpResponse importResponse(int status, String body) {
        return new ImportHttpResponse("https://skills.sh/api/search", status,
            HttpHeaders.of(Collections.emptyMap(), (key, value) -> true),
            body.getBytes(StandardCharsets.UTF_8));
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
