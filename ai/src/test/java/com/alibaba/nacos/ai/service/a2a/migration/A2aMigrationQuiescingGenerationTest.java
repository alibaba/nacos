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

package com.alibaba.nacos.ai.service.a2a.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aMigrationQuiescingGenerationTest {
    
    @Test
    void shouldBindOpaqueGenerationToExactMemberView() {
        A2aMigrationMemberView first = new A2aMigrationMemberView("members-a", 2);
        A2aMigrationMemberView same = new A2aMigrationMemberView("members-a", 2);
        A2aMigrationMemberView changed = new A2aMigrationMemberView("members-b", 2);
        String generation = A2aMigrationQuiescingGeneration.create(first, "nonce");
        assertEquals("members-a", first.getFingerprint());
        assertEquals(2, first.getMemberCount());
        assertEquals(first, first);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, changed);
        assertNotEquals(first, null);
        assertNotEquals(first, "members-a");
        assertTrue(A2aMigrationQuiescingGeneration.matches(generation, same));
        assertFalse(A2aMigrationQuiescingGeneration.matches(generation, changed));
    }
    
    @Test
    void shouldRejectInvalidGenerationShapes() {
        A2aMigrationMemberView view = new A2aMigrationMemberView("members", 1);
        assertThrows(IllegalArgumentException.class,
            () -> A2aMigrationQuiescingGeneration.create(null, "nonce"));
        assertThrows(IllegalArgumentException.class,
            () -> A2aMigrationQuiescingGeneration.create(
                new A2aMigrationMemberView(" ", 1), "nonce"));
        assertThrows(IllegalArgumentException.class,
            () -> A2aMigrationQuiescingGeneration.create(view, " "));
        assertThrows(IllegalArgumentException.class,
            () -> A2aMigrationQuiescingGeneration.create(view, "bad:nonce"));
        assertFalse(A2aMigrationQuiescingGeneration.matches(null, view));
        assertFalse(A2aMigrationQuiescingGeneration.matches("legacy", view));
        assertFalse(A2aMigrationQuiescingGeneration.matches("q1::nonce", view));
        assertFalse(A2aMigrationQuiescingGeneration.matches("q1:members:", view));
        assertFalse(A2aMigrationQuiescingGeneration.matches("q1:members:nonce", null));
    }
}
