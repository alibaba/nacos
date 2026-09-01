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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.common.Constants;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Shared canonicalization and fingerprinting for Agent Discover and Watch.
 *
 * <p>The implementation deliberately uses only Java 8 and API model types. Server and client
 * therefore produce the same identity and fingerprint without depending on a selected JSON
 * adapter. Request set values are sorted and deduplicated, while result arrays retain their
 * contract-defined order.</p>
 *
 * @author Nacos
 */
public final class AgentDiscoveryCanonicalizer {
    
    /**
     * Algorithm identifier carried by RAD Watch fingerprint tokens.
     */
    public static final String ALGORITHM_ID = "sha256-canonical-json-v1";
    
    private static final String SHA_256 = "SHA-256";
    
    private AgentDiscoveryCanonicalizer() {
    }
    
    /**
     * Return an isolated, validated Discover request with an effective namespace and normalized
     * set-valued filters.
     *
     * @param source caller-owned request
     * @return canonical request copy
     * @throws IllegalArgumentException when the request is invalid
     */
    public static AgentDiscoveryRequest canonicalizeRequest(AgentDiscoveryRequest source) {
        if (source == null) {
            throw new IllegalArgumentException("AgentDiscoveryRequest must not be null");
        }
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId(defaultNamespace(source.getNamespaceId()));
        result.setReference(copyReference(source.getReference()));
        result.setFilter(canonicalizeFilter(source.getFilter()));
        RadModelValidator.validate(result);
        return result;
    }
    
    /**
     * Return a readable, collision-safe canonical key for one Discover projection.
     *
     * <p>Caller-owned listener identity is intentionally excluded and may be composed by the
     * client cache. Authorization and visibility are decisions and are not resource identity.</p>
     *
     * @param source Discover request
     * @return canonical JSON projection key
     * @throws IllegalArgumentException when the request is invalid
     */
    public static String canonicalRequestKey(AgentDiscoveryRequest source) {
        AgentDiscoveryRequest request = canonicalizeRequest(source);
        return toCanonicalJson(requestFrame(request));
    }
    
    /**
     * Return an isolated, validated complete discovery snapshot with all Endpoint defaults
     * materialized and all JSON object keys canonicalized.
     *
     * @param source caller-owned complete snapshot
     * @return canonical snapshot copy
     * @throws IllegalArgumentException when the snapshot is invalid
     */
    public static AgentDiscoveryResult canonicalizeResult(AgentDiscoveryResult source) {
        if (source == null) {
            throw new IllegalArgumentException("AgentDiscoveryResult must not be null");
        }
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId(defaultNamespace(source.getNamespaceId()));
        result.setAgentName(source.getAgentName());
        result.setVersion(source.getVersion());
        result.setContentDigest(source.getContentDigest());
        result.setCallInterfaces(copyCallInterfaces(source.getCallInterfaces()));
        RadModelValidator.validate(result);
        return result;
    }
    
    /**
     * Calculate the equality-only fingerprint of a complete public discovery snapshot.
     *
     * @param source complete discovery snapshot
     * @return algorithm identifier and lowercase SHA-256 digest
     * @throws IllegalArgumentException when the snapshot is invalid
     */
    public static String fingerprint(AgentDiscoveryResult source) {
        String canonicalJson = canonicalResultJson(source);
        return ALGORITHM_ID + ':' + digestHex(SHA_256, canonicalJson);
    }
    
    static String canonicalResultJson(AgentDiscoveryResult source) {
        return toCanonicalJson(resultFrame(canonicalizeResult(source)));
    }
    
    private static String defaultNamespace(String namespaceId) {
        return namespaceId == null || namespaceId.isEmpty()
            ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    private static AgentReference copyReference(AgentReference source) {
        if (source == null) {
            return null;
        }
        AgentReference result = new AgentReference();
        result.setAgentName(source.getAgentName());
        result.setVersion(source.getVersion());
        result.setLabel(source.getLabel());
        return result;
    }
    
    private static AgentDiscoveryFilter canonicalizeFilter(AgentDiscoveryFilter source) {
        if (source == null) {
            return null;
        }
        AgentDiscoveryFilter result = new AgentDiscoveryFilter();
        result.setProtocols(normalizeStrings(source.getProtocols()));
        result.setProtocolVersion(source.getProtocolVersion());
        result.setTransports(normalizeStrings(source.getTransports()));
        result.setEndpointSources(normalizeSources(source.getEndpointSources()));
        result.setMetadataSelector(normalizeMetadata(source.getMetadataSelector()));
        return isEmpty(result) ? null : result;
    }
    
    private static List<String> normalizeStrings(List<String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String each : source) {
            if (each == null) {
                throw new IllegalArgumentException("Discover filter items must not be null");
            }
        }
        Set<String> unique = new LinkedHashSet<String>(source);
        List<String> result = new ArrayList<String>(unique);
        Collections.sort(result);
        return result;
    }
    
