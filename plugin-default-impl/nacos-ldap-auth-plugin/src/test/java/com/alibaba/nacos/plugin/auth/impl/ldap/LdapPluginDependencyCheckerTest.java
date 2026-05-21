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

package com.alibaba.nacos.plugin.auth.impl.ldap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LdapPluginDependencyCheckerTest {
    
    @Test
    void testHasRequiredDependencyWithKnownPresentClass() {
        assertTrue(LdapPluginDependencyChecker.hasRequiredDependency("java.lang.String"));
    }
    
    @Test
    void testHasRequiredDependencyWithMissingClass() {
        String missingClassName = "com.alibaba.nacos.test.MissingClass";
        assertFalse(LdapPluginDependencyChecker.hasRequiredDependency(missingClassName));
    }
}
