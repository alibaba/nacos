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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigChangeItemTest {
    
    @Test
    void testSetNewValue() {
        ConfigChangeItem item = new ConfigChangeItem("testKey", null, "testValue");
        item.setType(PropertyChangeType.ADDED);
        assertEquals("testKey", item.getKey());
        assertNull(item.getOldValue());
        assertEquals("testValue", item.getNewValue());
        assertEquals(PropertyChangeType.ADDED, item.getType());
        item.setOldValue("testValue");
        item.setNewValue("testValue2");
        item.setType(PropertyChangeType.MODIFIED);
        assertEquals("testKey", item.getKey());
        assertEquals("testValue", item.getOldValue());
        assertEquals("testValue2", item.getNewValue());
        assertEquals(PropertyChangeType.MODIFIED, item.getType());
        
        item.setKey("deletedKey");
        item.setType(PropertyChangeType.DELETED);
        assertEquals("deletedKey", item.getKey());
        assertEquals(PropertyChangeType.DELETED, item.getType());
    }
    
    @Test
    void testToString() {
        ConfigChangeItem item = new ConfigChangeItem("testKey", null, "testValue");
        item.setType(PropertyChangeType.ADDED);
        assertEquals("ConfigChangeItem{key='testKey', oldValue='null', newValue='testValue', type=ADDED}",
                item.toString());
    }
}