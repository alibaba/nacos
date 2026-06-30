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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ArdChunkBuilder}.
 *
 * @author nacos
 */
class ArdChunkBuilderTest {
    
    @Test
    void buildChunksShouldIncludeDescriptionTagsCapabilitiesAndMetadata() {
        ArdEntry entry = new ArdEntry();
        entry.setNamespaceId("public");
        entry.setIdentifier("urn:air:nacos.local:public:skill:api-helper");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName("api-helper");
        entry.setResourceVersion("1.0.0");
        entry.setDisplayName("api-helper");
        entry.setDescription("Generate API parameter tables");
        entry.setTags(JacksonUtils.toJson(List.of("api")));
        entry.setCapabilities(JacksonUtils.toJson(List.of("documentation")));
        entry.setRepresentativeQueries(JacksonUtils.toJson(List.of("api helper")));
        entry.setMetadata(JacksonUtils.toJson(Map.of("inputTypes", List.of("json"),
            "riskLevel", "low")));
        
        List<ArdChunk> chunks = new ArdChunkBuilder().buildChunks(entry);
        
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_DESCRIPTION.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().anyMatch(
            chunk -> ArdIndexConstants.CHUNK_TYPE_METADATA_IO.equals(chunk.getChunkType())));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkHash() != null));
    }
}
