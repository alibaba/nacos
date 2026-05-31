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

package com.alibaba.nacos.ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptDataIdUtilsTest {
    
    @Test
    void buildDataIdsShouldReturnExpectedFormat() {
        assertEquals("p1.descriptor.json", PromptDataIdUtils.buildMetaDataId("p1"));
        assertEquals("p1.descriptor.json", PromptDataIdUtils.buildDescriptorDataId("p1"));
        assertEquals("p1.label-version-mapping.json",
            PromptDataIdUtils.buildLabelVersionMappingDataId("p1"));
        assertEquals("p1.json", PromptDataIdUtils.buildLatestDataId("p1"));
        assertEquals("p1.1.0.0.json", PromptDataIdUtils.buildVersionDataId("p1", "1.0.0"));
    }
    
    @Test
    void isMetaDataIdShouldMatchOnlyMetaSuffix() {
        assertTrue(PromptDataIdUtils.isMetaDataId("p1.descriptor.json"));
        assertTrue(PromptDataIdUtils.isDescriptorDataId("p1.descriptor.json"));
        assertTrue(PromptDataIdUtils.isLabelVersionMappingDataId("p1.label-version-mapping.json"));
        assertFalse(PromptDataIdUtils.isMetaDataId("p1.json"));
        assertFalse(PromptDataIdUtils.isMetaDataId(""));
    }
    
    @Test
    void extractPromptKeyFromMetaDataIdShouldReturnNullForInvalid() {
        assertEquals("p1", PromptDataIdUtils.extractPromptKeyFromMetaDataId("p1.descriptor.json"));
        assertEquals("p1",
            PromptDataIdUtils
                .extractPromptKeyFromLabelVersionMappingDataId("p1.label-version-mapping.json"));
        assertNull(PromptDataIdUtils.extractPromptKeyFromMetaDataId("p1.json"));
    }
}
