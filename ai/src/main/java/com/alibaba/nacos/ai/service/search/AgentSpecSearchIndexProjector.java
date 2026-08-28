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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the deterministic shared-search projection for one AgentSpec.
 *
 * @author Nacos
 */
public class AgentSpecSearchIndexProjector {
    
    public static final int PROJECTION_VERSION = 1;
    
    private static final int MAX_PUBLIC_CONTENT_CHARS = 12000;
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
        new TypeReference<List<String>>() {
        };
    
    private static final Set<String> PRIVATE_KEYS = Set.of("runtime", "credential",
        "credentials", "secret", "secrets", "token", "tokens", "password", "privatekey",
        "privatekeys", "apikey", "apikeys", "accesskey", "accesskeys", "clientsecret",
        "authorization", "authentication", "auth", "cookie", "environment", "env");
    
    private final AiResourceIndexProjectionBuilder projectionBuilder =
        new AiResourceIndexProjectionBuilder();
    
    /**
     * Project the latest online AgentSpec without indexing private runtime values.
     *
     * @param meta canonical AgentSpec metadata
     * @param version exact latest online version row
     * @param agentSpec exact version package
     * @return complete document, chunk, and facet projection
     */
    public AiResourceIndexProjection project(AiResource meta, AiResourceVersion version,
        AgentSpec agentSpec) {
        if (meta == null || version == null || agentSpec == null) {
            throw new IllegalArgumentException(
                "AgentSpec metadata, Version, and content must not be null");
        }
        Map<String, Object> manifest = parseManifest(agentSpec.getContent());
        List<Object> publicDependencies = collectPublicSections(manifest, "dependencies");
        List<Object> publicCapabilities = collectPublicSections(manifest, "capabilities");
        List<String> tags = parseTags(meta.getBizTags());
        List<String> capabilities = flattenValues(publicCapabilities);
        AiResourceSearchDocument document = buildDocument(meta, version, agentSpec, tags,
            capabilities, publicDependencies);
        List<AiResourceIndexEnhancementContent> contents = publicContents(publicDependencies,
            publicCapabilities);
        return projectionBuilder.build(document, contents,
            AiResourceSearchConstants.CHUNK_TYPE_AGENTSPEC_CONTENT);
    }
    
    private AiResourceSearchDocument buildDocument(AiResource meta, AiResourceVersion version,
        AgentSpec agentSpec, List<String> tags, List<String> capabilities,
        List<Object> publicDependencies) {
        String description = firstNotBlank(agentSpec.getDescription(), version.getDesc(),
            meta.getDesc());
        AiResourceSearchDocument result = new AiResourceSearchDocument();
        result.setNamespaceId(meta.getNamespaceId());
        result.setResourceType(Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC);
        result.setResourceName(meta.getName());
        result.setResourceVersion(version.getVersion());
        result.setDisplayName(meta.getName());
        result.setDescription(description);
        result.setTags(JacksonUtils.toJson(tags));
        result.setCapabilities(JacksonUtils.toJson(capabilities));
        result.setRepresentativeQueries(JacksonUtils.toJson(representativeQueries(meta.getName(),
            description, tags, capabilities, publicDependencies)));
        result.setMetadata(JacksonUtils.toJson(metadata(meta, version, publicDependencies)));
        result.setSourceDigest(sourceDigest(meta, version, agentSpec, publicDependencies,
            capabilities));
        result.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        result.setGenerateMode(AiResourceSearchConstants.GENERATE_MODE_AUTO);
        result.setGmtModified(version.getGmtModified() == null ? meta.getGmtModified()
            : version.getGmtModified());
        return result;
    }
    
