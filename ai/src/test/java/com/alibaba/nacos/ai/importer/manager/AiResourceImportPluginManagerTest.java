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

package com.alibaba.nacos.ai.importer.manager;

import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportPluginManagerTest {
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void testDefaultDiscoveryAndEmptySupplier() {
        assertTrue(new AiResourceImportPluginManager().loadPlugins().isEmpty());
        
        AiResourceImportPluginManager manager =
            new AiResourceImportPluginManager(() -> null);
        assertTrue(manager.loadPlugins().isEmpty());
    }
    
    @Test
    void testLoadPluginsOnceAndReturnImmutableMap() {
        AtomicInteger loadCount = new AtomicInteger();
        FakeBuilder builder = new FakeBuilder("source-1", "fake", "Fake source",
            Collections.singleton("mcp"));
        AiResourceImportPluginManager manager = new AiResourceImportPluginManager(() -> {
            loadCount.incrementAndGet();
            return Collections.singletonList(builder);
        });
        
        Map<String, AiResourceImportServiceBuilder> first = manager.loadPlugins();
        Map<String, AiResourceImportServiceBuilder> second = manager.loadPlugins();
        
        assertSame(first, second);
        assertSame(builder, first.get("source-1"));
        assertEquals(1, loadCount.get());
        assertThrows(UnsupportedOperationException.class,
            () -> first.put("other", builder));
    }
    
    @Test
    void testRejectBlankAndDuplicatePluginNames() {
        AiResourceImportPluginManager blank = managerWith(
            new FakeBuilder(" ", "fake", "blank", Collections.singleton("mcp")));
        assertThrows(IllegalStateException.class, blank::loadPlugins);
        
        AiResourceImportPluginManager duplicate = managerWith(
            new FakeBuilder("same", "fake", "first", Collections.singleton("mcp")),
            new FakeBuilder("same", "fake", "second", Collections.singleton("skill")));
        assertThrows(IllegalStateException.class, duplicate::loadPlugins);
    }
    
    @Test
    void testListAndResolveEnabledPlugins() throws Exception {
        FakeBuilder mcp = new FakeBuilder("mcp-source", "mcp-registry", "MCP source",
            Collections.singleton("mcp"));
        FakeBuilder skill = new FakeBuilder("skill-source", "skill-source", "Skill source",
            Collections.singleton("skill"));
        AiResourceImportPluginManager manager = managerWith(mcp, skill);
        manager.loadPlugins();
        setModuleEnabled(manager, true);
        PluginStateCheckerHolder.setInstance((type, name) -> "mcp-source".equals(name));
        
        List<AiResourceImportSourceInfo> all = manager.listSourceInfos(null);
        List<AiResourceImportSourceInfo> skills = manager.listSourceInfos("skill");
        
        assertEquals(1, all.size());
        AiResourceImportSourceInfo info = all.get(0);
        assertEquals("mcp-source", info.getSourceId());
        assertEquals("mcp-registry", info.getPluginName());
        assertEquals("MCP source", info.getDisplayName());
        assertEquals("Description for MCP source", info.getDescription());
        assertEquals(Collections.singletonList("mcp"), info.getResourceTypes());
        assertEquals(Arrays.asList("search", "validate", "execute"), info.getCapabilities());
        assertTrue(info.isEnabled());
        assertTrue(skills.isEmpty());
        assertSame(mcp, manager.resolveBuilder("mcp-source", "mcp"));
    }
    
    @Test
    void testResolveFailuresAndDisabledModule() {
        FakeBuilder builder = new FakeBuilder("source-1", "fake", "Fake source",
            Collections.singleton("mcp"));
        AiResourceImportPluginManager manager = managerWith(builder);
        setModuleEnabled(manager, true);
        
        NacosApiException notLoaded = assertThrows(NacosApiException.class,
            () -> manager.resolveBuilder("source-1", "mcp"));
        assertEquals(NacosException.NOT_FOUND, notLoaded.getErrCode());
        
        manager.loadPlugins();
        NacosApiException notFound = assertThrows(NacosApiException.class,
            () -> manager.resolveBuilder("missing", "mcp"));
        assertEquals(NacosException.NOT_FOUND, notFound.getErrCode());
        
        PluginStateCheckerHolder.setInstance((type, name) -> false);
        assertThrows(NacosApiException.class,
            () -> manager.resolveBuilder("source-1", "mcp"));
        
        PluginStateCheckerHolder.setInstance((type, name) -> true);
        assertThrows(NacosApiException.class,
            () -> manager.resolveBuilder("source-1", "skill"));
        
        setModuleEnabled(manager, false);
        NacosApiException disabled = assertThrows(NacosApiException.class,
            () -> manager.listSourceInfos(null));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, disabled.getErrCode());
    }
    
    private AiResourceImportPluginManager managerWith(FakeBuilder... builders) {
        Supplier<Collection<AiResourceImportServiceBuilder>> supplier =
            () -> Arrays.asList(builders);
        return new AiResourceImportPluginManager(supplier);
    }
    
    private void setModuleEnabled(AiResourceImportPluginManager manager, boolean enabled) {
        AiResourceImportProperties properties = new AiResourceImportProperties();
        properties.setEnabled(enabled);
        ReflectionTestUtils.setField(manager, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
    }
    
    private static class FakeBuilder implements AiResourceImportServiceBuilder {
        
        private final String pluginName;
        
        private final String importerType;
        
        private final String displayName;
        
        private final Set<String> resourceTypes;
        
        FakeBuilder(String pluginName, String importerType, String displayName,
            Set<String> resourceTypes) {
            this.pluginName = pluginName;
            this.importerType = importerType;
            this.displayName = displayName;
            this.resourceTypes = resourceTypes;
        }
        
        @Override
        public String pluginName() {
            return pluginName;
        }
        
        @Override
        public String importerType() {
            return importerType;
        }
        
        @Override
        public String displayName() {
            return displayName;
        }
        
        @Override
        public String description() {
            return "Description for " + displayName;
        }
        
        @Override
        public Set<String> supportedResourceTypes() {
            return resourceTypes;
        }
        
        @Override
        public AiResourceImportService build() {
            return null;
        }
    }
}
