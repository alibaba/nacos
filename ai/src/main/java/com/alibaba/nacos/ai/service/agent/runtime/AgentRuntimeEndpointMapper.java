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

package com.alibaba.nacos.ai.service.agent.runtime;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.identity.RadAsciiAgentIdCodec;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointState;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.naming.pojo.Instance;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts Agent Runtime Endpoints to and from Naming instances.
 *
 * @author Nacos
 */
public final class AgentRuntimeEndpointMapper {
    
    private static final int MAX_HOST_LENGTH = 253;
    
    private static final int MAX_NAMING_METADATA_LENGTH = 1024;
    
    private static final Pattern URI_SCHEME_PATTERN =
        Pattern.compile("[a-z][a-z0-9+.-]*");
    
    private static final Set<String> KNOWN_RESERVED_KEYS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(Constants.Agent.AGENT_ENDPOINT_PATH_KEY,
            Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY,
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY,
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY,
            Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY,
            Constants.Agent.AGENT_ENDPOINT_QUERY_KEY,
            Constants.Agent.AGENT_ENDPOINT_TENANT_KEY,
            Constants.Agent.AGENT_ENDPOINT_VERSION_KEY,
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY,
            Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY)));
    
    private AgentRuntimeEndpointMapper() {
    }
    
    /**
     * Convert one public Runtime Endpoint into an ephemeral Naming instance.
     *
     * @param endpoint public Endpoint
     * @param runtimeVersion deployed runtime Version
     * @param versionRange canonical compatible Agent Version range
     * @return Naming instance without a service name
     */
    public static Instance toInstance(Endpoint endpoint, String runtimeVersion,
        String versionRange) {
        Endpoint canonical = canonicalPayload(endpoint);
        String canonicalRange = canonicalVersionRange(runtimeVersion, versionRange);
        URI uri = parseCanonicalUri(canonical.getUri());
        
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put(Constants.Agent.AGENT_ENDPOINT_PATH_KEY, valueOrEmpty(uri.getRawPath()));
        metadata.put(Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY, canonical.getTransport());
        metadata.put(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY, uri.getScheme());
        metadata.put(Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY,
            Boolean.toString(isTlsScheme(uri.getScheme())));
        if (uri.getRawQuery() != null) {
            metadata.put(Constants.Agent.AGENT_ENDPOINT_QUERY_KEY, uri.getRawQuery());
        }
        metadata.put(Constants.Agent.AGENT_ENDPOINT_VERSION_KEY, runtimeVersion);
        metadata.put(Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY, canonicalRange);
        metadata.put(Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY,
            Integer.toString(canonical.getPriority()));
        if (canonical.getMetadata() != null) {
            validateNamingControlKeys(canonical.getMetadata());
            metadata.putAll(canonical.getMetadata());
        }
        validateCompleteMetadata(metadata);
        
        String host = EndpointCanonicalizer.normalizedHost(canonical.getUri());
        validateHost(host);
        Instance result = new Instance();
        result.setIp(host);
        result.setPort(EndpointCanonicalizer.effectivePort(canonical.getUri()));
        result.setClusterName(RadAsciiAgentIdCodec.encode(canonical.getTransport()));
        result.setWeight(canonical.getWeight());
        result.setEnabled(true);
        result.setHealthy(true);
        result.setEphemeral(true);
        result.setMetadata(metadata);
        return result;
    }
    
    /**
     * Convert one legacy A2A Endpoint into the canonical Runtime Naming layout.
     *
     * <p>The legacy protocol version and tenant remain compatibility-only reserved metadata.
     * They are intentionally excluded from the public RAD Endpoint metadata and revision.</p>
     *
     * @param endpoint public Endpoint converted from the legacy A2A model
     * @param runtimeVersion exact legacy Agent Version
     * @param protocolVersion optional legacy A2A protocol version
     * @param tenant optional legacy A2A tenant
     * @return Naming instance in the canonical Runtime layout
     */
    public static Instance toLegacyA2aInstance(Endpoint endpoint, String runtimeVersion,
        String protocolVersion, String tenant) {
        Instance result = toInstance(endpoint, runtimeVersion, null);
        if (protocolVersion != null && !protocolVersion.isEmpty()) {
            result.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY,
                protocolVersion);
        }
        if (tenant != null && !tenant.isEmpty()) {
            result.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY, tenant);
        }
        validateLegacyMetadata(result.getMetadata());
        validateCompleteMetadata(result.getMetadata());
        return result;
    }
    
    /**
     * Test whether one canonical Runtime Naming instance supports an exact Agent Version.
     *
     * @param instance canonical Runtime Naming instance
     * @param version exact Agent Version
     * @return {@code true} when any persisted binding contains the Version
     */
    public static boolean supportsVersion(Instance instance, String version) {
        RuntimeEndpointSnapshotItem item = fromInstance(instance, 0L);
        for (RuntimeVersionBinding binding : item.getBindings()) {
            if (RuntimeVersionRangeSupport.contains(binding.getVersionRange(), version)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Convert one Naming ServiceStorage instance into a Runtime Endpoint contribution.
     *
     * @param instance Naming instance with operational metadata already applied
     * @param lastUpdatedTime Naming ServiceInfo observation time
     * @return validated single-contribution management item
     */
    public static RuntimeEndpointSnapshotItem fromInstance(Instance instance,
        long lastUpdatedTime) {
        if (instance == null) {
            throw new IllegalArgumentException("Naming instance must not be null");
        }
        validateHost(instance.getIp());
        if (instance.getPort() < 1 || instance.getPort() > 65535) {
            throw new IllegalArgumentException(
                "Invalid Naming instance port: " + instance.getPort());
        }
        Map<String, String> metadata = stringMetadata(instance.getMetadata());
        validateCompleteMetadata(metadata);
        validateReservedKeys(metadata);
        
        String transport = requiredMetadata(metadata,
            Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY);
        AgentValidationUtils.validateTransport(transport);
        if (!instance.getClusterName().equals(RadAsciiAgentIdCodec.encode(transport))) {
            throw new IllegalArgumentException(
                "Naming cluster must match the encoded Agent Endpoint transport");
        }
        String uriScheme = requiredMetadata(metadata,
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY);
        validateUriScheme(uriScheme);
        String supportTls = requiredMetadata(metadata,
            Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY);
        if (!"true".equals(supportTls) && !"false".equals(supportTls)) {
            throw new IllegalArgumentException("Invalid Agent Endpoint supportTls: " + supportTls);
        }
        if (Boolean.parseBoolean(supportTls) != isTlsScheme(uriScheme)) {
            throw new IllegalArgumentException(
                "Agent Endpoint URI scheme and supportTls must agree");
        }
        
        String path = requiredMetadata(metadata, Constants.Agent.AGENT_ENDPOINT_PATH_KEY);
        String query = metadata.get(Constants.Agent.AGENT_ENDPOINT_QUERY_KEY);
        int priority = parsePriority(requiredMetadata(metadata,
            Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY));
        String runtimeVersion = requiredMetadata(metadata,
            Constants.Agent.AGENT_ENDPOINT_VERSION_KEY);
        String persistedRange = requiredMetadata(metadata,
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY);
        String versionRange = canonicalVersionRange(runtimeVersion, persistedRange);
        if (!persistedRange.equals(versionRange)) {
            throw new IllegalArgumentException(
                "Agent Endpoint Version range must be canonical");
        }
        validateLegacyMetadata(metadata);
        
        String uri = composeUri(uriScheme, instance.getIp(), instance.getPort(), path, query);
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport(transport);
        endpoint.setPriority(priority);
        endpoint.setWeight(instance.getWeight());
        Map<String, String> publicMetadata = publicMetadata(metadata);
        if (!publicMetadata.isEmpty()) {
            endpoint.setMetadata(publicMetadata);
        }
        Endpoint canonical = canonicalPayload(endpoint);
        if (!uri.equals(canonical.getUri())) {
            throw new IllegalArgumentException(
                "Naming instance does not contain a canonical Agent Endpoint URI");
        }
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion(runtimeVersion);
        binding.setVersionRange(versionRange);
        RuntimeEndpointSnapshotItem result = new RuntimeEndpointSnapshotItem();
        result.setEndpoint(canonical);
        result.setBindings(new ArrayList<RuntimeVersionBinding>(
            Collections.singletonList(binding)));
        result.setEnabled(instance.isEnabled());
        result.setHealthy(instance.isHealthy());
        result.setState(runtimeState(instance.isEnabled(), instance.isHealthy()));
        result.setLastUpdatedTime(lastUpdatedTime);
        return result;
    }
    
    private static Endpoint canonicalPayload(Endpoint endpoint) {
        Endpoint canonical = EndpointCanonicalizer.canonicalize(endpoint);
        canonical.setHealthy(null);
        return canonical;
    }
    
    private static String canonicalVersionRange(String runtimeVersion, String versionRange) {
        AgentValidationUtils.validateVersion(runtimeVersion);
        String result = versionRange == null
            ? RuntimeVersionRangeSupport.exact(runtimeVersion)
            : RuntimeVersionRangeSupport.canonicalize(versionRange);
        if (!RuntimeVersionRangeSupport.contains(result, runtimeVersion)) {
            throw new IllegalArgumentException("versionRange must contain runtimeVersion");
        }
        return result;
    }
    
    private static URI parseCanonicalUri(String uri) {
        try {
            URI result = new URI(uri);
            validateUriScheme(result.getScheme());
            return result;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid canonical Agent Endpoint URI: " + uri, e);
        }
    }
    
    private static String composeUri(String scheme, String host, int port, String path,
        String query) {
        String formattedHost = host.indexOf(':') >= 0 ? '[' + host + ']' : host;
        StringBuilder result = new StringBuilder().append(scheme).append("://")
            .append(formattedHost).append(':').append(port).append(path);
        if (query != null) {
            result.append('?').append(query);
        }
        return result.toString();
    }
    
    private static Map<String, String> stringMetadata(Map<String, String> rawMetadata) {
        if (rawMetadata == null) {
            throw new IllegalArgumentException("Naming instance metadata must not be null");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : rawMetadata.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(
                    "Agent Endpoint Naming metadata must contain only non-null strings");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    private static void validateNamingControlKeys(Map<String, String> metadata) {
        if (metadata.containsKey(
            com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_ENABLE)
            || metadata.containsKey(
                com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_WEIGHT)) {
            throw new IllegalArgumentException(
                "Endpoint metadata uses a reserved Naming control key");
        }
    }
    
    private static void validateReservedKeys(Map<String, String> metadata) {
        for (String key : metadata.keySet()) {
            if (key.startsWith(Constants.Agent.AGENT_ENDPOINT_METADATA_PREFIX)
                && !KNOWN_RESERVED_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                    "Unknown Agent Endpoint reserved metadata key: " + key);
            }
        }
    }
    
    private static String requiredMetadata(Map<String, String> metadata, String key) {
        String result = metadata.get(key);
        if (result == null) {
            throw new IllegalArgumentException("Missing Agent Endpoint metadata: " + key);
        }
        return result;
    }
    
    private static void validateLegacyMetadata(Map<String, String> metadata) {
        String protocolVersion =
            metadata.get(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY);
        if (protocolVersion != null && !protocolVersion.isEmpty()) {
            AgentValidationUtils.validateProtocolVersion(protocolVersion);
        }
        String tenant = metadata.get(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY);
        if (tenant != null && tenant.length() > 256) {
            throw new IllegalArgumentException("Invalid Agent Endpoint tenant");
        }
    }
    
    private static Map<String, String> publicMetadata(Map<String, String> metadata) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!entry.getKey().startsWith(Constants.Agent.AGENT_ENDPOINT_METADATA_PREFIX)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        AgentValidationUtils.validateEndpointMetadata(result);
        validateNamingControlKeys(result);
        return result;
    }
    
    private static int parsePriority(String value) {
        if (value.isEmpty() || value.length() > 10
            || value.length() > 1 && value.charAt(0) == '0') {
            throw new IllegalArgumentException("Invalid Agent Endpoint priority: " + value);
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                throw new IllegalArgumentException("Invalid Agent Endpoint priority: " + value);
            }
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Agent Endpoint priority: " + value, e);
        }
    }
    
    private static void validateUriScheme(String value) {
        if (value == null || !URI_SCHEME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Agent Endpoint URI scheme: " + value);
        }
    }
    
    private static boolean isTlsScheme(String scheme) {
        return "https".equals(scheme) || "wss".equals(scheme);
    }
    
    private static void validateHost(String host) {
        if (host == null || host.isEmpty() || host.length() > MAX_HOST_LENGTH) {
            throw new IllegalArgumentException("Invalid Naming instance host: " + host);
        }
    }
    
    private static void validateCompleteMetadata(Map<String, String> metadata) {
        int length = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            length += entry.getKey().length() + entry.getValue().length();
            if (length > MAX_NAMING_METADATA_LENGTH) {
                throw new IllegalArgumentException(
                    "Agent Endpoint Naming metadata exceeds "
                        + MAX_NAMING_METADATA_LENGTH + " characters");
            }
        }
    }
    
    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
    
    private static RuntimeEndpointState runtimeState(boolean enabled, boolean healthy) {
        if (!enabled) {
            return RuntimeEndpointState.DISABLED;
        }
        if (!healthy) {
            return RuntimeEndpointState.UNHEALTHY;
        }
        return RuntimeEndpointState.AVAILABLE;
    }
}
