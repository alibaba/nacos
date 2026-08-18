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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-neutral search projection produced from one canonical AI resource.
 *
 * <p>The document owns resource identity and currentness, chunks own keyword and optional
 * vector recall text, and facets own exact filtering attributes. Facets are persisted in the
 * document metadata during the first implementation phase.</p>
 *
 * @author nacos
 */
public class AiResourceIndexProjection {
    
    private final AiResourceSearchDocument document;
    
    private final List<AiResourceSearchChunk> chunks;
    
    private final Map<String, Object> facets;
    
    private final List<AiResourceIndexEnhancementContent> enhancementContents;
    
    public AiResourceIndexProjection(AiResourceSearchDocument document,
        List<AiResourceSearchChunk> chunks, Map<String, Object> facets,
        List<AiResourceIndexEnhancementContent> enhancementContents) {
        this.document = Objects.requireNonNull(document, "document");
        this.chunks = immutableList(chunks);
        this.facets = immutableMap(facets);
        this.enhancementContents = immutableList(enhancementContents);
    }
    
    public AiResourceSearchDocument getDocument() {
        return document;
    }
    
    public List<AiResourceSearchChunk> getChunks() {
        return chunks;
    }
    
    public Map<String, Object> getFacets() {
        return facets;
    }
    
    public List<AiResourceIndexEnhancementContent> getEnhancementContents() {
        return enhancementContents;
    }
    
    private <T> List<T> immutableList(List<T> values) {
        return values == null || values.isEmpty() ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(values));
    }
    
    private Map<String, Object> immutableMap(Map<String, Object> values) {
        return values == null || values.isEmpty() ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
