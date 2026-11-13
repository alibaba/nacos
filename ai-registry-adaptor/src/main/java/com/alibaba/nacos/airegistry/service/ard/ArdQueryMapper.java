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

import com.alibaba.nacos.airegistry.model.ard.ArdSearchFilter;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchQuery;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps original-key OR groups separate even when aliases resolve to the same field.
 *
 * @author nacos
 */
final class ArdQueryMapper {
    
    private final ArdContextResolver contexts = new ArdContextResolver();
    
    ArdTermPathResolver resolver(ArdSearchQuery query) {
        JsonNode context = query.isContextPresent()
            ? JacksonUtils.toObj(JacksonUtils.toJson(query.getContext()), JsonNode.class) : null;
        return new ArdTermPathResolver(contexts.resolve(context));
    }
    
    List<Filter> map(ArdSearchQuery query) {
        return map(query, resolver(query));
    }
    
    List<Filter> map(ArdSearchQuery query, ArdTermPathResolver resolver) {
        Map<Group, List<String>> groups = new LinkedHashMap<>();
        if (query.getFilter() != null) {
            query.getFilter().forEach((original, value) -> add(groups, original,
                resolver.resolve(original), values(value, true)));
        }
        if (query.getFilters() != null) {
            for (ArdSearchFilter filter : query.getFilters()) {
                if (filter == null) {
                    continue;
                }
                String original = filter.getFieldPath();
                ArdTermPathResolver.validateLiteral(original);
                Object value = filter.getValues() == null ? filter.getValue() : filter.getValues();
                add(groups, original, original, values(value, false));
            }
        }
        List<Filter> result = new ArrayList<>();
        groups.forEach((key, value) -> result
            .add(new Filter(key.original(), key.field(), List.copyOf(value))));
        return result;
    }
    
    static List<Filter> legacy(Map<String, List<String>> filters) {
        List<Filter> result = new ArrayList<>();
        filters.forEach((key, value) -> result.add(new Filter(key, key, value)));
        return result;
    }
    
    private void add(Map<Group, List<String>> groups, String original, String field,
        List<String> values) {
        groups.computeIfAbsent(new Group(original, field), key -> new ArrayList<>()).addAll(values);
    }
    
    private List<String> values(Object value, boolean strict) {
        if (value instanceof String) {
            return List.of((String) value);
        }
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                if (strict && !(item instanceof String)) {
                    throw invalidValue();
                }
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        throw invalidValue();
    }
    
    private IllegalArgumentException invalidValue() {
        return new IllegalArgumentException(
            "query.filter values must be strings or arrays of strings");
    }
    
    record Filter(String original, String field, List<String> values) {
    }
    
    private record Group(String original, String field) {
    }
}