    private Map<String, Object> metadata(AiResource meta, AiResourceVersion version,
        List<Object> publicDependencies) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespaceId", meta.getNamespaceId());
        result.put("resourceType", Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC);
        result.put("resourceName", meta.getName());
        result.put("resourceVersion", version.getVersion());
        putIfPresent(result, "scope", AiResourceManager.resolveScope(meta));
        putIfPresent(result, "owner", meta.getOwner());
        if (!publicDependencies.isEmpty()) {
            result.put("publicDependencies", publicDependencies);
        }
        result.put("projectionVersion", PROJECTION_VERSION);
        return result;
    }
    
    private List<AiResourceIndexEnhancementContent> publicContents(
        List<Object> publicDependencies, List<Object> publicCapabilities) {
        if (publicDependencies.isEmpty() && publicCapabilities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> content = new LinkedHashMap<>();
        if (!publicDependencies.isEmpty()) {
            content.put("dependencies", publicDependencies);
        }
        if (!publicCapabilities.isEmpty()) {
            content.put("capabilities", publicCapabilities);
        }
        String text = JsonUtils.toCanonicalJson(content);
        if (text.length() > MAX_PUBLIC_CONTENT_CHARS) {
            text = text.substring(0, MAX_PUBLIC_CONTENT_CHARS);
        }
        return Collections.singletonList(
            new AiResourceIndexEnhancementContent("manifest-public.json", text));
    }
    
    private List<String> representativeQueries(String name, String description,
        List<String> tags, List<String> capabilities, List<Object> publicDependencies) {
        Set<String> result = new LinkedHashSet<>();
        addIfNotBlank(result, name);
        addIfNotBlank(result, description);
        addAll(result, tags);
        addAll(result, capabilities);
        addAll(result, flattenValues(publicDependencies));
        return new ArrayList<>(result);
    }
    
    private Map<String, Object> parseManifest(String content) {
        if (StringUtils.isBlank(content)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> result = JacksonUtils.toObj(content, MAP_TYPE);
            return result == null ? Collections.emptyMap() : result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
    
    private List<Object> collectPublicSections(Object node, String sectionName) {
        List<Object> result = new ArrayList<>();
        collectPublicSections(node, sectionName, result);
        return result;
    }
    
    private void collectPublicSections(Object node, String sectionName, List<Object> target) {
        if (node instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) node;
            if (isPrivate(source)) {
                return;
            }
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (isPrivateKey(key)) {
                    continue;
                }
                if (sectionName.equalsIgnoreCase(key)) {
                    Object sanitized = sanitizePublicValue(entry.getValue());
                    if (sanitized instanceof Collection) {
                        target.addAll((Collection<?>) sanitized);
                    } else if (sanitized != null) {
                        target.add(sanitized);
                    }
                } else {
                    collectPublicSections(entry.getValue(), sectionName, target);
                }
            }
        } else if (node instanceof Collection) {
            for (Object each : (Collection<?>) node) {
                collectPublicSections(each, sectionName, target);
            }
        }
    }
    
    private Object sanitizePublicValue(Object value) {
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            if (isPrivate(source)) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (isPrivateKey(key)) {
                    continue;
                }
                Object sanitized = sanitizePublicValue(entry.getValue());
                if (sanitized != null) {
                    result.put(key, sanitized);
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (value instanceof Collection) {
            List<Object> result = new ArrayList<>();
            for (Object each : (Collection<?>) value) {
                Object sanitized = sanitizePublicValue(each);
                if (sanitized != null) {
                    result.add(sanitized);
                }
            }
            return result;
        }
        return value;
    }
    
    private boolean isPrivate(Map<?, ?> value) {
        return Boolean.TRUE.equals(value.get("private"))
            || Boolean.FALSE.equals(value.get("public"))
            || isPrivateMarker(value.get("visibility")) || isPrivateMarker(value.get("scope"));
    }
    
    private boolean isPrivateMarker(Object value) {
        return value != null && "private".equalsIgnoreCase(String.valueOf(value));
    }
    
    private boolean isPrivateKey(String key) {
        String normalized = key == null ? "" : key.replace("-", "").replace("_", "")
            .toLowerCase(Locale.ROOT);
        if (PRIVATE_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("secret") || normalized.contains("credential")
            || normalized.contains("password") || normalized.endsWith("token")
            || normalized.contains("privatekey") || normalized.contains("apikey");
    }
    
    private List<String> flattenValues(Object value) {
        Set<String> result = new LinkedHashSet<>();
        flattenValues(value, result);
        return new ArrayList<>(result);
    }
    
    private void flattenValues(Object value, Set<String> target) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                addIfNotBlank(target, String.valueOf(entry.getKey()));
                flattenValues(entry.getValue(), target);
            }
        } else if (value instanceof Collection) {
            for (Object each : (Collection<?>) value) {
                flattenValues(each, target);
            }
        } else if (value instanceof String || value instanceof Number) {
            addIfNotBlank(target, String.valueOf(value));
        }
    }
    
    private List<String> parseTags(String tags) {
        if (StringUtils.isBlank(tags)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = JacksonUtils.toObj(tags, STRING_LIST_TYPE);
            if (parsed == null) {
                return Collections.emptyList();
            }
            Set<String> result = new LinkedHashSet<>();
            addAll(result, parsed);
            return new ArrayList<>(result);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
    
    private String sourceDigest(AiResource meta, AiResourceVersion version, AgentSpec agentSpec,
        List<Object> publicDependencies, List<String> publicCapabilities) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", meta.getStatus());
        source.put("description", meta.getDesc());
        source.put("bizTags", meta.getBizTags());
        source.put("scope", AiResourceManager.resolveScope(meta));
        source.put("owner", meta.getOwner());
        source.put("version", version.getVersion());
        source.put("versionStatus", version.getStatus());
        source.put("manifestName", agentSpec.getName());
        source.put("manifestDescription", agentSpec.getDescription());
        source.put("publicDependencies", publicDependencies);
        source.put("publicCapabilities", publicCapabilities);
        source.put("projectionVersion", PROJECTION_VERSION);
        return sha256(JsonUtils.toCanonicalJson(source));
    }
    
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte each : digest) {
                result.append(String.format("%02x", each & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
    
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
    
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String)
            || StringUtils.isNotBlank((String) value))) {
            target.put(key, value);
        }
    }
    
    private void addAll(Set<String> target, Collection<String> values) {
        if (values != null) {
            for (String value : values) {
                addIfNotBlank(target, value);
            }
        }
    }
    
    private void addIfNotBlank(Set<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }
}
