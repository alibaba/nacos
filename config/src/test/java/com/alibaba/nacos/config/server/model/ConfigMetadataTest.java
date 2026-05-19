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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigMetadataTest {
    
    @Test
    void testMetadataAccessors() {
        ConfigMetadata.ConfigExportItem item = newItem();
        ConfigMetadata metadata = new ConfigMetadata();
        
        metadata.setMetadata(Collections.singletonList(item));
        
        assertEquals(Collections.singletonList(item), metadata.getMetadata());
    }
    
    @Test
    void testConfigExportItemEqualsAndHashCode() {
        ConfigMetadata.ConfigExportItem item = newItem();
        ConfigMetadata.ConfigExportItem sameItem = newItem();
        ConfigMetadata.ConfigExportItem differentItem = newItem();
        differentItem.setConfigTags("different");
        
        assertEquals(item, item);
        assertEquals(item, sameItem);
        assertEquals(item.hashCode(), sameItem.hashCode());
        assertNotEquals(item, null);
        assertNotEquals(item, "item");
        assertNotEquals(item, differentItem);
    }
    
    @Test
    void testConfigExportItemAccessors() {
        ConfigMetadata.ConfigExportItem item = newItem();
        
        assertEquals("group", item.getGroup());
        assertEquals("dataId", item.getDataId());
        assertEquals("desc", item.getDesc());
        assertEquals("text", item.getType());
        assertEquals("app", item.getAppName());
        assertEquals("tagA,tagB", item.getConfigTags());
    }
    
    private ConfigMetadata.ConfigExportItem newItem() {
        ConfigMetadata.ConfigExportItem item = new ConfigMetadata.ConfigExportItem();
        item.setGroup("group");
        item.setDataId("dataId");
        item.setDesc("desc");
        item.setType("text");
        item.setAppName("app");
        item.setConfigTags("tagA,tagB");
        return item;
    }
}
