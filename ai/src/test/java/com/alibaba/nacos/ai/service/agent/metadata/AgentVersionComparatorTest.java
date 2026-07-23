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

package com.alibaba.nacos.ai.service.agent.metadata;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionComparatorTest {
    
    @Test
    void testCoreAndUnboundedNumericPrecedence() {
        assertEqual("1.0.0", "1.0.0");
        assertLower("1.0.0", "2.0.0");
        assertLower("1.9.9", "2.0.0");
        assertLower("1.1.9", "1.2.0");
        assertLower("1.1.1", "1.1.2");
        assertLower("999999999999999999999999999999.0.0",
            "1000000000000000000000000000000.0.0");
    }
    
    @Test
    void testPrereleasePrecedenceAndCaseSensitivity() {
        List<String> ascending = Arrays.asList("1.0.0-0", "1.0.0-1", "1.0.0-2",
            "1.0.0-RC", "1.0.0-RC.1", "1.0.0-RC.2", "1.0.0-RC.10",
            "1.0.0-RC.A", "1.0.0-RC.a", "1.0.0");
        for (int i = 1; i < ascending.size(); i++) {
            assertLower(ascending.get(i - 1), ascending.get(i));
        }
        assertLower("1.0.0-1", "1.0.0-A");
        assertLower("1.0.0-A", "1.0.0-a");
        assertLower("1.0.0-alpha", "1.0.0-alpha-1");
        assertLower("1.0.0-alpha", "1.0.0-alpha.1");
    }
    
    @Test
    void testComparisonIsAntisymmetric() {
        String[] versions = {"0.0.0", "1.0.0-0", "1.0.0-RC1", "1.0.0-rc1",
            "1.0.0", "2.0.0"};
        for (String left : versions) {
            for (String right : versions) {
                int forward = Integer.signum(AgentVersionComparator.compare(left, right));
                int reverse = Integer.signum(AgentVersionComparator.compare(right, left));
                assertEquals(-forward, reverse, left + " / " + right);
            }
        }
    }
    
    @Test
    void testRejectInvalidVersionsWithoutNormalization() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionComparator.compare(null, "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionComparator.compare(" 1.0.0", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionComparator.compare("v1.0.0", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionComparator.compare("1.0.0+build", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionComparator.compare("1.0.0-01", "1.0.0"));
    }
    
    private void assertLower(String lower, String higher) {
        assertTrue(AgentVersionComparator.compare(lower, higher) < 0,
            lower + " should be lower than " + higher);
        assertTrue(AgentVersionComparator.compare(higher, lower) > 0,
            higher + " should be higher than " + lower);
    }
    
    private void assertEqual(String left, String right) {
        assertEquals(0, AgentVersionComparator.compare(left, right));
        assertEquals(0, AgentVersionComparator.compare(right, left));
    }
}
