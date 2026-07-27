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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionRangeTest {
    
    @Test
    void testExactAndEqualInclusiveRangeCanonicalization() {
        AgentVersionRange exact = AgentVersionRange.parse("[1.0.0-RC1]");
        assertTrue(exact.contains("1.0.0-RC1"));
        assertFalse(exact.contains("1.0.0-rc1"));
        assertEquals("[1.0.0-RC1]", exact.toString());
        assertEquals("[1.0.0]", AgentVersionRange.parse("[1.0.0,1.0.0]").getValue());
    }
    
    @Test
    void testIntervalBoundaries() {
        AgentVersionRange range = AgentVersionRange.parse("[1.0.0,2.0.0)");
        assertTrue(range.contains("1.0.0"));
        assertTrue(range.contains("1.9.9"));
        assertTrue(range.contains("2.0.0-RC1"));
        assertFalse(range.contains("0.9.9"));
        assertFalse(range.contains("2.0.0"));
        
        assertTrue(AgentVersionRange.parse("[1.0.0,)").contains("999999999999999999999999.0.0"));
        assertTrue(AgentVersionRange.parse("(,2.0.0]").contains("0.0.0"));
        assertFalse(AgentVersionRange.parse("(,2.0.0)").contains("2.0.0"));
    }
    
    @Test
    void testFactoriesValidationAndAccessors() {
        AgentVersion version = AgentVersion.parse("1.0.0-RC1");
        AgentVersionRange exact = AgentVersionRange.exact(version);
        assertEquals(version, exact.getLowerBound());
        assertEquals(version, exact.getUpperBound());
        assertTrue(exact.isLowerInclusive());
        assertTrue(exact.isUpperInclusive());
        assertTrue(AgentVersionRange.isValid("[1.0.0]"));
        assertFalse(AgentVersionRange.isValid("1.0.0"));
        assertThrows(NullPointerException.class, () -> AgentVersionRange.exact(null));
        
        AgentVersionRange open = AgentVersionRange.parse("(,2.0.0)");
        assertNull(open.getLowerBound());
        assertEquals(AgentVersion.parse("2.0.0"), open.getUpperBound());
        assertFalse(open.isLowerInclusive());
        assertFalse(open.isUpperInclusive());
    }
    
    @Test
    void testValueEqualityAndHashCode() {
        AgentVersionRange range = AgentVersionRange.parse("[1.0.0,2.0.0)");
        assertEquals(range, range);
        assertEquals(range, AgentVersionRange.parse("[1.0.0,2.0.0)"));
        assertNotEquals(range, AgentVersionRange.parse("[1.0.0,2.0.0]"));
        assertNotEquals(range, "[1.0.0,2.0.0)");
        assertEquals(range.hashCode(), AgentVersionRange.parse("[1.0.0,2.0.0)").hashCode());
    }
    
    @Test
    void testRejectsInvalidOrDiscontinuousRanges() {
        List<String> invalid =
            Arrays.asList(null, "", "[]", "(,)", "[,1.0.0]", "[1.0.0,]", "[2.0.0,1.0.0]",
                "(1.0.0,1.0.0]", "[1.0.0,1.0.0)", "[1.0.0, 2.0.0)", "[1.0.0],[2.0.0]",
                "[1.0.0,2.0.0,3.0.0]", "[1.0.0+build]", "1.0.0");
        for (String value : invalid) {
            assertThrows(IllegalArgumentException.class, () -> AgentVersionRange.parse(value),
                value);
        }
    }
}
