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

package com.alibaba.nacos.airegistry.model.ard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract assertions derived from the pinned upstream ARD OpenAPI.
 *
 * @author nacos
 */
class ArdOpenApiContractTest {
    
    private static final String OPEN_API =
        "/ard-spec/5fa2f5aef790b478319f6a3b43adf4661b0ed0e0/ard.openapi.yaml";
    
    private static Map<String, Object> document;
    
    @BeforeAll
    static void loadOpenApi() {
        try (InputStream input = ArdOpenApiContractTest.class.getResourceAsStream(OPEN_API)) {
            assertNotNull(input);
            document = new Yaml().load(input);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load pinned ARD OpenAPI", e);
        }
    }
    
    @Test
    void shouldPinExpectedUpstreamVersionAndUnauthorizedContract() {
        assertEquals("0.5.0", map(document.get("info")).get("version"));
        Map<String, Object> responses = operation("/search", "post", "responses");
        assertEquals("#/components/responses/401Unauthorized",
            map(responses.get("401")).get("$ref"));
        
        Map<String, Object> error = schema("Error");
        assertEquals(List.of("errorCode", "message"), error.get("required"));
    }
    
    @Test
    void shouldPinSearchListAndExploreResponseShapes() {
        Map<String, Object> search = schema("SearchResponse");
        assertTrue(list(search.get("required")).contains("results"));
        Map<String, Object> searchItem = schema("SearchResultItem");
        assertTrue(list(searchItem.get("required")).containsAll(List.of("score", "source")));
        Map<String, Object> searchProperties = map(searchItem.get("properties"));
        assertEquals("integer", map(searchProperties.get("score")).get("type"));
        assertEquals("uri", map(searchProperties.get("source")).get("format"));
        
        Map<String, Object> list = schema("ListResponse");
        assertTrue(list(list.get("required")).contains("items"));
        
        Map<String, Object> explore = schema("ExploreResponse");
        assertTrue(list(explore.get("required")).containsAll(List.of("resultType", "facets")));
    }
    
    private Map<String, Object> operation(String path, String method, String field) {
        Map<String, Object> paths = map(document.get("paths"));
        Map<String, Object> operation = map(map(paths.get(path)).get(method));
        return map(operation.get(field));
    }
    
    private Map<String, Object> schema(String name) {
        Map<String, Object> components = map(document.get("components"));
        return map(map(components.get("schemas")).get(name));
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
    
    @SuppressWarnings("unchecked")
    private static List<String> list(Object value) {
        return (List<String>) value;
    }
}
