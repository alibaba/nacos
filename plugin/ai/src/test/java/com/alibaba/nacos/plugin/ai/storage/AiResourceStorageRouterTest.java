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
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageChangeEvent;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageConsistencyMode;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageChangeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceStorageRouterTest {
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
        AiResourceStorageRouter.reset();
    }
    
    @Test
    void testJoinAndRouteStorage() throws NacosException {
        FakeStorage storage = new FakeStorage("fake");
        FakeStorage duplicate = new FakeStorage("fake");
        StorageKey emptyStorageKey = new StorageKey();
        emptyStorageKey.setProvider("fake");
        emptyStorageKey.setKey("resource");
        
        assertTrue(AiResourceStorageRouter.join(storage));
        assertFalse(AiResourceStorageRouter.join(duplicate));
        
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
    
    @Test
    void testRouteRejectsDisabledStorage() {
        AiResourceStorageRouter.join(new FakeStorage("disabled"));
        PluginStateCheckerHolder.setInstance(
            (pluginType, pluginName) -> !PluginType.AI_STORAGE.getType().equals(pluginType)
                || !"disabled".equals(pluginName));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> AiResourceStorageRouter.getInstance()
                .route(new StorageKey("disabled", "resource")));
        
        assertTrue(exception.getMessage().contains("disabled"));
    }
    
    @Test
    void testDefaultConsistencyAndListenerMethodsRemainCompatible() {
        AiResourceStorage storage = new DefaultStorage();
        AiResourceStorageChangeListener listener = event -> {
        };
        
        assertEquals(AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION,
            storage.consistencyMode());
        assertDoesNotThrow(() -> storage.addChangeListener(listener));
        assertDoesNotThrow(() -> storage.removeChangeListener(listener));
    }
    
    @Test
    void testListenerAttachesAfterStorageJoinAndCanBeRemoved() {
        FakeStorage storage = new FakeStorage("first");
        AtomicInteger events = new AtomicInteger();
        AiResourceStorageChangeListener listener = event -> events.incrementAndGet();
        AiResourceStorageRouter.join(storage);
        
        AiResourceStorageRouter.getInstance().addChangeListener(listener);
        AiResourceStorageRouter.getInstance().addChangeListener(listener);
        storage.fire();
        AiResourceStorageRouter.getInstance().removeChangeListener(listener);
        AiResourceStorageRouter.getInstance().removeChangeListener(listener);
        storage.fire();
        
        assertEquals(1, events.get());
        assertEquals(0, storage.listenerCount());
    }
    
    @Test
    void testListenerAttachesToSubsequentlyJoinedStorageAndResetDetachesIt() {
        FakeStorage storage = new FakeStorage("later");
        AtomicInteger events = new AtomicInteger();
        AiResourceStorageChangeListener listener = event -> events.incrementAndGet();
        AiResourceStorageRouter.getInstance().addChangeListener(listener);
        AiResourceStorageRouter.getInstance().addChangeListener(null);
        
        assertTrue(AiResourceStorageRouter.join(storage));
        storage.fire();
        AiResourceStorageRouter.reset();
        storage.fire();
        
        assertEquals(1, events.get());
        assertEquals(0, storage.listenerCount());
    }
    
    @Test
    void testListenerProviderFailuresAreIsolated() {
        ThrowingStorage storage = new ThrowingStorage("throwing");
        AiResourceStorageChangeListener listener = event -> {
        };
        AiResourceStorageRouter.join(storage);
        
        storage.failAdd = true;
        assertDoesNotThrow(
            () -> AiResourceStorageRouter.getInstance().addChangeListener(listener));
        storage.failAdd = false;
        storage.failRemove = true;
        assertDoesNotThrow(
            () -> AiResourceStorageRouter.getInstance().removeChangeListener(listener));
    }
    
    @Test
    void testStorageChangeEventCarriesOpaqueRoutingHints() {
        AiResourceStorageChangeEvent event =
            new AiResourceStorageChangeEvent("provider", "agent", "opaque-key");
        
        assertEquals("provider", event.getProvider());
        assertEquals("agent", event.getResourceType());
        assertEquals("opaque-key", event.getNotificationKey());
    }
    
    private static class FakeStorage implements AiResourceStorage {
        
        private final String type;
        
        private final Set<AiResourceStorageChangeListener> listeners =
            new LinkedHashSet<AiResourceStorageChangeListener>();
        
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
        
        @Override
        public void addChangeListener(AiResourceStorageChangeListener listener) {
            listeners.add(listener);
        }
        
        @Override
        public void removeChangeListener(AiResourceStorageChangeListener listener) {
            listeners.remove(listener);
        }
        
        private void fire() {
            for (AiResourceStorageChangeListener listener : new LinkedHashSet<AiResourceStorageChangeListener>(
                listeners)) {
                listener.onStorageChanged(
                    new AiResourceStorageChangeEvent(type, "agent", "key"));
            }
        }
        
        private int listenerCount() {
            return listeners.size();
        }
    }
    
    private static class DefaultStorage implements AiResourceStorage {
        
        @Override
        public String type() {
            return "default";
        }
        
        @Override
        public void save(StorageKey storageKey, byte[] content) {
        }
        
        @Override
        public byte[] get(StorageKey storageKey) {
            return null;
        }
        
        @Override
        public void delete(StorageKey storageKey) {
        }
    }
    
    private static class ThrowingStorage extends DefaultStorage {
        
        private final String type;
        
        private boolean failAdd;
        
        private boolean failRemove;
        
        private ThrowingStorage(String type) {
            this.type = type;
        }
        
        @Override
        public String type() {
            return type;
        }
        
        @Override
        public void addChangeListener(AiResourceStorageChangeListener listener) {
            if (failAdd) {
                throw new IllegalStateException("add");
            }
        }
        
        @Override
        public void removeChangeListener(AiResourceStorageChangeListener listener) {
            if (failRemove) {
                throw new IllegalStateException("remove");
            }
        }
    }
}
