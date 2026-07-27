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

package com.alibaba.nacos.ai.service.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeVersionRangeSupportTest {
    
    @Test
    void testExactAndCanonicalForms() {
        assertEquals("[1.0.0-RC1]", RuntimeVersionRangeSupport.exact("1.0.0-RC1"));
        assertEquals("[1.0.0]",
            RuntimeVersionRangeSupport.canonicalize("[1.0.0,1.0.0]"));
        assertEquals("[1.0.0,2.0.0)",
            RuntimeVersionRangeSupport.canonicalize("[1.0.0,2.0.0)"));
        assertEquals("[1.0.0,2.0.0]",
            RuntimeVersionRangeSupport.canonicalize("[1.0.0,2.0.0]"));
        assertEquals("(,2.0.0]",
            RuntimeVersionRangeSupport.canonicalize("(,2.0.0]"));
        assertEquals("[1.0.0,)",
            RuntimeVersionRangeSupport.canonicalize("[1.0.0,)"));
    }
    
    @Test
    void testContainsClosedOpenAndUnboundedRanges() {
        assertTrue(RuntimeVersionRangeSupport.contains("[1.0.0]", "1.0.0"));
        assertFalse(RuntimeVersionRangeSupport.contains("[1.0.0]", "1.0.1"));
        assertTrue(RuntimeVersionRangeSupport.contains("[1.0.0,2.0.0)", "1.0.0"));
        assertTrue(RuntimeVersionRangeSupport.contains("[1.0.0,2.0.0)", "2.0.0-RC1"));
        assertFalse(RuntimeVersionRangeSupport.contains("[1.0.0,2.0.0)", "2.0.0"));
        assertTrue(RuntimeVersionRangeSupport.contains("(,1.0.0]", "0.0.0"));
        assertTrue(RuntimeVersionRangeSupport.contains("[1.0.0,)", "100.0.0"));
        assertFalse(RuntimeVersionRangeSupport.contains("(1.0.0,)", "1.0.0"));
    }
    
    @Test
    void testCaseSensitivePrereleasePrecedence() {
        assertTrue(RuntimeVersionRangeSupport.contains("[1.0.0-RC1,1.0.0)",
            "1.0.0-rc1"));
        assertFalse(RuntimeVersionRangeSupport.contains("[1.0.0-rc1,1.0.0)",
            "1.0.0-RC1"));
    }
    
    @Test
    void testRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeVersionRangeSupport.exact(null));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeVersionRangeSupport.canonicalize("[2.0.0,1.0.0]"));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeVersionRangeSupport.contains("[1.0.0]", "1.0"));
    }
}
