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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.TimeUnit;

public class CacheBuilderTest {
    
    @Rule
    public ExpectedException exception = ExpectedException.none();
    
    @Test
    public void testNegativeDuration() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("duration cannot be negative");
        CacheBuilder.builder().expireNanos(-1, TimeUnit.MINUTES).build();
    }
    
    @Test
    public void testNullTimeUnit() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("unit cannot be null");
        CacheBuilder.builder().expireNanos(500, null).build();
    }
    
    @Test
    public void testNegativeMaximumSize() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("size cannot be negative");
        CacheBuilder.builder().maximumSize(-1).build();
    }
    
    @Test
    public void testNegativeInitializeCapacity() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("initializeCapacity cannot be negative");
        CacheBuilder.builder().initializeCapacity(-1).build();
    }
    
}
