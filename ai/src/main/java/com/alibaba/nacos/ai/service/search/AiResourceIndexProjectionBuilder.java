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

import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds the common document, chunk, and facet projection boundary.
 *
 * @author nacos
 */
public class AiResourceIndexProjectionBuilder {
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private final AiResourceSearchChunkBuilder chunkBuilder =
        new AiResourceSearchChunkBuilder();
    
    /**
     * Build a complete index projection.
     *
     * @param document canonical search document
     * @param contents bounded source contents used by keyword and enhancement processing
     * @param sourceChunkType chunk type assigned to source contents
     * @return immutable projection boundary
     */
    public AiResourceIndexProjection build(AiResourceSearchDocument document,
        List<AiResourceIndexEnhancementContent> contents, String sourceChunkType) {
        List<AiResourceSearchChunk> chunks = new ArrayList<>(chunkBuilder.buildChunks(document));
        chunks.addAll(chunkBuilder.buildSourceContentChunks(document, contents, sourceChunkType));
        return new AiResourceIndexProjection(document, chunks, parseFacets(document.getMetadata()),
            contents);
    }
    
    private Map<String, Object> parseFacets(String metadata) {
        if (StringUtils.isBlank(metadata)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> result = JacksonUtils.toObj(metadata, MAP_TYPE);
            return result == null ? Collections.emptyMap() : result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
