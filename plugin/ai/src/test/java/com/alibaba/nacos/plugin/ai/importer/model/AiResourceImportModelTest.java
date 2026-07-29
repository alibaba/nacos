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

package com.alibaba.nacos.plugin.ai.importer.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportModelTest {
    
    private static final Map<String, String> METADATA = Collections.singletonMap("key", "value");
    
    @Test
    void testImportContextAccessors() {
        AiResourceImportContext context = new AiResourceImportContext();
        
        context.setNamespaceId("namespace");
        context.setResourceType("mcp");
        context.setQuery("nacos");
        context.setCursor("cursor");
        context.setLimit(10);
        context.setOptions(METADATA);
        context.setRequestId("request");
        context.setOperator("operator");
        context.setClientIp("127.0.0.1");
        
        assertEquals("namespace", context.getNamespaceId());
        assertEquals("mcp", context.getResourceType());
        assertEquals("nacos", context.getQuery());
        assertEquals("cursor", context.getCursor());
        assertEquals(10, context.getLimit());
        assertSame(METADATA, context.getOptions());
        assertEquals("request", context.getRequestId());
        assertEquals("operator", context.getOperator());
        assertEquals("127.0.0.1", context.getClientIp());
    }
    
    @Test
    void testImportCandidateAndPageAccessors() {
        AiResourceImportCandidate candidate = new AiResourceImportCandidate();
        AiResourceImportCandidatePage page = new AiResourceImportCandidatePage();
        
        candidate.setResourceType("skill");
        candidate.setExternalId("external");
        candidate.setName("name");
        candidate.setVersion("v1");
        candidate.setDescription("description");
        candidate.setMetadata(METADATA);
        page.setItems(Collections.singletonList(candidate));
        page.setNextCursor("next");
        page.setHasMore(true);
        page.setSourceMetadata(METADATA);
        
        assertEquals("skill", candidate.getResourceType());
        assertEquals("external", candidate.getExternalId());
        assertEquals("name", candidate.getName());
        assertEquals("v1", candidate.getVersion());
        assertEquals("description", candidate.getDescription());
        assertSame(METADATA, candidate.getMetadata());
        assertEquals(Collections.singletonList(candidate), page.getItems());
        assertEquals("next", page.getNextCursor());
        assertTrue(page.isHasMore());
        assertSame(METADATA, page.getSourceMetadata());
        page.setHasMore(false);
        assertFalse(page.isHasMore());
    }
    
    @Test
    void testImportItemAndArtifactAccessors() {
        byte[] payload = new byte[] {1, 2, 3};
        AiResourceImportItem item = new AiResourceImportItem();
        AiResourceImportArtifact artifact = new AiResourceImportArtifact();
        
        item.setExternalId("external");
        item.setName("item");
        item.setVersion("v1");
        item.setMetadata(METADATA);
        artifact.setResourceType("mcp");
        artifact.setExternalId(item.getExternalId());
        artifact.setName(item.getName());
        artifact.setVersion(item.getVersion());
        artifact.setDescription("description");
        artifact.setPayloadKind(AiResourceImportPayloadKind.BYTES);
        artifact.setPayload(payload);
        artifact.setPayloadJson("{}");
        artifact.setChecksum("sha256");
        artifact.setSourceMetadata(METADATA);
        
        assertEquals("external", item.getExternalId());
        assertEquals("item", item.getName());
        assertEquals("v1", item.getVersion());
        assertSame(METADATA, item.getMetadata());
        assertEquals("mcp", artifact.getResourceType());
        assertEquals("external", artifact.getExternalId());
        assertEquals("item", artifact.getName());
        assertEquals("v1", artifact.getVersion());
        assertEquals("description", artifact.getDescription());
        assertEquals(AiResourceImportPayloadKind.BYTES, artifact.getPayloadKind());
        assertArrayEquals(payload, artifact.getPayload());
        assertEquals("{}", artifact.getPayloadJson());
        assertEquals("sha256", artifact.getChecksum());
        assertSame(METADATA, artifact.getSourceMetadata());
    }
}
