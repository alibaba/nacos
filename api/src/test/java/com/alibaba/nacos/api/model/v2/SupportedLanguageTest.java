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

package com.alibaba.nacos.api.model.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedLanguageTest {
    
    @Test
    void testGetLanguage() {
        assertEquals("zh-CN", SupportedLanguage.ZH_CN.getLanguage());
        assertEquals("en-US", SupportedLanguage.EN_US.getLanguage());
    }
    
    @Test
    void testIsSupported() {
        // 测试支持的语言
        assertTrue(SupportedLanguage.isSupported("zh-CN"));
        assertTrue(SupportedLanguage.isSupported("en-US"));
        
        // 测试不支持的语言
        assertFalse(SupportedLanguage.isSupported("fr-FR"));
        assertFalse(SupportedLanguage.isSupported("ja-JP"));
        assertFalse(SupportedLanguage.isSupported(null));
        assertFalse(SupportedLanguage.isSupported(""));
    }
}