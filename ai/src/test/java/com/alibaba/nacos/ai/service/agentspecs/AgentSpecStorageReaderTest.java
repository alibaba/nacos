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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSpecStorageReaderTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String NAME = "demo-worker";
    
    private static final String VERSION = "1.0.0";
    
    @Mock
    private AiResourceStorageRouter storageRouter;
    
    @Mock
    private AiResourceStorage storage;
    
    private final Map<String, byte[]> values = new HashMap<>();
    
    private AgentSpecStorageReader reader;
    
    @BeforeEach
    void setUp() throws Exception {
        reader = new AgentSpecStorageReader(storageRouter);
        when(storageRouter.route(any())).thenReturn(storage);
        when(storage.get(any())).thenAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            return values.get(key.getProvider() + ':' + key.getKey());
        });
    }
    
    @Test
    void shouldReadCompletePackageFromPersistedProvider() throws Exception {
        put("external", mainPath(), """
            {"name":"demo-worker","description":"Demo","content":"{\\"version\\":1}",
             "resources":[null,{"name":"guide.md","type":"docs"},
               {"name":"missing.md","type":"docs"}]}
            """);
        put("external", resourcePath("docs", "guide.md"),
            "{\"name\":\"guide.md\",\"type\":\"docs\",\"content\":\"Guide\"}");
        
        AgentSpec result = reader.read(NAMESPACE_ID, NAME, VERSION,
            "{\"provider\":\" external \"}");
        
        assertEquals(NAMESPACE_ID, result.getNamespaceId());
        assertEquals(NAME, result.getName());
        assertEquals("Demo", result.getDescription());
        assertEquals("{\"version\":1}", result.getContent());
        assertEquals(1, result.getResource().size());
        assertEquals("Guide", result.getResource().get(resourceId("docs", "guide.md"))
            .getContent());
    }
    
    @Test
    void shouldReadMetadataWithoutLoadingResourceContent() throws Exception {
        put(NacosConfigAiResourceStorage.TYPE, mainPath(), """
            {"name":"demo-worker","description":"Demo","content":"{}",
             "resources":[null,{"name":"guide.md","type":"docs"}]}
            """);
        
        AgentSpec result = reader.readMeta(NAMESPACE_ID, NAME, VERSION, "not-json");
        
        assertEquals(1, result.getResource().size());
        assertNotNull(result.getResource().get(resourceId("docs", "guide.md")));
        assertNull(result.getResource().get(resourceId("docs", "guide.md")).getContent());
        verify(storage).get(any());
    }
    
    @Test
    void shouldSupportMissingResourceListAndDefaultProvider() throws Exception {
        put(NacosConfigAiResourceStorage.TYPE, mainPath(),
            "{\"name\":\"demo-worker\",\"content\":\"{}\"}");
        
        AgentSpec result = reader.read(NAMESPACE_ID, NAME, VERSION, null);
        
        assertTrueEmpty(result.getResource());
    }
    
    @Test
    void shouldReportMissingMainPackageAsNotFound() {
        NacosException exception = assertThrows(NacosException.class,
            () -> reader.read(NAMESPACE_ID, NAME, VERSION, "{}"));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    private void assertTrueEmpty(Map<?, ?> value) {
        assertNotNull(value);
        assertEquals(0, value.size());
    }
    
    private void put(String provider, String path, String value) {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider, NAMESPACE_ID,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, NAME, VERSION, path);
        values.put(provider + ':' + key.getKey(), value.getBytes(StandardCharsets.UTF_8));
    }
    
    private String mainPath() {
        return NacosConfigAiResourceStorage.getMainFilePath("manifest.json");
    }
    
    private String resourcePath(String type, String name) {
        return NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(type, name);
    }
    
    private String resourceId(String type, String name) {
        return AgentSpecUtils.generateResourceId(type, name);
    }
}
