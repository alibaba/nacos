/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerCheckResultTest {
    
    @Test
    void testSettersAndGetters() {
        ListenerCheckResult result = new ListenerCheckResult();
        result.setHasListener(true);
        result.setCode(200);
        result.setMessage("ok");
        assertTrue(result.isHasListener());
        assertEquals(200, result.getCode());
        assertEquals("ok", result.getMessage());
    }
    
    @Test
    void testDefaults() {
        ListenerCheckResult result = new ListenerCheckResult();
        assertFalse(result.isHasListener());
        assertEquals(0, result.getCode());
        assertNull(result.getMessage());
    }
}
