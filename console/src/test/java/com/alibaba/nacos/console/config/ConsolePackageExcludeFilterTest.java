/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.config;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolePackageExcludeFilterTest {
    
    @Test
    void testGetResponsiblePackagePrefix() {
        ConsolePackageExcludeFilter filter = new ConsolePackageExcludeFilter();
        assertEquals("com.alibaba.nacos.console", filter.getResponsiblePackagePrefix());
    }
    
    @Test
    void testIsExcludedAlwaysReturnsTrue() {
        ConsolePackageExcludeFilter filter = new ConsolePackageExcludeFilter();
        assertTrue(
            filter.isExcluded("com.alibaba.nacos.console.SomeClass", Collections.emptySet()));
        assertTrue(filter.isExcluded("any.ClassName", Collections.singleton("@Component")));
    }
}
