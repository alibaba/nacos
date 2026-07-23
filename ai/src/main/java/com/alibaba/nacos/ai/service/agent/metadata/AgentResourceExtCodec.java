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
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict JSON codec for the typed Agent content persisted in {@code ai_resource.ext}.
 *
 * @author Nacos
 */
public final class AgentResourceExtCodec {
    
    public static final int MAX_EXTENSIONS_SIZE = 16 * 1024;
    
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    
    private static final int MAX_URI_LENGTH = 2048;
    
    private static final int MAX_PROVIDER_NAME_LENGTH = 128;
    
    private static final int MAX_EXTENSIONS = 32;
    
    private static final int MAX_EXTENSION_KEY_LENGTH = 128;
    
    private static final JsonFactory STRICT_JSON_FACTORY = new JsonFactory()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    
    private static final Set<String> ROOT_FIELDS = unmodifiableSet("schemaVersion",
        "displayName", "iconUrl", "provider", "extensions", "versionCatalog");
    
    private static final Set<String> PROVIDER_FIELDS = unmodifiableSet("name", "url");
    
    private static final Set<String> CATALOG_FIELDS =
        unmodifiableSet("latestVersion", "onlineVersions");
    
    private static final Set<String> CATALOG_ENTRY_FIELDS =
        unmodifiableSet("version", "labels", "protocols");
    
    private AgentResourceExtCodec() {
    }
    
    /**
     * Validate and encode Agent resource extension data.
     *
     * @param resourceExt typed extension object
     * @return JSON persisted in {@code ai_resource.ext}
     */
    public static String encode(AgentResourceExt resourceExt) {
        validate(resourceExt);
        try {
            return JacksonUtils.toJson(toStorageProjection(resourceExt));
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize AgentResourceExt", e);
        }
    }
    
    /**
     * Decode and validate Agent resource extension data.
     *
     * @param json JSON read from {@code ai_resource.ext}
     * @return typed extension object
     */
    public static AgentResourceExt decode(String json) {
        validateJsonShape(json);
        final AgentResourceExt result;
        try {
            result = JacksonUtils.toObj(json, AgentResourceExt.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid AgentResourceExt", e);
        }
        validate(result);
        return result;
    }
    
    /**
     * Validate Agent resource extension data against schema version 1.
     *
     * @param resourceExt typed extension object
     */
    public static void validate(AgentResourceExt resourceExt) {
        if (resourceExt == null) {
            throw new IllegalArgumentException("AgentResourceExt must not be null");
        }
        if (!Integer.valueOf(AgentResourceExt.SCHEMA_VERSION)
            .equals(resourceExt.getSchemaVersion())) {
            throw new IllegalArgumentException("AgentResourceExt schemaVersion must be "
                + AgentResourceExt.SCHEMA_VERSION);
        }
        validateOptionalCodePointLength(resourceExt.getDisplayName(),
            MAX_DISPLAY_NAME_LENGTH, "displayName");
        validateOptionalAbsoluteUri(resourceExt.getIconUrl(), "iconUrl");
        validateProvider(resourceExt.getProvider());
        validateExtensions(resourceExt.getExtensions());
        validateCatalog(resourceExt.getVersionCatalog());
    }
    
    private static void validateProvider(AgentProvider provider) {
        if (provider == null) {
            return;
        }
        validateRequiredCodePointLength(provider.getName(), MAX_PROVIDER_NAME_LENGTH,
            "provider.name");
        validateOptionalAbsoluteUri(provider.getUrl(), "provider.url");
    }
    
    private static void validateExtensions(Map<String, Object> extensions) {
        if (extensions == null) {
            return;
        }
        if (extensions.size() > MAX_EXTENSIONS) {
            throw new IllegalArgumentException(
                "extensions exceeds " + MAX_EXTENSIONS + " entries");
        }
        for (Map.Entry<?, ?> entry : extensions.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalArgumentException(
                    "Agent extensions contains a non-string JSON object key");
            }
            String key = (String) entry.getKey();
            validateRequiredCodePointLength(key, MAX_EXTENSION_KEY_LENGTH, "extension key");
            validateJsonValue(entry.getValue(), "extension " + entry.getKey());
        }
        final byte[] bytes;
        try {
            bytes = JacksonUtils.toJsonBytes(extensions);
        } catch (NacosSerializationException e) {
            throw new IllegalArgumentException("Unable to serialize Agent extensions", e);
        }
        if (bytes.length > MAX_EXTENSIONS_SIZE) {
            throw new IllegalArgumentException(
                "Agent extensions exceeds " + MAX_EXTENSIONS_SIZE + " bytes");
        }
    }
    
