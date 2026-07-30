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

package com.alibaba.nacos.core.plugin.config.storage;

import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalFilePluginConfigStorageTest {
    
    @Test
    void delegatesStorageOperations() {
        PluginStatePersistenceService persistence = mock(PluginStatePersistenceService.class);
        Map<String, Map<String, String>> configs = Collections.singletonMap("trace:test",
            Collections.singletonMap("endpoint", "value"));
        when(persistence.loadAllConfigs()).thenReturn(configs);
        LocalFilePluginConfigStorage storage = new LocalFilePluginConfigStorage(persistence);
        
        assertSame(configs, storage.loadAllConfigs());
        storage.saveConfig("trace:test", configs.get("trace:test"));
        storage.replaceAllConfigs(configs);
        
        verify(persistence).saveConfig("trace:test", configs.get("trace:test"));
        verify(persistence).replaceAllConfigs(configs);
    }
    
    @Test
    void rejectsMissingPersistenceInsteadOfSilentlyDiscardingWrites() {
        assertThrows(NullPointerException.class, () -> new LocalFilePluginConfigStorage(null));
    }
    
    @Test
    void providerExposesBuiltInMetadataAndCreatesStorage() {
        PluginStatePersistenceService persistence = mock(PluginStatePersistenceService.class);
        LocalFilePluginConfigStorageProvider provider =
            new LocalFilePluginConfigStorageProvider(persistence);
        
        assertEquals("local-file", provider.getName());
        assertEquals(Integer.MAX_VALUE, provider.getOrder());
        assertTrue(provider.isEnabledByDefault());
        assertTrue(provider.createStorage() instanceof LocalFilePluginConfigStorage);
    }
}
