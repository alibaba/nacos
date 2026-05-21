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

package com.alibaba.nacos.ai.importer.skill;

import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    
    @Mock
    private HttpClient httpClient;
    
    private SkillWellKnownImportService importService;
    
    @BeforeEach
    void setUp() throws Exception {
        lenient().when(httpClient.send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
        importService = new SkillWellKnownImportService(httpClient);
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
        assertEquals("2", result.getItems().get(0).getMetadata().get("fileCount"));
        assertFalse(result.isHasMore());
    }
    
    @Test
    void testFetchReturnsSkillZipArtifact() throws Exception {
        AiResourceImportArtifact result = importService.fetch(newContext(), item("demo-skill"));
        
        assertEquals(SkillWellKnownImportService.RESOURCE_TYPE_SKILL, result.getResourceType());
        assertEquals(AiResourceImportPayloadKind.SKILL_ZIP, result.getPayloadKind());
        assertEquals("demo-skill", result.getName());
        assertEquals("1.2.3", result.getVersion());
        Skill skill = SkillZipParser.parseSkillFromZip(result.getPayload(), "public");
        assertEquals("demo-skill", skill.getName());
        assertEquals("Demo skill", skill.getDescription());
        assertEquals(1, skill.getResource().size());
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
        AiResourceImportContext context = new AiResourceImportContext();
        context.setNamespaceId("public");
        AiResourceImportSource source = new AiResourceImportSource();
        source.setEndpoint(ENDPOINT);
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
    
    private String skillMarkdown(String name) {
        String description = "demo-skill".equals(name) ? "Demo skill" : "Other skill";
        return "---\nname: " + name + "\ndescription: " + description
            + "\nversion: 1.2.3\n---\n\nUse this skill.";
    }
    
    private HttpResponse<byte[]> responseFor(HttpRequest request) {
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
        return response(404, "");
    }
    
    private HttpResponse<byte[]> response(int status, String body) {
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
                return HttpHeaders.of(Collections.emptyMap(), (key, value) -> true);
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
}