    private static void validateJsonValue(Object value, String fieldName) {
        if (value == null || value instanceof String || value instanceof Boolean
            || value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long) {
            return;
        }
        if (value instanceof Float) {
            if (!Float.isFinite((Float) value)) {
                throw new IllegalArgumentException(fieldName + " must be a finite JSON number");
            }
            return;
        }
        if (value instanceof Double) {
            if (!Double.isFinite((Double) value)) {
                throw new IllegalArgumentException(fieldName + " must be a finite JSON number");
            }
            return;
        }
        if (value instanceof Number) {
            return;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                validateJsonValue(item, fieldName);
            }
            return;
        }
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException(
                        fieldName + " contains a non-string JSON object key");
                }
                validateJsonValue(entry.getValue(), fieldName);
            }
            return;
        }
        throw new IllegalArgumentException(fieldName + " is not a JSON value");
    }
    
    private static void validateCatalog(AgentVersionCatalog catalog) {
        AgentModelValidator.validateVersionCatalog(catalog);
        List<AgentVersionCatalogEntry> versions = catalog.getOnlineVersions();
        for (int i = 1; i < versions.size(); i++) {
            String previous = versions.get(i - 1).getVersion();
            String current = versions.get(i).getVersion();
            if (AgentVersionComparator.compare(previous, current) <= 0) {
                throw new IllegalArgumentException(
                    "Agent Version catalog must use descending Version order");
            }
        }
    }
    
    private static void validateOptionalAbsoluteUri(String value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.isEmpty() || value.codePointCount(0, value.length()) > MAX_URI_LENGTH) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException(fieldName + " must be an absolute URI");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid " + fieldName, e);
        }
    }
    
    private static void validateOptionalCodePointLength(String value, int maxLength,
        String fieldName) {
        if (value != null && value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxLength
                + " Unicode code points");
        }
    }
    
    private static void validateRequiredCodePointLength(String value, int maxLength,
        String fieldName) {
        if (value == null || value.isEmpty()
            || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
    }
    
    private static Map<String, Object> toStorageProjection(AgentResourceExt resourceExt) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", AgentResourceExt.SCHEMA_VERSION);
        putIfPresent(result, "displayName", resourceExt.getDisplayName());
        putIfPresent(result, "iconUrl", resourceExt.getIconUrl());
        if (resourceExt.getProvider() != null) {
            Map<String, Object> provider = new LinkedHashMap<String, Object>();
            provider.put("name", resourceExt.getProvider().getName());
            putIfPresent(provider, "url", resourceExt.getProvider().getUrl());
            result.put("provider", provider);
        }
        putIfPresent(result, "extensions", resourceExt.getExtensions());
        result.put("versionCatalog", toStorageProjection(resourceExt.getVersionCatalog()));
        return result;
    }
    
    private static Map<String, Object> toStorageProjection(AgentVersionCatalog catalog) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        putIfPresent(result, "latestVersion", catalog.getLatestVersion());
        List<Map<String, Object>> versions = new ArrayList<Map<String, Object>>();
        for (AgentVersionCatalogEntry entry : catalog.getOnlineVersions()) {
            Map<String, Object> version = new LinkedHashMap<String, Object>();
            version.put("version", entry.getVersion());
            version.put("labels", entry.getLabels());
            version.put("protocols", entry.getProtocols());
            versions.add(version);
        }
        result.put("onlineVersions", versions);
        return result;
    }
    
    private static void putIfPresent(Map<String, Object> target, String field, Object value) {
        if (value != null) {
            target.put(field, value);
        }
    }
    
    private static void validateJsonShape(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("AgentResourceExt JSON must not be empty");
        }
        validateSingleJsonValue(json);
        final Map<?, ?> root;
        try {
            root = JacksonUtils.toObj(json, Map.class);
        } catch (NacosDeserializationException e) {
            throw new IllegalArgumentException("Invalid AgentResourceExt", e);
        }
        if (root == null) {
            throw new IllegalArgumentException("AgentResourceExt must be a JSON object");
        }
        rejectUnknownFields(root, ROOT_FIELDS, "AgentResourceExt");
        validateJsonInteger(root, "schemaVersion");
        validateOptionalJsonText(root, "displayName");
        validateOptionalJsonText(root, "iconUrl");
        validateProviderShape(root);
        validateExtensionsShape(root);
        validateCatalogShape(root);
    }
    
    private static void validateProviderShape(Map<?, ?> root) {
        if (!root.containsKey("provider")) {
            return;
        }
        Map<?, ?> provider = requireJsonObject(root.get("provider"), "provider");
        rejectUnknownFields(provider, PROVIDER_FIELDS, "AgentProvider");
        validateRequiredJsonText(provider, "name");
        validateOptionalJsonText(provider, "url");
    }
    
    private static void validateExtensionsShape(Map<?, ?> root) {
        if (root.containsKey("extensions")) {
            requireJsonObject(root.get("extensions"), "extensions");
        }
    }
    
    private static void validateCatalogShape(Map<?, ?> root) {
        if (!root.containsKey("versionCatalog")) {
            throw new IllegalArgumentException("Missing AgentResourceExt field: versionCatalog");
        }
        Map<?, ?> catalog = requireJsonObject(root.get("versionCatalog"), "versionCatalog");
        rejectUnknownFields(catalog, CATALOG_FIELDS, "AgentVersionCatalog");
        validateOptionalJsonText(catalog, "latestVersion");
        Object versionsValue = catalog.get("onlineVersions");
        if (!(versionsValue instanceof List)) {
            throw new IllegalArgumentException(
                "AgentVersionCatalog onlineVersions must be an array");
        }
        for (Object entryValue : (List<?>) versionsValue) {
            Map<?, ?> entry = requireJsonObject(entryValue, "versionCatalog entry");
            rejectUnknownFields(entry, CATALOG_ENTRY_FIELDS, "AgentVersionCatalogEntry");
            validateRequiredJsonText(entry, "version");
            validateStringArray(entry, "labels");
            validateStringArray(entry, "protocols");
        }
    }
    
    private static void validateStringArray(Map<?, ?> object, String field) {
        Object value = object.get(field);
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(field + " must contain only strings");
            }
        }
    }
    
    private static Map<?, ?> requireJsonObject(Object value, String fieldName) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
        return (Map<?, ?>) value;
    }
    
    private static void validateRequiredJsonText(Map<?, ?> object, String field) {
        if (!(object.get(field) instanceof String)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
    }
    
    private static void validateOptionalJsonText(Map<?, ?> object, String field) {
        if (object.containsKey(field) && !(object.get(field) instanceof String)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
    }
    
    private static void validateJsonInteger(Map<?, ?> object, String field) {
        Object value = object.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
    }
    
    private static void rejectUnknownFields(Map<?, ?> object, Set<String> allowedFields,
        String objectName) {
        for (Object field : object.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException(
                    "Unknown " + objectName + " field: " + field);
            }
        }
    }
    
    private static void validateSingleJsonValue(String json) {
        try (JsonParser parser = STRICT_JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException("AgentResourceExt JSON must not be empty");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(
                    "AgentResourceExt must contain one JSON value");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid AgentResourceExt", e);
        }
    }
    
    private static Set<String> unmodifiableSet(String... fields) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(fields)));
    }
}
