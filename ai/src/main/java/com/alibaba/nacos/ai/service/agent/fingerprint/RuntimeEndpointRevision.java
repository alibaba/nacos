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

package com.alibaba.nacos.ai.service.agent.fingerprint;

import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionComparator;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryEndpoint;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import org.apache.commons.codec.digest.MurmurHash3;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates the deterministic revision of one filtered Runtime Endpoint projection.
 *
 * @author Nacos
 */
public final class RuntimeEndpointRevision {
    
    public static final String ALGORITHM_ID = "murmur3-x64-128-v1";
    
    public static final String TOKEN_PREFIX = ALGORITHM_ID + ':';
    
    private static final int MAX_ENDPOINTS = 1000;
    
    private static final int MURMUR_SEED = 0;
    
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    
    private RuntimeEndpointRevision() {
    }
    
    /**
     * Compute the opaque revision for an already aggregated and Version-filtered runtime set.
     *
     * @param namespaceId effective namespace used to validate and sort natural keys
     * @param agentName original Agent name used to validate and sort natural keys
     * @param protocol protocol group used to validate and sort natural keys
     * @param endpoints enabled Runtime Endpoints; health must be present
     * @return revision token in {@code murmur3-x64-128-v1:<32 lowercase hex>} form
     * @throws IllegalArgumentException when the projection is invalid
     */
    public static String compute(String namespaceId, String agentName, String protocol,
        List<AgentDiscoveryEndpoint> endpoints) {
        byte[] revisionBytes = revisionBytes(namespaceId, agentName, protocol, endpoints);
        long[] hash = MurmurHash3.hash128x64(revisionBytes, 0, revisionBytes.length, MURMUR_SEED);
        char[] value = new char[32];
        appendHex(hash[0], value, 0);
        appendHex(hash[1], value, 16);
        return TOKEN_PREFIX + new String(value);
    }
    
    static byte[] revisionBytes(String namespaceId, String agentName, String protocol,
        List<AgentDiscoveryEndpoint> endpoints) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateProtocol(protocol);
        if (endpoints == null) {
            throw new IllegalArgumentException("Runtime Endpoint projection must not be null");
        }
        if (endpoints.size() > MAX_ENDPOINTS) {
            throw new IllegalArgumentException(
                "Runtime Endpoint projection exceeds " + MAX_ENDPOINTS + " items");
        }
        Map<EndpointNaturalKey, AgentDiscoveryEndpoint> canonicalEndpoints =
            new TreeMap<EndpointNaturalKey, AgentDiscoveryEndpoint>();
        for (AgentDiscoveryEndpoint endpoint : endpoints) {
            Endpoint canonicalEndpoint = EndpointCanonicalizer.canonicalize(endpoint);
            AgentDiscoveryEndpoint canonical = copyEndpoint(canonicalEndpoint);
            if (canonical.getHealthy() == null) {
                throw new IllegalArgumentException("Runtime Endpoint healthy must not be null");
            }
            canonical.setBindings(canonicalBindings(endpoint.getBindings()));
            EndpointNaturalKey key = EndpointNaturalKey.of(namespaceId, agentName, protocol,
                canonical);
            if (canonicalEndpoints.put(key, canonical) != null) {
                throw new IllegalArgumentException("Duplicate Runtime Endpoint: " + key);
            }
        }
        return frame(canonicalEndpoints);
    }
    
    private static byte[] frame(Map<EndpointNaturalKey, AgentDiscoveryEndpoint> endpoints) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(endpoints.size());
            for (AgentDiscoveryEndpoint endpoint : endpoints.values()) {
                writeUtf8(output, endpoint.getUri());
                writeUtf8(output, endpoint.getTransport());
                output.writeInt(endpoint.getPriority());
                double weight = endpoint.getWeight() == 0D ? 0D : endpoint.getWeight();
                output.writeLong(Double.doubleToLongBits(weight));
                writeMetadata(output, endpoint.getMetadata());
                output.writeBoolean(endpoint.getHealthy());
                writeBindings(output, endpoint.getBindings());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to frame Runtime Endpoint projection", e);
        }
        return buffer.toByteArray();
    }
    
    private static List<RuntimeVersionBinding> canonicalBindings(
        List<RuntimeVersionBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalArgumentException("Runtime Endpoint bindings must not be empty");
        }
        List<RuntimeVersionBinding> result = new ArrayList<RuntimeVersionBinding>(bindings);
        for (RuntimeVersionBinding binding : result) {
            if (binding == null || binding.getRuntimeVersion() == null
                || binding.getVersionRange() == null) {
                throw new IllegalArgumentException("Runtime Endpoint binding must be complete");
            }
        }
        Collections.sort(result, new Comparator<RuntimeVersionBinding>() {
            
            @Override
            public int compare(RuntimeVersionBinding left, RuntimeVersionBinding right) {
                int comparison = AgentVersionComparator.compare(left.getRuntimeVersion(),
                    right.getRuntimeVersion());
                return comparison == 0
                    ? left.getVersionRange().compareTo(right.getVersionRange()) : comparison;
            }
        });
        return result;
    }
    
    private static void writeBindings(DataOutputStream output,
        List<RuntimeVersionBinding> bindings) throws IOException {
        output.writeInt(bindings.size());
        for (RuntimeVersionBinding binding : bindings) {
            writeUtf8(output, binding.getRuntimeVersion());
            writeUtf8(output, binding.getVersionRange());
        }
    }
    
    private static AgentDiscoveryEndpoint copyEndpoint(Endpoint source) {
        AgentDiscoveryEndpoint result = new AgentDiscoveryEndpoint();
        result.setUri(source.getUri());
        result.setTransport(source.getTransport());
        result.setPriority(source.getPriority());
        result.setWeight(source.getWeight());
        result.setMetadata(source.getMetadata());
        result.setHealthy(source.getHealthy());
        return result;
    }
    
    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
    
    private static void writeMetadata(DataOutputStream output, Map<String, String> metadata)
        throws IOException {
        if (metadata == null || metadata.isEmpty()) {
            output.writeInt(0);
            return;
        }
        Map<String, String> sorted = new TreeMap<String, String>(metadata);
        output.writeInt(sorted.size());
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            writeUtf8(output, entry.getKey());
            writeUtf8(output, entry.getValue());
        }
    }
    
    private static void appendHex(long value, char[] target, int offset) {
        for (int i = 15; i >= 0; i--) {
            target[offset + i] = HEX[(int) (value & 0x0F)];
            value >>>= 4;
        }
    }
}
