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

package com.alibaba.nacos.config.server.filter;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigEnabledFilterTest {
    
    private ConfigEnabledFilter filter;
    
    private String originalFunctionMode;
    
    @BeforeEach
    void setUp() {
        filter = new ConfigEnabledFilter();
        originalFunctionMode =
            (String) ReflectionTestUtils.getField(EnvUtil.class,
                "functionModeType");
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", null);
    }
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType",
            originalFunctionMode);
    }
    
    @Test
    void testGetResponsiblePackagePrefix() {
        assertNotNull(filter.getResponsiblePackagePrefix());
    }
    
    @Test
    void testIsExcludedWhenNoFunctionMode() {
        assertFalse(filter.isExcluded("SomeClass", Collections.emptySet()));
    }
    
    @Test
    void testIsExcludedWhenConfigMode() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType",
            "config");
        assertFalse(filter.isExcluded("SomeClass", Collections.emptySet()));
    }
    
    @Test
    void testIsExcludedWhenNamingMode() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType",
            "naming");
        assertTrue(filter.isExcluded("SomeClass", Collections.emptySet()));
    }
}
