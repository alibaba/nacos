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

package com.alibaba.nacos.common.cache.builder;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheBuilderTest {
    
    @Test
    void testNegativeDuration() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            CacheBuilder.builder().expireNanos(-1, TimeUnit.MINUTES).build();
        });
        assertTrue(exception.getMessage().contains("duration cannot be negative"));
    }
    
    @Test
    void testNullTimeUnit() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            CacheBuilder.builder().expireNanos(500, null).build();
        });
        assertTrue(exception.getMessage().contains("unit cannot be null"));
    }
    
    @Test
    void testNegativeMaximumSize() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            CacheBuilder.builder().maximumSize(-1).build();
        });
        assertTrue(exception.getMessage().contains("size cannot be negative"));
    }
    
    @Test
    void testNegativeInitializeCapacity() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            CacheBuilder.builder().initializeCapacity(-1).build();
        });
        assertTrue(exception.getMessage().contains("initializeCapacity cannot be negative"));
    }
    
}
