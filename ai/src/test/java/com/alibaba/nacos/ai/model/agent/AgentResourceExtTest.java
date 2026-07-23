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

package com.alibaba.nacos.ai.model.agent;

import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentResourceExtTest {
    
    @Test
    void testPlainBeanAccessorsAndJacksonRoundTrip() throws Exception {
        AgentResourceExt resourceExt = new AgentResourceExt();
        assertNull(resourceExt.getSchemaVersion());
        assertNull(resourceExt.getDisplayName());
        assertNull(resourceExt.getIconUrl());
        assertNull(resourceExt.getProvider());
        assertNull(resourceExt.getExtensions());
        assertNull(resourceExt.getVersionCatalog());
        
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        extensions.put("example.com/enabled", true);
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setOnlineVersions(Collections.emptyList());
        
        resourceExt.setSchemaVersion(AgentResourceExt.SCHEMA_VERSION);
        resourceExt.setDisplayName("Nacos Agent");
        resourceExt.setIconUrl("https://nacos.io/icon.png");
        resourceExt.setProvider(provider);
        resourceExt.setExtensions(extensions);
        resourceExt.setVersionCatalog(catalog);
        
        assertEquals(1, resourceExt.getSchemaVersion());
        assertEquals("Nacos Agent", resourceExt.getDisplayName());
        assertEquals("https://nacos.io/icon.png", resourceExt.getIconUrl());
        assertEquals(provider, resourceExt.getProvider());
        assertEquals(extensions, resourceExt.getExtensions());
        assertEquals(catalog, resourceExt.getVersionCatalog());
        
        ObjectMapper mapper = new ObjectMapper();
        AgentResourceExt restored =
            mapper.readValue(mapper.writeValueAsString(resourceExt), AgentResourceExt.class);
        assertEquals(resourceExt.getSchemaVersion(), restored.getSchemaVersion());
        assertEquals(resourceExt.getDisplayName(), restored.getDisplayName());
        assertEquals(resourceExt.getIconUrl(), restored.getIconUrl());
        assertEquals(provider.getName(), restored.getProvider().getName());
        assertEquals(provider.getUrl(), restored.getProvider().getUrl());
        assertEquals(extensions, restored.getExtensions());
        assertEquals(0, restored.getVersionCatalog().getOnlineVersions().size());
    }
}
