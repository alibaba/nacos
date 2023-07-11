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

package com.alibaba.nacos.api.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTypeTest {
    
    @Test
    public void isValidType() {
        assertTrue(ConfigType.isValidType("xml"));
        assertTrue(ConfigType.isValidType("properties"));
        assertTrue(ConfigType.isValidType("json"));
        assertTrue(ConfigType.isValidType("text"));
        assertTrue(ConfigType.isValidType("html"));
        assertTrue(ConfigType.isValidType("yaml"));
        assertTrue(ConfigType.isValidType("unset"));
        assertFalse(ConfigType.isValidType(""));
        assertFalse(ConfigType.isValidType("yml"));
    }
    
    @Test
    public void testGetDefaultType() {
        assertEquals("text", ConfigType.getDefaultType().getType());
    }
}