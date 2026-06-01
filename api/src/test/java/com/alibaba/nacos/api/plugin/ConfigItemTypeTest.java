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

package com.alibaba.nacos.api.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigItemTypeTest {
    
    @Test
    @DisplayName("test STRING enum value")
    void testStringEnumValue() {
        assertEquals("STRING", ConfigItemType.STRING.name());
    }
    
    @Test
    @DisplayName("test NUMBER enum value")
    void testNumberEnumValue() {
        assertEquals("NUMBER", ConfigItemType.NUMBER.name());
    }
    
    @Test
    @DisplayName("test BOOLEAN enum value")
    void testBooleanEnumValue() {
        assertEquals("BOOLEAN", ConfigItemType.BOOLEAN.name());
    }
    
    @Test
    @DisplayName("test ENUM enum value")
    void testEnumEnumValue() {
        assertEquals("ENUM", ConfigItemType.ENUM.name());
    }
    
    @Test
    @DisplayName("test all enum values count")
    void testAllEnumValuesCount() {
        ConfigItemType[] values = ConfigItemType.values();
        assertEquals(4, values.length);
    }
    
    @Test
    @DisplayName("test enum valueOf")
    void testEnumValueOf() {
        assertEquals(ConfigItemType.STRING, ConfigItemType.valueOf("STRING"));
        assertEquals(ConfigItemType.NUMBER, ConfigItemType.valueOf("NUMBER"));
        assertEquals(ConfigItemType.BOOLEAN, ConfigItemType.valueOf("BOOLEAN"));
        assertEquals(ConfigItemType.ENUM, ConfigItemType.valueOf("ENUM"));
    }
}
