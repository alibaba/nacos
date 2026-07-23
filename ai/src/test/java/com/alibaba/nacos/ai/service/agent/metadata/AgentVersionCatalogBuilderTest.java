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

package com.alibaba.nacos.ai.service.agent.metadata;

import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentVersionCatalogBuilderTest {
    
    @Test
    void testEmptyOnlineVersionsRemoveLatestAndPreserveOtherLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("latest", "1.0.0");
        labels.put("archived", "0.9.0");
        
        AgentVersionCatalogBuilder.Result result = AgentVersionCatalogBuilder.build(
            Collections.<String, List<String>>emptyMap(), labels);
        
        assertNull(result.getVersionCatalog().getLatestVersion());
        assertEquals(Collections.emptyList(),
            result.getVersionCatalog().getOnlineVersions());
        assertEquals(Collections.singletonMap("archived", "0.9.0"), result.getLabels());
    }
    
    @Test
    void testBuildSortsVersionsAndLabelsButPreservesInterfaceOrder() {
        Map<String, List<String>> versions = new HashMap<String, List<String>>();
        versions.put("1.0.0", Arrays.asList("grpc", "a2a"));
        versions.put("2.0.0-RC1", Collections.singletonList("A2A-v1"));
        versions.put("2.0.0", Arrays.asList("a2a", "json-rpc"));
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("stable", "1.0.0");
        labels.put("latest", "1.0.0");
        labels.put("canary", "2.0.0");
        labels.put("beta", "2.0.0");
        labels.put("offline", "0.9.0");
        
        AgentVersionCatalogBuilder.Result result =
            AgentVersionCatalogBuilder.build(versions, labels);
        AgentVersionCatalog catalog = result.getVersionCatalog();
        
        assertEquals("1.0.0", catalog.getLatestVersion());
        assertEquals(Arrays.asList("2.0.0", "2.0.0-RC1", "1.0.0"),
            catalogVersions(catalog));
        assertEquals(Arrays.asList("beta", "canary"),
            catalog.getOnlineVersions().get(0).getLabels());
        assertEquals(Collections.emptyList(),
            catalog.getOnlineVersions().get(1).getLabels());
        assertEquals(Collections.singletonList("stable"),
            catalog.getOnlineVersions().get(2).getLabels());
        assertEquals(Arrays.asList("grpc", "a2a"),
            catalog.getOnlineVersions().get(2).getProtocols());
        assertEquals(Arrays.asList("beta", "canary", "latest", "offline", "stable"),
            new ArrayList<String>(result.getLabels().keySet()));
    }
    
    @Test
    void testRepairMissingOrStaleLatestToGreatestOnlineVersion() {
        Map<String, List<String>> versions = new LinkedHashMap<String, List<String>>();
        versions.put("1.0.0-RC2", Collections.singletonList("a2a"));
        versions.put("1.0.0", Collections.singletonList("a2a"));
        
        AgentVersionCatalogBuilder.Result missingLatest = AgentVersionCatalogBuilder.build(
            versions, Collections.<String, String>emptyMap());
        assertEquals("1.0.0", missingLatest.getVersionCatalog().getLatestVersion());
        assertEquals("1.0.0", missingLatest.getLabels().get("latest"));
        
        AgentVersionCatalogBuilder.Result staleLatest = AgentVersionCatalogBuilder.build(
            versions, Collections.singletonMap("latest", "2.0.0"));
        assertEquals("1.0.0", staleLatest.getVersionCatalog().getLatestVersion());
        assertEquals("1.0.0", staleLatest.getLabels().get("latest"));
    }
    
    @Test
    void testVersionAndProtocolIdentityRemainCaseSensitive() {
        Map<String, List<String>> versions = new LinkedHashMap<String, List<String>>();
        versions.put("1.0.0-RC1", Arrays.asList("A2A", "a2a"));
        versions.put("1.0.0-rc1", Collections.singletonList("a2a"));
        
        AgentVersionCatalogBuilder.Result result = AgentVersionCatalogBuilder.build(versions,
            Collections.singletonMap("latest", "1.0.0-rc1"));
        
        assertEquals(Arrays.asList("1.0.0-rc1", "1.0.0-RC1"),
            catalogVersions(result.getVersionCatalog()));
        assertEquals(Arrays.asList("A2A", "a2a"),
            result.getVersionCatalog().getOnlineVersions().get(1).getProtocols());
    }
    
    @Test
    void testResultCollectionsAreImmutable() {
        AgentVersionCatalogBuilder.Result result = AgentVersionCatalogBuilder.build(
            Collections.singletonMap("1.0.0", Collections.singletonList("a2a")),
            Collections.<String, String>emptyMap());
        
        assertThrows(UnsupportedOperationException.class,
            () -> result.getLabels().put("stable", "1.0.0"));
        assertThrows(UnsupportedOperationException.class,
            () -> result.getVersionCatalog().getOnlineVersions().add(
                new AgentVersionCatalogEntry()));
        assertThrows(UnsupportedOperationException.class,
            () -> result.getVersionCatalog().getOnlineVersions().get(0)
                .getProtocols().add("grpc"));
        assertThrows(UnsupportedOperationException.class,
            () -> result.getVersionCatalog().getOnlineVersions().get(0)
                .getLabels().add("stable"));
    }
    
    @Test
    void testRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionCatalogBuilder.build(null, Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionCatalogBuilder.build(Collections.emptyMap(), null));
        assertRejectedVersion("v1.0.0", Collections.singletonList("a2a"));
        assertRejectedVersion("1.0.0", null);
        assertRejectedVersion("1.0.0", Collections.emptyList());
        assertRejectedVersion("1.0.0", Collections.nCopies(17, "a2a"));
        assertRejectedVersion("1.0.0", Arrays.asList("a2a", "a2a"));
        assertRejectedVersion("1.0.0", Collections.singletonList("a2a_rpc"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionCatalogBuilder.build(Collections.emptyMap(),
                Collections.singletonMap("-label", "1.0.0")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionCatalogBuilder.build(Collections.emptyMap(),
                Collections.singletonMap("stable", "v1.0.0")));
    }
    
    private void assertRejectedVersion(String version, List<String> protocols) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionCatalogBuilder.build(
                Collections.singletonMap(version, protocols), Collections.emptyMap()));
    }
    
    private List<String> catalogVersions(AgentVersionCatalog catalog) {
        List<String> result = new ArrayList<String>();
        for (AgentVersionCatalogEntry entry : catalog.getOnlineVersions()) {
            result.add(entry.getVersion());
        }
        return result;
    }
}
