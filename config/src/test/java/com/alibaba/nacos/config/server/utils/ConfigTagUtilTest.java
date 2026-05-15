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

package com.alibaba.nacos.config.server.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTagUtilTest {
    
    @Test
    void testIsIstioNull() {
        assertFalse(ConfigTagUtil.isIstio(null));
    }
    
    @Test
    void testIsIstioEmpty() {
        assertFalse(ConfigTagUtil.isIstio(""));
    }
    
    @Test
    void testIsIstioVirtualService() {
        assertTrue(ConfigTagUtil.isIstio("virtual-service"));
    }
    
    @Test
    void testIsIstioDestinationRule() {
        assertTrue(ConfigTagUtil.isIstio("destination-rule"));
    }
    
    @Test
    void testIsIstioWithOtherTags() {
        assertTrue(ConfigTagUtil.isIstio("tag1,virtual-service,tag2"));
    }
    
    @Test
    void testIsIstioNoMatch() {
        assertFalse(ConfigTagUtil.isIstio("tag1,tag2"));
    }
    
    @Test
    void testGetIstioTypeNull() {
        assertThrows(IllegalArgumentException.class,
            () -> ConfigTagUtil.getIstioType(null));
    }
    
    @Test
    void testGetIstioTypeEmpty() {
        assertNull(ConfigTagUtil.getIstioType(""));
    }
    
    @Test
    void testGetIstioTypeVirtualService() {
        String result = ConfigTagUtil.getIstioType("virtual-service");
        assertEquals("virtualservice", result);
    }
    
    @Test
    void testGetIstioTypeDestinationRule() {
        String result = ConfigTagUtil.getIstioType("destination-rule");
        assertEquals("destinationrule", result);
    }
    
    @Test
    void testGetIstioTypeNoMatch() {
        assertNull(ConfigTagUtil.getIstioType("tag1,tag2"));
    }
    
    @Test
    void testGetIstioTypeWithMultipleTags() {
        String result = ConfigTagUtil.getIstioType("tag1,destination-rule,tag2");
        assertEquals("destinationrule", result);
    }
}
