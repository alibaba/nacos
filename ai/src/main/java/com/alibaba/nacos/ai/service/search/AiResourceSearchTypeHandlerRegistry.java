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

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry that isolates resource-specific projection and validation from the search core.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceSearchTypeHandlerRegistry {
    
    private final Map<String, AiResourceSearchTypeHandler> handlers;
    
    private final List<AiResourceSearchTypeHandler> uniqueHandlers;
    
    public AiResourceSearchTypeHandlerRegistry(List<AiResourceSearchTypeHandler> handlers) {
        Map<String, AiResourceSearchTypeHandler> indexed = new LinkedHashMap<>();
        Set<AiResourceSearchTypeHandler> unique = new LinkedHashSet<>();
        if (handlers != null) {
            for (AiResourceSearchTypeHandler handler : handlers) {
                if (handler == null) {
                    continue;
                }
                register(indexed, handler);
                unique.add(handler);
            }
        }
        this.handlers = Collections.unmodifiableMap(indexed);
        this.uniqueHandlers = Collections.unmodifiableList(new ArrayList<>(unique));
    }
    
    public AiResourceSearchTypeHandler get(String resourceType) {
        return handlers.get(resourceType);
    }
    
    public Collection<String> resourceTypes() {
        return handlers.keySet();
    }
    
    public List<AiResourceSearchTypeHandler> handlers() {
        return uniqueHandlers;
    }
    
    /**
     * Validate a document through the handler that owns its resource type.
     *
     * @param document persisted search document
     * @return {@code true} when the canonical resource remains current and readable
     * @throws NacosException when canonical lookup fails
     */
    public boolean isCurrent(AiResourceSearchDocument document) throws NacosException {
        if (document == null) {
            return false;
        }
        AiResourceSearchTypeHandler handler = get(document.getResourceType());
        return handler != null && handler.isCurrent(document);
    }
    
    private void register(Map<String, AiResourceSearchTypeHandler> indexed,
        AiResourceSearchTypeHandler handler) {
        Collection<String> resourceTypes = handler.resourceTypes();
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            throw new IllegalStateException("AI resource search type handler owns no type: "
                + handler.getClass().getName());
        }
        for (String resourceType : resourceTypes) {
            if (StringUtils.isBlank(resourceType)) {
                throw new IllegalStateException("AI resource search type must not be blank: "
                    + handler.getClass().getName());
            }
            AiResourceSearchTypeHandler previous = indexed.putIfAbsent(resourceType, handler);
            if (previous != null && previous != handler) {
                throw new IllegalStateException("Duplicate AI resource search type handler for "
                    + resourceType + ": " + previous.getClass().getName() + " and "
                    + handler.getClass().getName());
            }
        }
    }
}
