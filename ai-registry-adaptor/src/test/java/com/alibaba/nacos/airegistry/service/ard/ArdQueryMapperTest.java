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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.airegistry.model.ard.ArdSearchQuery;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Standard and legacy filter groups preserve OR/AND and effective IRI identity.
 *
 * @author nacos
 */
class ArdQueryMapperTest {
    
    private static final String NS = "https://agenticresourcediscovery.org/ns#";
    private final ArdQueryMapper mapper = new ArdQueryMapper();
    
    @Test
    void shouldKeepAliasGroupsIndependentAndMergeOnlyIdenticalLegacyGroups() throws Exception {
        ArdSearchQuery query = query("{\"@context\":{\"t\":\"" + NS + "tags\"},"
            + "\"filter\":{\"tags\":[\"a\"],\"t\":[\"b\"]},"
            + "\"filters\":[{\"fieldPath\":\"tags\",\"values\":[\"c\"]}]}");
        var groups = mapper.map(query);
        assertEquals(2, groups.size());
        assertEquals("tags", groups.get(0).field());
        assertEquals(List.of("a", "c"), groups.get(0).values());
        assertEquals("tags", groups.get(1).field());
        assertEquals(List.of("b"), groups.get(1).values());
        assertEquals(List.of("a"), query.getFilter().get("tags"));
    }
    
    @Test
    void shouldNotMergeReboundStandardSpellingWithLegacyField() throws Exception {
        var groups = mapper.map(query("{\"@context\":{\"tags\":\"https://other.example/tags\"},"
            + "\"filter\":{\"tags\":[\"a\"]},\"filters\":[{\"fieldPath\":\"tags\",\"values\":[\"b\"]}]}"));
        assertEquals(2, groups.size());
        assertNotEquals("tags", groups.get(0).field());
        assertEquals("tags", groups.get(1).field());
    }
    
    @Test
    void shouldPreserveLegacyConversionsValuesPrecedenceAndNullEntries() throws Exception {
        var groups = mapper.map(query("{\"filters\":[null,{\"fieldPath\":\"tags\","
            + "\"value\":\"ignored\",\"values\":[1,true,{},[\"x\"],null]},"
            + "{\"fieldPath\":\"tags\",\"value\":\"last\"}]}"));
        assertEquals(List.of("1", "true", "{}", "[x]", "last"), groups.get(0).values());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"null", "1", "true", "{}", "[1]", "[true]", "[{}]", "[[]]", "[null]",
        "[\"a\",null]"})
    void shouldRejectNonStringStandardValues(String value) {
        ArdSearchQuery query = query("{\"filter\":{\"tags\":" + value + "}}");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(query));
    }
    
    @Test
    void shouldResolveOpaqueRootsAndPreserveLiteralMemberCase() throws Exception {
        var groups = mapper.map(query("{\"@context\":{\"m\":\"" + NS + "metadata\","
            + "\"d\":\"" + NS + "data\",\"t\":\"" + NS + "trustManifest\"},"
            + "\"filter\":{\"m.resourceType\":\"skill\",\"d.Member\":\"value\",\"t.attestations.type\":\"SOC2\"}}"));
        assertEquals(
            List.of("metadata.resourceType", "data.Member", "trustManifest.attestations.type"),
            groups.stream().map(ArdQueryMapper.Filter::field).toList());
    }
    
    @Test
    void shouldPrioritizeDefinedTermsAndFullIrisBeforePathSplitting() throws Exception {
        var groups = mapper.map(query("{\"@context\":{\"a.b\":\"" + NS + "tags\"},"
            + "\"filter\":{\"a.b\":\"a\",\"" + NS + "mediaType\":\"skill\","
            + "\"https://Other.Example/a.b#Term\":\"value\"}}"));
        assertEquals("tags", groups.get(0).field());
        assertEquals("type", groups.get(1).field());
        assertNotEquals("type", groups.get(2).field());
        assertNotEquals(mapper.map(query("{\"filter\":{\"" + NS + "Tags\":\"a\"}}")).get(0).field(),
            "tags");
        assertNotEquals(mapper.map(query("{\"filter\":{\"" + NS + "type\":\"a\"}}")).get(0).field(),
            "type");
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"metadata..x", "metadata.", ".tags", "bad key", ""})
    void shouldRejectMalformedPaths(String field) {
        ArdSearchQuery query = query("{\"filter\":{" + JacksonUtils.toJson(field) + ":\"x\"}}");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(query));
    }
    
    @Test
    void shouldDistinguishAbsentContextFromExplicitNull() throws Exception {
        assertEquals("type", mapper.map(query("{\"filter\":{\"type\":\"x\"}}")).get(0).field());
        assertThrows(IllegalArgumentException.class,
            () -> mapper.map(query("{\"@context\":null,\"filter\":{\"type\":\"x\"}}")));
    }
    
    @Test
    void shouldBindContextWithBothJacksonGenerationsWithoutLeakingPresenceFlag() throws Exception {
        tools.jackson.databind.json.JsonMapper webMapper =
            tools.jackson.databind.json.JsonMapper.builder().build();
        for (String context : List.of("\"https://agenticresourcediscovery.org/context/v1\"",
            "{\"a\":\"" + NS + "\"}", "[null,{\"a\":\"" + NS + "\"}]")) {
            String json = "{\"@context\":" + context + ",\"filter\":{\"type\":\"x\"}}";
            assertEquals("type", mapper.map(query(json)).get(0).field());
            ArdSearchQuery webQuery = webMapper.readValue(json, ArdSearchQuery.class);
            assertEquals("type", mapper.map(webQuery).get(0).field());
            org.junit.jupiter.api.Assertions
                .assertFalse(webMapper.writeValueAsString(webQuery).contains("contextPresent"));
        }
        ArdSearchQuery explicitNull =
            webMapper.readValue("{\"@context\":null}", ArdSearchQuery.class);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(explicitNull));
        assertEquals(List.of(), mapper.map(webMapper.readValue("{}", ArdSearchQuery.class)));
    }
    
    private ArdSearchQuery query(String json) {
        return JacksonUtils.toObj(json, ArdSearchQuery.class);
    }
}
