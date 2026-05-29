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

package com.alibaba.nacos.naming.config;

import com.alibaba.nacos.naming.NamingApp;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingEnabledFilterTest {
    
    private NamingEnabledFilter filter;
    
    private String originalFunctionMode;
    
    @BeforeEach
    void setUp() {
        filter = new NamingEnabledFilter();
        originalFunctionMode =
            (String) ReflectionTestUtils.getField(EnvUtil.class, "functionModeType");
    }
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", originalFunctionMode);
    }
    
    @Test
    void testGetResponsiblePackagePrefix() {
        assertEquals(NamingApp.class.getPackage().getName(),
            filter.getResponsiblePackagePrefix());
    }
    
    @Test
    void testIsExcludedWhenFunctionModeIsEmpty() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "");
        
        assertFalse(filter.isExcluded("com.alibaba.nacos.naming.Test",
            Collections.emptySet()));
    }
    
    @Test
    void testIsExcludedWhenFunctionModeAllowsNaming() {
        String[] functionModes = new String[] {EnvUtil.FUNCTION_MODE_NAMING,
            EnvUtil.FUNCTION_MODE_MICROSERVICE, EnvUtil.FUNCTION_MODE_AI};
        for (String functionMode : functionModes) {
            ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", functionMode);
            
            assertFalse(filter.isExcluded("com.alibaba.nacos.naming.Test",
                Collections.emptySet()));
        }
    }
    
    @Test
    void testIsExcludedWhenFunctionModeDisablesNaming() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType",
            EnvUtil.FUNCTION_MODE_CONFIG);
        
        assertTrue(filter.isExcluded("com.alibaba.nacos.naming.Test", Collections.emptySet()));
    }
}
