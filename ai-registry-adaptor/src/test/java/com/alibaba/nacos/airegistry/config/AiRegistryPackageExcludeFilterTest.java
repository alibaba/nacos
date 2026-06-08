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

package com.alibaba.nacos.airegistry.config;

import com.alibaba.nacos.airegistry.NacosAiRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRegistryPackageExcludeFilterTest {
    
    @Test
    void testGetResponsiblePackagePrefix() {
        AiRegistryPackageExcludeFilter filter = new AiRegistryPackageExcludeFilter();
        
        assertEquals(NacosAiRegistry.class.getPackage().getName(),
            filter.getResponsiblePackagePrefix());
    }
    
    @Test
    void testIsExcludedAlwaysReturnsTrue() {
        AiRegistryPackageExcludeFilter filter = new AiRegistryPackageExcludeFilter();
        
        assertTrue(filter.isExcluded("com.alibaba.nacos.airegistry.NacosAiRegistry",
            Collections.emptySet()));
        assertTrue(filter.isExcluded(null, null));
    }
}
