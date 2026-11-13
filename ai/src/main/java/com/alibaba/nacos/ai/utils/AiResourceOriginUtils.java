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
 *
 */

package com.alibaba.nacos.ai.utils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strict common provenance stored with a newly transferred Resource, never an authorization grant. */
public final class AiResourceOriginUtils {
    
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList("schemaVersion",
        "sourceNamespaceId", "sourceResourceId", "sourceName", "sourceVersionId",
        "sourceVersion", "hubEntryId", "importedAt"));
    
    private AiResourceOriginUtils() {
    }
    
    /** Validate optional persisted provenance and return a defensive copy. */
    public static Map<String, Object> copy(Map<?, ?> origin) {
        if (origin == null) {
            return null;
        }
        for (Object field : origin.keySet()) {
            if (!(field instanceof String) || !FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown resource origin field");
            }
        }
        if (!Long.valueOf(1L).equals(integer(origin.get("schemaVersion")))) {
            throw new IllegalArgumentException("Invalid resource origin schemaVersion");
        }
        text(origin, "sourceNamespaceId", 128);
        text(origin, "sourceName", 256);
        text(origin, "sourceVersion", 64);
        if (HubNamespaceUtils.isHubNamespace((String) origin.get("sourceNamespaceId"))
            || "latest".equalsIgnoreCase((String) origin.get("sourceVersion"))) {
            throw new IllegalArgumentException(
                "Origin must identify an ordinary exact source version");
        }
        positive(origin, "sourceResourceId");
        positive(origin, "sourceVersionId");
        if (origin.get("hubEntryId") != null) {
            positive(origin, "hubEntryId");
        }
        text(origin, "importedAt", 64);
        try {
            Instant.parse((String) origin.get("importedAt"));
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Invalid resource origin importedAt", invalid);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : origin.entrySet()) {
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    private static void text(Map<?, ?> origin, String field, int maximum) {
        Object value = origin.get(field);
        if (!(value instanceof String) || ((String) value).isBlank()
            || ((String) value).length() > maximum) {
            throw new IllegalArgumentException("Invalid resource origin " + field);
        }
    }
    
    private static void positive(Map<?, ?> origin, String field) {
        Long number = integer(origin.get(field));
        if (number == null || number <= 0) {
            throw new IllegalArgumentException("Invalid resource origin " + field);
        }
    }
    
    private static Long integer(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
