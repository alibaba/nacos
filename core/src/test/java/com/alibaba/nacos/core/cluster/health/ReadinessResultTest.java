/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessResultTest {
    
    @Test
    void testSuccessResult() {
        ReadinessResult result = new ReadinessResult(true, "OK");
        assertTrue(result.isSuccess());
        assertEquals("OK", result.getResultMessage());
    }
    
    @Test
    void testFailureResult() {
        ReadinessResult result = new ReadinessResult(false, "module1 not in readiness");
        assertFalse(result.isSuccess());
        assertEquals("module1 not in readiness", result.getResultMessage());
    }
    
    @Test
    void testResultWithEmptyMessage() {
        ReadinessResult result = new ReadinessResult(true, "");
        assertTrue(result.isSuccess());
        assertEquals("", result.getResultMessage());
    }
}
