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

import com.alibaba.nacos.ai.model.agent.AgentResourceExt;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResourceExtSerializerTest {
    
    private static final String MINIMAL_JSON =
        "{\"schemaVersion\":1,\"versionCatalog\":{\"onlineVersions\":[]}}";
    
    @Test
    void testFullRoundTripUsesFixedProjection() {
        AgentResourceExt original = createFullResourceExt();
        
        String json = AgentResourceExtSerializer.serialize(original);
        AgentResourceExt restored = AgentResourceExtSerializer.deserialize(json);
        
        assertTrue(json.startsWith("{\"schemaVersion\":1,\"displayName\":"));
        assertTrue(json.contains("\"provider\":{\"name\":\"Nacos\",\"url\":"));
        assertTrue(json.indexOf("\"extensions\"") < json.indexOf("\"versionCatalog\""));
        assertEquals(original.getSchemaVersion(), restored.getSchemaVersion());
        assertEquals(original.getDisplayName(), restored.getDisplayName());
        assertEquals(original.getIconUrl(), restored.getIconUrl());
        assertEquals(original.getProvider().getName(), restored.getProvider().getName());
        assertEquals(original.getProvider().getUrl(), restored.getProvider().getUrl());
        assertEquals(original.getExtensions(), restored.getExtensions());
        assertCatalogEquals(original.getVersionCatalog(), restored.getVersionCatalog());
    }
    
    @Test
    void testMinimalRoundTripOmitsAbsentFields() {
        AgentResourceExt resourceExt = createMinimalResourceExt();
        
        String json = AgentResourceExtSerializer.serialize(resourceExt);
        AgentResourceExt restored = AgentResourceExtSerializer.deserialize(json);
        
        assertEquals(MINIMAL_JSON, json);
        assertEquals(1, restored.getSchemaVersion());
        assertNull(restored.getDisplayName());
        assertNull(restored.getIconUrl());
        assertNull(restored.getProvider());
        assertNull(restored.getExtensions());
        assertNull(restored.getVersionCatalog().getLatestVersion());
        assertEquals(Collections.emptyList(),
            restored.getVersionCatalog().getOnlineVersions());
    }
    
    @Test
    void testAcceptSchemaCapacityBoundaries() {
        AgentResourceExt resourceExt = createMinimalResourceExt();
        resourceExt.setDisplayName(repeat("😀", 128));
        AgentProvider provider = new AgentProvider();
        provider.setName(repeat("供", 128));
        provider.setUrl("https://example.com/" + repeat("a", 2028));
        resourceExt.setProvider(provider);
        
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 31; i++) {
            extensions.put("key-" + i, i);
        }
        extensions.put(repeat("键", 128), null);
        resourceExt.setExtensions(extensions);
        
        AgentResourceExtSerializer.deserialize(AgentResourceExtSerializer.serialize(resourceExt));
        
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("k", repeat("a", 16376)));
        String exactLimitJson = AgentResourceExtSerializer.serialize(resourceExt);
        assertTrue(exactLimitJson.contains(repeat("a", 16376)));
    }
    
    @Test
    void testRejectValuesAboveCapacityBoundaries() {
        AgentResourceExt resourceExt = createMinimalResourceExt();
        resourceExt.setDisplayName(repeat("😀", 129));
        assertEncodeRejected(resourceExt);
        
        resourceExt = createMinimalResourceExt();
        AgentProvider provider = new AgentProvider();
        provider.setName("");
        resourceExt.setProvider(provider);
        assertEncodeRejected(resourceExt);
        provider.setName(repeat("供", 129));
        assertEncodeRejected(resourceExt);
        provider.setName("Nacos");
        provider.setUrl("https://example.com/" + repeat("a", 2029));
        assertEncodeRejected(resourceExt);
        
        resourceExt = createMinimalResourceExt();
        Map<String, Object> tooMany = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 33; i++) {
            tooMany.put("key-" + i, i);
        }
        resourceExt.setExtensions(tooMany);
        assertEncodeRejected(resourceExt);
        
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("", true));
        assertEncodeRejected(resourceExt);
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap(repeat("键", 129), true));
        assertEncodeRejected(resourceExt);
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("k", repeat("a", 16377)));
        assertEncodeRejected(resourceExt);
    }
    
    @Test
    void testAcceptNestedJsonExtensionValues() {
        AgentResourceExt resourceExt = createMinimalResourceExt();
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("string", "value");
        nested.put("boolean", true);
        nested.put("integer", 1);
        nested.put("float", 1.25F);
        nested.put("decimal", new BigDecimal("1.25"));
        nested.put("null", null);
        nested.put("list", Arrays.<Object>asList("a", false, null));
        resourceExt.setExtensions(Collections.<String, Object>singletonMap("nested", nested));
        
        AgentResourceExt restored =
            AgentResourceExtSerializer
                .deserialize(AgentResourceExtSerializer.serialize(resourceExt));
        
        assertTrue(restored.getExtensions().get("nested") instanceof Map);
    }
    
    @Test
    void testRejectNonJsonExtensionValues() {
        AgentResourceExt resourceExt = createMinimalResourceExt();
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("value", new Object()));
        assertEncodeRejected(resourceExt);
        
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("value", Double.NaN));
        assertEncodeRejected(resourceExt);
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("value", Float.POSITIVE_INFINITY));
        assertEncodeRejected(resourceExt);
        
        Map<Object, Object> invalidNestedMap = new LinkedHashMap<Object, Object>();
        invalidNestedMap.put(1, "value");
        resourceExt.setExtensions(
            Collections.<String, Object>singletonMap("value", invalidNestedMap));
        assertEncodeRejected(resourceExt);
        
        Map<Object, Object> invalidRootMap = new LinkedHashMap<Object, Object>();
        invalidRootMap.put(1, "value");
        resourceExt.setExtensions(asStringMap(invalidRootMap));
        assertEncodeRejected(resourceExt);
    }
    
    @Test
    void testRejectInvalidSchemaAndUris() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentResourceExtSerializer.validate(null));
        AgentResourceExt resourceExt = createMinimalResourceExt();
        resourceExt.setSchemaVersion(null);
        assertEncodeRejected(resourceExt);
        resourceExt.setSchemaVersion(2);
        assertEncodeRejected(resourceExt);
        
        resourceExt = createMinimalResourceExt();
        resourceExt.setIconUrl("");
        assertEncodeRejected(resourceExt);
        resourceExt.setIconUrl("icons/agent.png");
        assertEncodeRejected(resourceExt);
        resourceExt.setIconUrl("https://[");
        assertEncodeRejected(resourceExt);
        
        resourceExt = createMinimalResourceExt();
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("nacos.io/provider");
        resourceExt.setProvider(provider);
        assertEncodeRejected(resourceExt);
    }
    
    @Test
    void testRejectInvalidCatalogFactsAndOrder() {
        AgentResourceExt resourceExt = createMinimalResourceExt();
        resourceExt.setVersionCatalog(null);
        assertEncodeRejected(resourceExt);
        
        resourceExt = createFullResourceExt();
        Collections.reverse(resourceExt.getVersionCatalog().getOnlineVersions());
        assertEncodeRejected(resourceExt);
        
        resourceExt = createFullResourceExt();
        resourceExt.getVersionCatalog().setLatestVersion("3.0.0");
        assertEncodeRejected(resourceExt);
        
        resourceExt = createFullResourceExt();
        resourceExt.getVersionCatalog().getOnlineVersions().get(0)
            .setProtocols(Arrays.asList("a2a", "a2a"));
        assertEncodeRejected(resourceExt);
        
        resourceExt = createFullResourceExt();
        resourceExt.getVersionCatalog().getOnlineVersions().get(0)
            .setLabels(Collections.singletonList("latest"));
        assertEncodeRejected(resourceExt);
        
        resourceExt = createFullResourceExt();
        resourceExt.getVersionCatalog().getOnlineVersions().get(1)
            .setLabels(Collections.singletonList("stable"));
        assertEncodeRejected(resourceExt);
    }
    
    @Test
    void testRejectInvalidRootJsonShape() {
        assertDecodeRejected(null);
        assertDecodeRejected("");
        assertDecodeRejected("   ");
        assertDecodeRejected("not-json");
        assertDecodeRejected("null");
        assertDecodeRejected("[]");
        assertDecodeRejected("\"ext\"");
        assertDecodeRejected("1");
        assertDecodeRejected(MINIMAL_JSON + "{}");
        assertDecodeRejected(MINIMAL_JSON.substring(0, MINIMAL_JSON.length() - 1)
            + ",\"unknown\":true}");
        assertDecodeRejected(MINIMAL_JSON.replace("\"schemaVersion\":1,",
            "\"schemaVersion\":1,\"schemaVersion\":1,"));
        assertDecodeRejected(MINIMAL_JSON.replace("\"schemaVersion\":1",
            "\"schemaVersion\":1.0"));
        assertDecodeRejected(MINIMAL_JSON.replace("\"schemaVersion\":1",
            "\"schemaVersion\":2147483648"));
        assertDecodeRejected("{\"versionCatalog\":{\"onlineVersions\":[]}}");
        assertDecodeRejected("{\"schemaVersion\":1}");
    }
    
    @Test
    void testRejectInvalidOptionalJsonFields() {
        assertDecodeRejected(addRootField("\"displayName\":null"));
        assertDecodeRejected(addRootField("\"iconUrl\":1"));
        assertDecodeRejected(addRootField("\"extensions\":null"));
        assertDecodeRejected(addRootField("\"extensions\":[]"));
        assertDecodeRejected(addRootField("\"provider\":null"));
        assertDecodeRejected(addRootField("\"provider\":[]"));
        assertDecodeRejected(addRootField("\"provider\":{}"));
        assertDecodeRejected(addRootField("\"provider\":{\"name\":1}"));
        assertDecodeRejected(addRootField(
            "\"provider\":{\"name\":\"Nacos\",\"url\":null}"));
        assertDecodeRejected(addRootField(
            "\"provider\":{\"name\":\"Nacos\",\"unknown\":true}"));
    }
    
    @Test
    void testRejectInvalidCatalogJsonShape() {
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":null}");
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":[]}");
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":{}}");
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":{\"onlineVersions\":null}}");
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":{\"onlineVersions\":{},"
                + "\"latestVersion\":\"1.0.0\"}}");
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":{\"onlineVersions\":[],"
                + "\"latestVersion\":null}}");
        assertDecodeRejected(
            "{\"schemaVersion\":1,\"versionCatalog\":{\"onlineVersions\":[],"
                + "\"unknown\":true}}");
        assertDecodeRejected(catalogEntryJson("null"));
        assertDecodeRejected(catalogEntryJson("{}"));
        assertDecodeRejected(catalogEntryJson(
            "{\"version\":1,\"labels\":[],\"protocols\":[\"a2a\"]}"));
        assertDecodeRejected(catalogEntryJson(
            "{\"version\":\"1.0.0\",\"labels\":null,\"protocols\":[\"a2a\"]}"));
        assertDecodeRejected(catalogEntryJson(
            "{\"version\":\"1.0.0\",\"labels\":[1],\"protocols\":[\"a2a\"]}"));
        assertDecodeRejected(catalogEntryJson(
            "{\"version\":\"1.0.0\",\"labels\":[],\"protocols\":null}"));
        assertDecodeRejected(catalogEntryJson(
            "{\"version\":\"1.0.0\",\"labels\":[],\"protocols\":[1]}"));
        assertDecodeRejected(catalogEntryJson(
            "{\"version\":\"1.0.0\",\"labels\":[],\"protocols\":[\"a2a\"],"
                + "\"unknown\":true}"));
    }
    
    @Test
    void testDecodeAlsoEnforcesSemanticLimits() {
        assertDecodeRejected(MINIMAL_JSON.replace("\"onlineVersions\":[]",
            "\"latestVersion\":\"1.0.0\",\"onlineVersions\":[]"));
        assertDecodeRejected(addRootField(
            "\"displayName\":\"" + repeat("x", 129) + "\""));
        assertDecodeRejected(addRootField(
            "\"extensions\":{\"k\":\"" + repeat("a", 16377) + "\"}"));
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }
    
    private void assertEncodeRejected(AgentResourceExt resourceExt) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentResourceExtSerializer.serialize(resourceExt));
    }
    
    private void assertDecodeRejected(String json) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentResourceExtSerializer.deserialize(json));
    }
    
    private String addRootField(String field) {
        return MINIMAL_JSON.substring(0, 1) + field + "," + MINIMAL_JSON.substring(1);
    }
    
    private String catalogEntryJson(String entry) {
        return "{\"schemaVersion\":1,\"versionCatalog\":{\"latestVersion\":\"1.0.0\","
            + "\"onlineVersions\":[" + entry + "]}}";
    }
    
    private AgentResourceExt createMinimalResourceExt() {
        AgentResourceExt result = new AgentResourceExt();
        result.setSchemaVersion(AgentResourceExt.SCHEMA_VERSION);
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setOnlineVersions(new ArrayList<AgentVersionCatalogEntry>());
        result.setVersionCatalog(catalog);
        return result;
    }
    
    private AgentResourceExt createFullResourceExt() {
        AgentResourceExt result = createMinimalResourceExt();
        result.setDisplayName("Nacos Agent");
        result.setIconUrl("https://nacos.io/icon.png");
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        result.setProvider(provider);
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        extensions.put("example.com/enabled", true);
        extensions.put("example.com/modes", Arrays.asList("chat", "task"));
        result.setExtensions(extensions);
        
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setLatestVersion("2.0.0");
        catalog.setOnlineVersions(new ArrayList<AgentVersionCatalogEntry>(
            Arrays.asList(createCatalogEntry("2.0.0", Collections.singletonList("stable"),
                Arrays.asList("a2a", "grpc")),
                createCatalogEntry("1.0.0-RC1", Collections.singletonList("preview"),
                    Collections.singletonList("a2a")))));
        result.setVersionCatalog(catalog);
        return result;
    }
    
    private AgentVersionCatalogEntry createCatalogEntry(String version, List<String> labels,
        List<String> protocols) {
        AgentVersionCatalogEntry result = new AgentVersionCatalogEntry();
        result.setVersion(version);
        result.setLabels(new ArrayList<String>(labels));
        result.setProtocols(new ArrayList<String>(protocols));
        return result;
    }
    
    private void assertCatalogEquals(AgentVersionCatalog expected,
        AgentVersionCatalog actual) {
        assertEquals(expected.getLatestVersion(), actual.getLatestVersion());
        assertEquals(expected.getOnlineVersions().size(), actual.getOnlineVersions().size());
        for (int i = 0; i < expected.getOnlineVersions().size(); i++) {
            AgentVersionCatalogEntry expectedEntry = expected.getOnlineVersions().get(i);
            AgentVersionCatalogEntry actualEntry = actual.getOnlineVersions().get(i);
            assertEquals(expectedEntry.getVersion(), actualEntry.getVersion());
            assertEquals(expectedEntry.getLabels(), actualEntry.getLabels());
            assertEquals(expectedEntry.getProtocols(), actualEntry.getProtocols());
        }
    }
    
    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
