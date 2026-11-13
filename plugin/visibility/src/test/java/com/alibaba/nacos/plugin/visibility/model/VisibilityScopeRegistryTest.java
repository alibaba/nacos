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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityScopeRegistryTest {
    
    @Test
    void defaultsRetainPublicAndPrivateClassification() {
        VisibilityScopeRegistry scopes = VisibilityScopeRegistry.defaults();
        assertEquals(Arrays.asList("PUBLIC", "PRIVATE"), new ArrayList<>(scopes.getScopeNames()));
        assertTrue(scopes.contains("private"));
        assertTrue(scopes.contains("publİc"));
        assertFalse(scopes.contains(" PUBLIC"));
        assertFalse(scopes.contains(null));
        assertEquals(Collections.singletonList("PUBLIC"), scopes.getPublicScopes());
        assertTrue(scopes.isPublicScope("PUBLIC"));
        assertFalse(scopes.isPublicScope("PRIVATE"));
        assertFalse(scopes.isPublicScope("UNKNOWN"));
        assertFalse(scopes.isPublicScope(null));
    }
    
    @Test
    void declarationsAreNormalizedImmutableAndIsolated() {
        VisibilityScopeDefinition publicScope = new VisibilityScopeDefinition("team_edit", true);
        VisibilityScopeDefinition privateScope = new VisibilityScopeDefinition("team_only", false);
        List<VisibilityScopeDefinition> definitions =
            new ArrayList<>(Arrays.asList(publicScope, privateScope));
        VisibilityScopeRegistry scopes = new VisibilityScopeRegistry(definitions);
        definitions.clear();
        assertEquals("TEAM_EDIT", publicScope.getScopeName());
        assertTrue(publicScope.isPublicScope());
        assertFalse(privateScope.isPublicScope());
        assertTrue(scopes.contains("team_edit"));
        assertTrue(scopes.contains("team_only"));
        assertTrue(scopes.isPublicScope("TEAM_EDIT"));
        assertFalse(scopes.isPublicScope("TEAM_ONLY"));
        assertEquals(Arrays.asList("PUBLIC", "TEAM_EDIT"), scopes.getPublicScopes());
        assertFalse(VisibilityScopeRegistry.defaults().contains("TEAM_EDIT"));
        assertThrows(UnsupportedOperationException.class, () -> scopes.getScopeNames().clear());
        assertThrows(UnsupportedOperationException.class, () -> scopes.getPublicScopes().clear());
    }
    
    @Test
    void invalidAndDuplicateNamesAreRejected() {
        for (String name : Arrays.asList(null, "", " ", "bad-name", "1NAME", "ABCDEFGHIJKLMNOPQ")) {
            assertThrows(IllegalArgumentException.class,
                () -> new VisibilityScopeDefinition(name, true));
            assertThrows(IllegalArgumentException.class,
                () -> new VisibilityScopeDefinition(name, false));
        }
        assertEquals("ABCDEFGHIJKLMNOP",
            new VisibilityScopeDefinition("ABCDEFGHIJKLMNOP", false).getScopeName());
        assertThrows(IllegalArgumentException.class, () -> new VisibilityScopeRegistry(null));
        assertThrows(IllegalArgumentException.class,
            () -> new VisibilityScopeRegistry(Collections.singletonList(null)));
        for (String name : Arrays.asList("public", "private")) {
            assertThrows(IllegalArgumentException.class, () -> new VisibilityScopeRegistry(
                Collections.singletonList(new VisibilityScopeDefinition(name, true))));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new VisibilityScopeRegistry(Arrays.asList(
                new VisibilityScopeDefinition("team", true),
                new VisibilityScopeDefinition("TEAM", false))));
    }
}
