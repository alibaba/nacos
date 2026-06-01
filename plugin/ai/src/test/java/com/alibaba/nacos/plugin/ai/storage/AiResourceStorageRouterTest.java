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

package com.alibaba.nacos.plugin.ai.storage;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceStorageRouterTest {
    
    @AfterEach
    void tearDown() {
        AiResourceStorageRouter.reset();
    }
    
    @Test
    void testJoinAndRouteStorage() throws NacosException {
        FakeStorage storage = new FakeStorage("fake");
        StorageKey emptyStorageKey = new StorageKey();
        emptyStorageKey.setProvider("fake");
        emptyStorageKey.setKey("resource");
        
        assertTrue(AiResourceStorageRouter.join(storage));
        
        AiResourceStorage routed = AiResourceStorageRouter.getInstance().route(emptyStorageKey);
        routed.save(emptyStorageKey, "content".getBytes(StandardCharsets.UTF_8));
        
        assertSame(storage, routed);
        assertArrayEquals("resource".getBytes(StandardCharsets.UTF_8), routed.get(emptyStorageKey));
        assertEquals(1, AiResourceStorageRouter.getInstance().allStorages().size());
        assertEquals("StorageKey{provider='fake', key='resource'}", emptyStorageKey.toString());
    }
    
    @Test
    void testJoinRejectsInvalidStorageAndRouteRejectsInvalidKey() {
        assertFalse(AiResourceStorageRouter.join(null));
        assertFalse(AiResourceStorageRouter.join(new FakeStorage(" ")));
        
        assertThrows(IllegalArgumentException.class,
            () -> AiResourceStorageRouter.getInstance().route(null));
        assertThrows(IllegalArgumentException.class,
            () -> AiResourceStorageRouter.getInstance().route(new StorageKey("", "key")));
        assertThrows(IllegalStateException.class,
            () -> AiResourceStorageRouter.getInstance().route(new StorageKey("missing", "key")));
    }
    
    private static class FakeStorage implements AiResourceStorage {
        
        private final String type;
        
        private FakeStorage(String type) {
            this.type = type;
        }
        
        @Override
        public String type() {
            return type;
        }
        
        @Override
        public void save(StorageKey storageKey, byte[] content) {
        }
        
        @Override
        public byte[] get(StorageKey storageKey) {
            return storageKey.getKey().getBytes(StandardCharsets.UTF_8);
        }
        
        @Override
        public void delete(StorageKey storageKey) {
        }
    }
}
