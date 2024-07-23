/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test RandomUtils.
 *
 * @author zzq
 */
class RandomUtilsTest {
    
    @Test
    void testNextLong() {
        final long result = RandomUtils.nextLong(1L, 199L);
        assertTrue(result >= 1L && result < 199L);
    }
    
    @Test
    void testNextLongWithSame() {
        final long result = RandomUtils.nextLong(1L, 1L);
        assertEquals(1L, result);
    }
    
    @Test
    void testNextLongWithIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            RandomUtils.nextLong(999L, 199L);
        });
    }
    
    @Test
    void testNextLongWithIllegalArgumentException2() {
        assertThrows(IllegalArgumentException.class, () -> {
            RandomUtils.nextLong(-10L, 199L);
        });
    }
    
    @Test
    void testNextInt() {
        final int result = RandomUtils.nextInt(1, 199);
        assertTrue(result >= 1 && result < 199);
    }
    
    @Test
    void testNextIntWithSame() {
        final int result = RandomUtils.nextInt(1, 1);
        assertEquals(1, result);
    }
}
