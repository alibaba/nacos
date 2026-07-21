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

package com.alibaba.nacos.api.ai.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionTest {
    
    @Test
    void testParsePreservesCaseAndSupportsLargeNumbers() {
        assertEquals("1.2.3-RC1", AgentVersion.parse("1.2.3-RC1").getValue());
        assertEquals("999999999999999999999999999999.0.0",
            AgentVersion.parse("999999999999999999999999999999.0.0").toString());
        assertTrue(AgentVersion.isValid("1.0.0-RC1.2"));
        assertFalse(AgentVersion.isValid("1.0.0-01"));
    }
    
    @Test
    void testSemverPrecedenceAndCaseSensitiveIdentifiers() {
        List<AgentVersion> versions = Arrays.asList(AgentVersion.parse("1.0.0"),
            AgentVersion.parse("1.0.0-beta.11"), AgentVersion.parse("1.0.0-alpha.beta"),
            AgentVersion.parse("1.0.0-rc.1"), AgentVersion.parse("1.0.0-alpha"),
            AgentVersion.parse("1.0.0-beta"), AgentVersion.parse("1.0.0-alpha.1"),
            AgentVersion.parse("1.0.0-beta.2"));
        Collections.sort(versions);
        assertEquals(
            Arrays.asList(AgentVersion.parse("1.0.0-alpha"), AgentVersion.parse("1.0.0-alpha.1"),
                AgentVersion.parse("1.0.0-alpha.beta"), AgentVersion.parse("1.0.0-beta"),
                AgentVersion.parse("1.0.0-beta.2"), AgentVersion.parse("1.0.0-beta.11"),
                AgentVersion.parse("1.0.0-rc.1"), AgentVersion.parse("1.0.0")),
            versions);
        assertTrue(AgentVersion.parse("1.0.0-RC1").compareTo(AgentVersion.parse("1.0.0-rc1")) < 0);
    }
    
    @Test
    void testNumericComparisonDoesNotOverflow() {
        AgentVersion larger = AgentVersion.parse("999999999999999999999999999999.0.0");
        AgentVersion smaller =
            AgentVersion.parse("9223372036854775808.999999999999999999.999999999999999999");
        assertTrue(larger.compareTo(smaller) > 0);
        assertTrue(AgentVersion.parse("1.0.0-999999999999999999999999999999")
            .compareTo(AgentVersion.parse("1.0.0-9223372036854775808")) > 0);
    }
    
    @Test
    void testStableAndMixedPrereleaseComparison() {
        assertEquals(0, AgentVersion.parse("1.0.0").compareTo(AgentVersion.parse("1.0.0")));
        assertTrue(AgentVersion.parse("1.0.0").compareTo(AgentVersion.parse("1.0.0-RC1")) > 0);
        assertTrue(AgentVersion.parse("1.0.0-1").compareTo(AgentVersion.parse("1.0.0-alpha")) < 0);
        assertTrue(AgentVersion.parse("1.0.0-alpha").compareTo(AgentVersion.parse("1.0.0-1")) > 0);
    }
    
    @Test
    void testValueEqualityAndHashCode() {
        AgentVersion version = AgentVersion.parse("1.2.3-RC1");
        assertEquals(version, version);
        assertEquals(version, AgentVersion.parse("1.2.3-RC1"));
        assertNotEquals(version, AgentVersion.parse("1.2.3-rc1"));
        assertNotEquals(version, "1.2.3-RC1");
        assertEquals(version.hashCode(), AgentVersion.parse("1.2.3-RC1").hashCode());
    }
    
    @Test
    void testRejectsNonCanonicalOrExtendedVersions() {
        List<String> invalid =
            Arrays.asList(null, "", " 1.0.0", "1.0.0 ", "v1.0.0", "1.0", "1.0.0+build",
                "01.0.0", "1.01.0", "1.0.01", "1.0.0-", "1.0.0-.RC1", "1.0.0-RC1.", "1.0.0-RC_1");
        for (String value : invalid) {
            assertThrows(IllegalArgumentException.class, () -> AgentVersion.parse(value), value);
        }
        assertThrows(IllegalArgumentException.class, () -> AgentVersion.parse("1.0.0-"
            + "a123456789012345678901234567890123456789012345678901234567890"));
    }
}
