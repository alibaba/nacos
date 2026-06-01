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

package com.alibaba.nacos.ai.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AiResourceConstants}.
 *
 * @author nacos
 */
class AiResourceConstantsTest {
    
    @Test
    void testMetaStatusConstants() {
        assertEquals("enable", AiResourceConstants.META_STATUS_ENABLE);
        assertEquals("disable", AiResourceConstants.META_STATUS_DISABLE);
    }
    
    @Test
    void testVersionStatusConstants() {
        assertEquals("online", AiResourceConstants.VERSION_STATUS_ONLINE);
        assertEquals("draft", AiResourceConstants.VERSION_STATUS_DRAFT);
        assertEquals("reviewing", AiResourceConstants.VERSION_STATUS_REVIEWING);
        assertEquals("reviewed", AiResourceConstants.VERSION_STATUS_REVIEWED);
        assertEquals("offline", AiResourceConstants.VERSION_STATUS_OFFLINE);
    }
    
    @Test
    void testMaxWorkingVersionRetry() {
        assertEquals(3, AiResourceConstants.MAX_WORKING_VERSION_RETRY);
    }
    
    @Test
    void testLabelLatest() {
        assertEquals("latest", AiResourceConstants.LABEL_LATEST);
    }
}