    private static List<EndpointSource> normalizeSources(List<EndpointSource> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (EndpointSource each : source) {
            if (each == null) {
                throw new IllegalArgumentException(
                    "Discover endpointSources items must not be null");
            }
        }
        Set<EndpointSource> unique = new LinkedHashSet<EndpointSource>(source);
        List<EndpointSource> result = new ArrayList<EndpointSource>(unique);
        Collections.sort(result, new Comparator<EndpointSource>() {
            
            @Override
            public int compare(EndpointSource left, EndpointSource right) {
                return left.name().compareTo(right.name());
            }
        });
        return result;
    }
    
    private static Map<String, String> normalizeMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        AgentValidationUtils.validateEndpointMetadata(source);
        return new LinkedHashMap<String, String>(new TreeMap<String, String>(source));
    }
    
    private static boolean isEmpty(AgentDiscoveryFilter filter) {
        return filter.getProtocols() == null && filter.getProtocolVersion() == null
            && filter.getTransports() == null && filter.getEndpointSources() == null
            && filter.getMetadataSelector() == null;
    }
    
    private static List<AgentDiscoveryCallInterface> copyCallInterfaces(
        List<AgentDiscoveryCallInterface> source) {
        if (source == null) {
            return null;
        }
        List<AgentDiscoveryCallInterface> result =
            new ArrayList<AgentDiscoveryCallInterface>(source.size());
        for (AgentDiscoveryCallInterface each : source) {
            if (each == null) {
                result.add(null);
                continue;
            }
            AgentDiscoveryCallInterface copy = new AgentDiscoveryCallInterface();
            copy.setProtocol(each.getProtocol());
            copy.setProtocolVersion(each.getProtocolVersion());
            copy.setDescriptorMediaType(each.getDescriptorMediaType());
            copy.setNativeDescriptor(copyJsonValue(each.getNativeDescriptor()));
            copy.setEndpointSets(copyEndpointSets(each.getEndpointSets()));
            result.add(copy);
        }
        return result;
    }
    
    private static List<EndpointSet> copyEndpointSets(List<EndpointSet> source) {
        if (source == null) {
            return null;
        }
        List<EndpointSet> result = new ArrayList<EndpointSet>(source.size());
        for (EndpointSet each : source) {
            if (each == null) {
                result.add(null);
                continue;
            }
            EndpointSet copy = new EndpointSet();
            copy.setSource(each.getSource());
            copy.setSourceRevision(each.getSourceRevision());
            copy.setEndpoints(copyEndpoints(each.getEndpoints()));
            result.add(copy);
        }
        return result;
    }
    
    private static List<AgentDiscoveryEndpoint> copyEndpoints(
        List<AgentDiscoveryEndpoint> source) {
        if (source == null) {
            return null;
        }
        List<AgentDiscoveryEndpoint> result =
            new ArrayList<AgentDiscoveryEndpoint>(source.size());
        for (AgentDiscoveryEndpoint each : source) {
            if (each == null) {
                result.add(null);
                continue;
            }
            Endpoint canonical = EndpointCanonicalizer.canonicalize(each);
            AgentDiscoveryEndpoint copy = new AgentDiscoveryEndpoint();
            copy.setUri(canonical.getUri());
            copy.setTransport(canonical.getTransport());
            copy.setPriority(canonical.getPriority());
            copy.setWeight(normalizeZero(canonical.getWeight()));
            copy.setMetadata(canonical.getMetadata());
            copy.setHealthy(canonical.getHealthy());
            copy.setBindings(copyBindings(each.getBindings()));
            result.add(copy);
        }
        return result;
    }
    
    private static Double normalizeZero(Double value) {
        return value != null && value.doubleValue() == 0D ? Double.valueOf(0D) : value;
    }
    
    private static List<RuntimeVersionBinding> copyBindings(List<RuntimeVersionBinding> source) {
        if (source == null) {
            return null;
        }
        List<RuntimeVersionBinding> result =
            new ArrayList<RuntimeVersionBinding>(source.size());
        for (RuntimeVersionBinding each : source) {
            if (each == null) {
                result.add(null);
                continue;
            }
            RuntimeVersionBinding copy = new RuntimeVersionBinding();
            copy.setRuntimeVersion(each.getRuntimeVersion());
            copy.setVersionRange(each.getVersionRange());
            result.add(copy);
        }
        return result;
    }
    
    private static Object copyJsonValue(Object source) {
        return copyJsonValue(source, new IdentityHashMap<Object, Boolean>());
    }
    
    private static Object copyJsonValue(Object source, IdentityHashMap<Object, Boolean> active) {
        if (source == null || source instanceof String || source instanceof Boolean
            || source instanceof Number) {
            validateJsonNumber(source);
            return source;
        }
        if (active.put(source, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("nativeDescriptor must not contain cycles");
        }
        try {
            if (source instanceof Map) {
                Map<?, ?> sourceMap = (Map<?, ?>) source;
                Map<String, Object> sorted = new TreeMap<String, Object>();
                for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException(
                            "nativeDescriptor object keys must be strings");
                    }
                    sorted.put((String) entry.getKey(), copyJsonValue(entry.getValue(), active));
                }
                return new LinkedHashMap<String, Object>(sorted);
            }
            if (source instanceof Iterable) {
                List<Object> result = new ArrayList<Object>();
                for (Object each : (Iterable<?>) source) {
                    result.add(copyJsonValue(each, active));
                }
                return result;
            }
            if (source.getClass().isArray()) {
                int length = Array.getLength(source);
                List<Object> result = new ArrayList<Object>(length);
                for (int i = 0; i < length; i++) {
                    result.add(copyJsonValue(Array.get(source, i), active));
                }
                return result;
            }
            throw new IllegalArgumentException(
                "nativeDescriptor contains a non-JSON value: " + source.getClass().getName());
        } finally {
            active.remove(source);
        }
    }
    
    private static void validateJsonNumber(Object source) {
        if (source instanceof Double) {
            double value = ((Double) source).doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("nativeDescriptor contains a non-finite number");
            }
        } else if (source instanceof Float) {
            float value = ((Float) source).floatValue();
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("nativeDescriptor contains a non-finite number");
            }
        }
    }
    
    private static Map<String, Object> requestFrame(AgentDiscoveryRequest request) {
        Map<String, Object> reference = new LinkedHashMap<String, Object>();
        reference.put("agentName", request.getReference().getAgentName());
        reference.put("label", request.getReference().getLabel());
        reference.put("version", request.getReference().getVersion());
        AgentDiscoveryFilter filter = request.getFilter();
        Map<String, Object> filterFrame = new LinkedHashMap<String, Object>();
        filterFrame.put("endpointSources", sourceNames(filter == null ? null
            : filter.getEndpointSources()));
        filterFrame.put("metadataSelector", emptyMap(filter == null ? null
            : filter.getMetadataSelector()));
        filterFrame.put("protocolVersion", filter == null ? null : filter.getProtocolVersion());
        filterFrame.put("protocols", emptyList(filter == null ? null : filter.getProtocols()));
        filterFrame.put("transports", emptyList(filter == null ? null : filter.getTransports()));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("filter", filterFrame);
        result.put("namespaceId", request.getNamespaceId());
        result.put("reference", reference);
        return result;
    }
    
    private static List<String> sourceNames(List<EndpointSource> sources) {
        if (sources == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>(sources.size());
        for (EndpointSource source : sources) {
            result.add(source.name());
        }
        return result;
    }
    
    private static List<?> emptyList(List<?> values) {
        return values == null ? Collections.emptyList() : values;
    }
    
    private static Map<?, ?> emptyMap(Map<?, ?> values) {
        return values == null ? Collections.emptyMap() : values;
    }
    
    private static Map<String, Object> resultFrame(AgentDiscoveryResult result) {
        Map<String, Object> frame = new LinkedHashMap<String, Object>();
        frame.put("agentName", result.getAgentName());
        frame.put("callInterfaces", callInterfaceFrames(result.getCallInterfaces()));
        frame.put("contentDigest", result.getContentDigest());
        frame.put("namespaceId", result.getNamespaceId());
        frame.put("version", result.getVersion());
        return frame;
    }
    
    private static List<Object> callInterfaceFrames(
        List<AgentDiscoveryCallInterface> callInterfaces) {
        List<Object> result = new ArrayList<Object>(callInterfaces.size());
        for (AgentDiscoveryCallInterface callInterface : callInterfaces) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("descriptorMediaType", callInterface.getDescriptorMediaType());
            frame.put("endpointSets", endpointSetFrames(callInterface.getEndpointSets()));
            frame.put("nativeDescriptor", callInterface.getNativeDescriptor());
            frame.put("protocol", callInterface.getProtocol());
            frame.put("protocolVersion", callInterface.getProtocolVersion());
            result.add(frame);
        }
        return result;
    }
    
    private static List<Object> endpointSetFrames(List<EndpointSet> endpointSets) {
        List<Object> result = new ArrayList<Object>(endpointSets.size());
        for (EndpointSet endpointSet : endpointSets) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("endpoints", endpointFrames(endpointSet.getEndpoints()));
            frame.put("source", endpointSet.getSource().name());
            frame.put("sourceRevision", endpointSet.getSourceRevision());
            result.add(frame);
        }
        return result;
    }
    
    private static List<Object> endpointFrames(List<AgentDiscoveryEndpoint> endpoints) {
        List<Object> result = new ArrayList<Object>(endpoints.size());
        for (AgentDiscoveryEndpoint endpoint : endpoints) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("bindings", bindingFrames(endpoint.getBindings()));
            frame.put("healthy", endpoint.getHealthy());
            frame.put("metadata", emptyMap(endpoint.getMetadata()));
            frame.put("priority", endpoint.getPriority());
            frame.put("transport", endpoint.getTransport());
            frame.put("uri", endpoint.getUri());
            frame.put("weight", endpoint.getWeight());
            result.add(frame);
        }
        return result;
    }
    
    private static List<Object> bindingFrames(List<RuntimeVersionBinding> bindings) {
        if (bindings == null) {
            return Collections.emptyList();
        }
        List<Object> result = new ArrayList<Object>(bindings.size());
        for (RuntimeVersionBinding binding : bindings) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("runtimeVersion", binding.getRuntimeVersion());
            frame.put("versionRange", binding.getVersionRange());
            result.add(frame);
        }
        return result;
    }
    
    static String toCanonicalJson(Object value) {
        StringBuilder result = new StringBuilder();
        appendJson(result, value);
        return result.toString();
    }
    
    private static void appendJson(StringBuilder target, Object value) {
        if (value == null) {
            target.append("null");
        } else if (value instanceof String) {
            appendString(target, (String) value);
        } else if (value instanceof Boolean) {
            target.append(value);
        } else if (value instanceof Number) {
            appendNumber(target, (Number) value);
        } else if (value instanceof Map) {
            appendMap(target, (Map<?, ?>) value);
        } else if (value instanceof Iterable) {
            appendIterable(target, (Iterable<?>) value);
        } else {
            throw new IllegalArgumentException(
                "Canonical JSON contains an unsupported value: " + value.getClass().getName());
        }
    }
    
    private static void appendMap(StringBuilder target, Map<?, ?> value) {
        Map<String, Object> sorted = new TreeMap<String, Object>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalArgumentException("Canonical JSON object keys must be strings");
            }
            sorted.put((String) entry.getKey(), entry.getValue());
        }
        target.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                target.append(',');
            }
            first = false;
            appendString(target, entry.getKey());
            target.append(':');
            appendJson(target, entry.getValue());
        }
        target.append('}');
    }
    
    private static void appendIterable(StringBuilder target, Iterable<?> value) {
        target.append('[');
        boolean first = true;
        for (Object each : value) {
            if (!first) {
                target.append(',');
            }
            first = false;
            appendJson(target, each);
        }
        target.append(']');
    }
    
    private static void appendNumber(StringBuilder target, Number value) {
        validateJsonNumber(value);
        try {
            BigDecimal decimal = new BigDecimal(value.toString());
            if (decimal.signum() == 0) {
                target.append('0');
            } else {
                target.append(decimal.stripTrailingZeros().toPlainString());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid JSON number: " + value, e);
        }
    }
    
    private static void appendString(StringBuilder target, String value) {
        target.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"':
                    target.append("\\\"");
                    break;
                case '\\':
                    target.append("\\\\");
                    break;
                case '\b':
                    target.append("\\b");
                    break;
                case '\f':
                    target.append("\\f");
                    break;
                case '\n':
                    target.append("\\n");
                    break;
                case '\r':
                    target.append("\\r");
                    break;
                case '\t':
                    target.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        appendUnicodeEscape(target, current);
                    } else if (Character.isHighSurrogate(current)) {
                        if (i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1))) {
                            target.append(current).append(value.charAt(++i));
                        } else {
                            appendUnicodeEscape(target, current);
                        }
                    } else if (Character.isLowSurrogate(current)) {
                        appendUnicodeEscape(target, current);
                    } else {
                        target.append(current);
                    }
                    break;
            }
        }
        target.append('"');
    }
    
    private static void appendUnicodeEscape(StringBuilder target, char value) {
        target.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            target.append(Character.forDigit(value >> shift & 0xF, 16));
        }
    }
    
    static String digestHex(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte each : bytes) {
                result.append(Character.forDigit(each >> 4 & 0xF, 16));
                result.append(Character.forDigit(each & 0xF, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        }
    }
}
