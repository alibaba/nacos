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

package com.alibaba.nacos.plugin.visibility.model;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable scope catalog for one visibility service, with reserved built-in scopes.
 */
public final class VisibilityScopeRegistry {
    
    private static final VisibilityScopeRegistry DEFAULTS =
        new VisibilityScopeRegistry(Collections.emptyList());
    
    private final Map<String, VisibilityScopeDefinition> definitions;
    
    /**
     * Build an isolated catalog; extensions cannot replace built-ins or duplicate each other.
     *
     * @param additionalScopes extra declarations from the selected visibility service
     */
    public VisibilityScopeRegistry(Collection<VisibilityScopeDefinition> additionalScopes) {
        Map<String, VisibilityScopeDefinition> entries = new LinkedHashMap<>();
        entries.put(VisibilityConstants.SCOPE_PUBLIC,
            new VisibilityScopeDefinition(VisibilityConstants.SCOPE_PUBLIC, true));
        entries.put(VisibilityConstants.SCOPE_PRIVATE,
            new VisibilityScopeDefinition(VisibilityConstants.SCOPE_PRIVATE, false));
        if (additionalScopes == null) {
            throw new IllegalArgumentException("Additional scopes must not be null");
        }
        for (VisibilityScopeDefinition definition : additionalScopes) {
            if (definition == null
                || entries.putIfAbsent(definition.getScopeName(), definition) != null) {
                throw new IllegalArgumentException(
                    "Null or duplicate visibility scope declaration");
            }
        }
        definitions = Collections.unmodifiableMap(entries);
    }
    
    /**
     * Return the shared immutable catalog of built-in scopes.
     *
     * @return PUBLIC and PRIVATE catalog
     */
    public static VisibilityScopeRegistry defaults() {
        return DEFAULTS;
    }
    
    /**
     * Return all canonical names in this catalog.
     *
     * @return immutable scope names
     */
    public Set<String> getScopeNames() {
        return definitions.keySet();
    }
    
    /**
     * Test an input scope using the historical case-insensitive form semantics.
     */
    public boolean contains(String scope) {
        return scope != null && definitions.keySet().stream().anyMatch(scope::equalsIgnoreCase);
    }
    
    /**
     * Classify a stored scope. This does not grant read/write permissions.
     */
    public boolean isPublicScope(String scope) {
        VisibilityScopeDefinition definition = definitions.get(scope);
        return definition != null && definition.isPublicScope();
    }
    
    /**
     * Return the registered public scope names. The plugin chooses when they apply.
     */
    public List<String> getPublicScopes() {
        List<String> result = new ArrayList<>();
        for (VisibilityScopeDefinition definition : definitions.values()) {
            if (definition.isPublicScope()) {
                result.add(definition.getScopeName());
            }
        }
        return Collections.unmodifiableList(result);
    }
}
