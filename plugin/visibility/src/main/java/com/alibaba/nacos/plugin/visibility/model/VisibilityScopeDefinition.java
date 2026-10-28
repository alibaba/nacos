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

import java.util.Locale;

/**
 * Immutable scope name and public classification. Operation permissions belong to the plugin.
 */
public final class VisibilityScopeDefinition {
    
    private final String scopeName;
    
    private final boolean publicScope;
    
    /**
     * Declare a scope without defining read/write permissions.
     *
     * @param scopeName resource scope value, not a plugin name; normalized without trimming
     * @param publicScope whether the scope belongs to the public category
     */
    public VisibilityScopeDefinition(String scopeName, boolean publicScope) {
        if (scopeName == null || !scopeName.matches("[a-zA-Z][a-zA-Z0-9_]{0,15}")) {
            throw new IllegalArgumentException(
                "Scope name must start with a letter and contain 1-16 letters, digits or underscores");
        }
        this.scopeName = scopeName.toUpperCase(Locale.ROOT);
        this.publicScope = publicScope;
    }
    
    /**
     * Return the canonical uppercase scope name.
     *
     * @return scope name
     */
    public String getScopeName() {
        return scopeName;
    }
    
    /**
     * Return the public classification, without granting resource access.
     *
     * @return whether the scope belongs to the public category
     */
    public boolean isPublicScope() {
        return publicScope;
    }
}
