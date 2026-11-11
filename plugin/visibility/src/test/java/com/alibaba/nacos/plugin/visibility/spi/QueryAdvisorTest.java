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

package com.alibaba.nacos.plugin.visibility.spi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryAdvisorTest {
    
    @Test
    void publicScopesKeepLegacyDefaultAndDistinguishEmpty() {
        QueryAdvisor advisor = new QueryAdvisor();
        assertEquals(Collections.singletonList("PUBLIC"), advisor.getPublicScopes());
        advisor.setPublicScopes(Collections.emptyList());
        assertTrue(advisor.getPublicScopes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> advisor.setPublicScopes(null));
        assertTrue(advisor.getPublicScopes().isEmpty());
    }
    
    @Test
    void invalidScopeNamesCannotRemoveVisibilityRestrictions() {
        QueryAdvisor advisor = new QueryAdvisor();
        advisor.setPublicScopes(Collections.emptyList());
        for (String invalid : Arrays.asList(null, "", " ", "\t", "PUBLIC ", "public", "1TEAM",
            "TEAM-EDIT", "ABCDEFGHIJKLMNOPQ", "PUBLIC') OR 1=1 --")) {
            assertThrows(IllegalArgumentException.class,
                () -> advisor.setPublicScopes(Collections.singletonList(invalid)));
            assertThrows(IllegalArgumentException.class,
                () -> advisor.setPublicScopes(Arrays.asList("PUBLIC", invalid)));
            assertTrue(advisor.getPublicScopes().isEmpty());
        }
    }
    
    @Test
    void scopeValuesAreDefensivelyCopied() {
        QueryAdvisor advisor = new QueryAdvisor();
        List<String> scopes = new ArrayList<>(Arrays.asList("PUBLIC", "TEAM_EDIT"));
        advisor.setPublicScopes(scopes);
        scopes.clear();
        assertEquals(Arrays.asList("PUBLIC", "TEAM_EDIT"), advisor.getPublicScopes());
        assertThrows(UnsupportedOperationException.class, () -> advisor.getPublicScopes().clear());
    }
}
