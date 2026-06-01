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

package com.alibaba.nacos.config.server.model.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDumpEventTest {
    
    @Test
    void testBuilderWithDelimiterAndBatch() {
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .remove(true)
            .namespaceId("namespace")
            .dataId("dataId")
            .group("group")
            .delimiter(1)
            .isBatch(true)
            .isBeta(true)
            .tag("tag")
            .grayName("gray")
            .grayRule("rule")
            .content("content")
            .betaIps("127.0.0.1")
            .handleIp("127.0.0.2")
            .encryptedDataKey("key")
            .type("properties")
            .lastModifiedTs(123L)
            .build();
        
        assertTrue(event.isRemove());
        assertEquals("namespace", event.getNamespaceId());
        assertEquals("dataId", event.getDataId());
        assertEquals("group", event.getGroup());
        assertEquals(1, event.getDelimiter());
        assertTrue(event.isBatch());
        assertTrue(event.isBeta());
        assertEquals("tag", event.getTag());
        assertEquals("gray", event.getGrayName());
        assertEquals("rule", event.getGrayRule());
        assertEquals("content", event.getContent());
        assertEquals("127.0.0.1", event.getBetaIps());
        assertEquals("127.0.0.2", event.getHandleIp());
        assertEquals("key", event.getEncryptedDataKey());
        assertEquals("properties", event.getType());
        assertEquals(123L, event.getLastModifiedTs());
    }
    
    @Test
    void testConfigDataChangeEventRejectsNullDataIdOrGroup() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConfigDataChangeEvent(null, "group", "tenant", 1L));
        assertThrows(IllegalArgumentException.class,
            () -> new ConfigDataChangeEvent("dataId", null, "tenant", 1L));
    }
}
