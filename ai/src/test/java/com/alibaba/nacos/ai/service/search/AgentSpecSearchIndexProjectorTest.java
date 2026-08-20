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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class AgentSpecSearchIndexProjectorTest {
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final TypeReference<List<String>> LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private final AgentSpecSearchIndexProjector projector =
        new AgentSpecSearchIndexProjector();
    
    @Test
    void shouldProjectOnlyPublicDependenciesAndCapabilities() {
        AgentSpec agentSpec = agentSpec();
        agentSpec.setContent("""
            {
              "dependencies": [
                {"name":"search-skill","version":"1.0.0","credential":"leak-one"},
                {"name":"private-skill","private":true,"description":"leak-two"}
              ],
              "worker": {
                "dependencies": {"name":"public-mcp","public":true,
                  "authorization":"Bearer leak-three"},
                "capabilities": [
                  {"name":"research","description":"Find papers",
                    "runtime":{"token":"leak-four"}},
                  {"name":"private-cap","scope":"private"}
                ]
              },
              "runtime": {
                "dependencies":[{"name":"runtime-leak"}],
                "capabilities":[{"name":"runtime-cap-leak"}]
              },
              "privateArea": {
                "visibility":"private",
                "capabilities":[{"name":"visibility-leak"}]
              }
            }
            """);
        
        AiResourceIndexProjection projection = projector.project(meta(), version(), agentSpec);
        AiResourceSearchDocument document = projection.getDocument();
        Map<String, Object> metadata = JacksonUtils.toObj(document.getMetadata(), MAP_TYPE);
        List<String> capabilities = JacksonUtils.toObj(document.getCapabilities(), LIST_TYPE);
        List<String> queries = JacksonUtils.toObj(document.getRepresentativeQueries(), LIST_TYPE);
        
        assertEquals("public", document.getNamespaceId());
        assertEquals("agentspec", document.getResourceType());
        assertEquals("research-worker", document.getResourceName());
        assertEquals("1.0.0", document.getResourceVersion());
        assertEquals("Research worker", document.getDescription());
        assertEquals(2000L, document.getGmtModified().getTime());
        assertEquals(List.of("research", "worker"),
            JacksonUtils.toObj(document.getTags(), LIST_TYPE));
        assertTrue(capabilities.containsAll(List.of("name", "research", "description",
            "Find papers")));
        assertTrue(queries.containsAll(List.of("research-worker", "Research worker",
            "search-skill", "public-mcp", "research", "Find papers")));
        assertEquals("PUBLIC", metadata.get("scope"));
        assertEquals("alice", metadata.get("owner"));
        assertEquals(1, metadata.get("projectionVersion"));
        assertTrue(metadata.containsKey("publicDependencies"));
        assertEquals(64, document.getSourceDigest().length());
        String chunkText = projection.getEnhancementContents().get(0).getText();
        assertTrue(chunkText.contains("search-skill"));
        assertTrue(chunkText.contains("public-mcp"));
        assertTrue(chunkText.contains("Find papers"));
        assertFalse(chunkText.contains("leak"));
        assertFalse(chunkText.contains("private-skill"));
        assertFalse(chunkText.contains("runtime-cap"));
        assertTrue(projection.getChunks().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_AGENTSPEC_CONTENT.equals(
                chunk.getChunkType())));
    }
    
    @Test
    void shouldHandleEmptyMalformedAndOversizedPublicManifest() {
        AiResource meta = meta();
        meta.setBizTags("not-json");
        meta.setOwner(null);
        meta.setScope(null);
        AiResourceVersion version = version();
        version.setGmtModified(null);
        AgentSpec agentSpec = agentSpec();
        agentSpec.setDescription(" ");
        agentSpec.setContent("not-json");
        
        AiResourceIndexProjection malformed = projector.project(meta, version, agentSpec);
        
        assertEquals("Version description", malformed.getDocument().getDescription());
        assertEquals(1000L, malformed.getDocument().getGmtModified().getTime());
        assertTrue(malformed.getEnhancementContents().isEmpty());
        assertEquals(List.of(), JacksonUtils.toObj(malformed.getDocument().getTags(), LIST_TYPE));
        assertEquals(List.of(),
            JacksonUtils.toObj(malformed.getDocument().getCapabilities(), LIST_TYPE));
        Map<String, Object> metadata = JacksonUtils.toObj(
            malformed.getDocument().getMetadata(), MAP_TYPE);
        assertFalse(metadata.containsKey("owner"));
        
        agentSpec.setContent("{\"dependencies\":[{\"description\":\""
            + "x".repeat(13000) + "\"}]}");
        AiResourceIndexProjection oversized = projector.project(meta, version, agentSpec);
        assertEquals(12000, oversized.getEnhancementContents().get(0).getText().length());
        
        agentSpec.setContent(null);
        version.setDesc(null);
        meta.setDesc("Metadata description");
        assertEquals("Metadata description",
            projector.project(meta, version, agentSpec).getDocument().getDescription());
    }
    
    @Test
    void shouldDigestOnlyStablePublicFacts() {
        AiResource meta = meta();
        AiResourceVersion version = version();
        AgentSpec agentSpec = agentSpec();
        agentSpec.setContent("{\"dependencies\":[{\"name\":\"skill-a\"}],"
            + "\"runtime\":{\"token\":\"secret-a\"}}");
        String initial = projector.project(meta, version, agentSpec).getDocument()
            .getSourceDigest();
        meta.setGmtModified(new Timestamp(9999L));
        version.setGmtModified(new Timestamp(8888L));
        agentSpec.setContent("{\"dependencies\":[{\"name\":\"skill-a\"}],"
            + "\"runtime\":{\"token\":\"secret-b\"}}");
        String privateOnly = projector.project(meta, version, agentSpec).getDocument()
            .getSourceDigest();
        agentSpec.setContent("{\"dependencies\":[{\"name\":\"skill-b\"}]}");
        String publicChange = projector.project(meta, version, agentSpec).getDocument()
            .getSourceDigest();
        
        assertEquals(initial, privateOnly);
        assertNotEquals(privateOnly, publicChange);
    }
    
    @Test
    void shouldHandleBlankAndNullTagsWithoutDescription() {
        AiResource meta = meta();
        meta.setBizTags(" ");
        meta.setDesc(null);
        AiResourceVersion version = version();
        version.setDesc(null);
        AgentSpec agentSpec = agentSpec();
        agentSpec.setDescription(null);
        
        AiResourceSearchDocument blankTags = projector.project(meta, version, agentSpec)
            .getDocument();
        
        assertEquals(List.of(), JacksonUtils.toObj(blankTags.getTags(), LIST_TYPE));
        assertEquals(null, blankTags.getDescription());
        
        meta.setBizTags("null");
        AiResourceSearchDocument nullTags = projector.project(meta, version, agentSpec)
            .getDocument();
        assertEquals(List.of(), JacksonUtils.toObj(nullTags.getTags(), LIST_TYPE));
    }
    
    @Test
    void shouldRejectMissingFactsAndWrapMissingSha256() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> projector.project(null, version(), agentSpec()));
        assertThrows(IllegalArgumentException.class,
            () -> projector.project(meta(), null, agentSpec()));
        assertThrows(IllegalArgumentException.class,
            () -> projector.project(meta(), version(), null));
        try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance(eq("SHA-256")))
                .thenThrow(new NoSuchAlgorithmException("missing"));
            assertThrows(IllegalStateException.class,
                () -> projector.project(meta(), version(), agentSpec()));
        }
    }
    
    private AiResource meta() {
        AiResource result = new AiResource();
        result.setNamespaceId("public");
        result.setType("agentspec");
        result.setName("research-worker");
        result.setDesc("Metadata description");
        result.setStatus("enable");
        result.setBizTags("[\"research\",null,\"\",\"worker\",\"research\"]");
        result.setScope("PUBLIC");
        result.setOwner("alice");
        result.setGmtModified(new Timestamp(1000L));
        return result;
    }
    
    private AiResourceVersion version() {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId("public");
        result.setType("agentspec");
        result.setName("research-worker");
        result.setVersion("1.0.0");
        result.setStatus("online");
        result.setDesc("Version description");
        result.setGmtModified(new Timestamp(2000L));
        return result;
    }
    
    private AgentSpec agentSpec() {
        AgentSpec result = new AgentSpec();
        result.setNamespaceId("public");
        result.setName("research-worker");
        result.setDescription("Research worker");
        result.setContent("{}");
        return result;
    }
}
