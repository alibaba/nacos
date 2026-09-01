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

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Safe, stable log rendering shared by RAD Watch clients and servers.
 *
 * <p>Opaque identifiers and selector values are reduced to short SHA-256 correlation tokens.
 * Complete descriptors, Endpoint metadata, credentials, and selector values are never rendered.
 * Logging helpers are best effort and never throw into the Watch control path.</p>
 *
 * @author Nacos
 */
public final class AgentWatchLogUtils {
    
    private static final String SHA_256 = "SHA-256";
    
    private static final int TOKEN_LENGTH = 12;
    
    private static final int MAX_SAFE_VALUE_LENGTH = 128;
    
    private AgentWatchLogUtils() {
    }
    
    /**
     * Build a non-reversible process-independent correlation token.
     *
     * @param value opaque identifier or sensitive selector value
     * @return short lowercase token, or {@code -} when absent
     */
    public static String token(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        return token(value, SHA_256);
    }
    
    static String token(String value, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte each : bytes) {
                result.append(Character.forDigit((each >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(each & 0x0f, 16));
            }
            return result.substring(0, Math.min(TOKEN_LENGTH, result.length()));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }
    
    /**
     * Render an equality fingerprint without writing its complete digest.
     *
     * @param fingerprint fingerprint token
     * @return algorithm and abbreviated digest
     */
    public static String fingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return "-";
        }
        int separator = fingerprint.indexOf(':');
        if (separator < 0) {
            return token(fingerprint);
        }
        String algorithm = safe(fingerprint.substring(0, separator));
        String digest = fingerprint.substring(separator + 1);
        return algorithm + ':' + (digest.length() <= TOKEN_LENGTH ? safe(digest)
            : safe(digest.substring(0, TOKEN_LENGTH)));
    }
    
    /**
     * Render one canonical discovery selection without exposing metadata selector values.
     *
     * @param source discovery request
     * @return stable one-line selection summary
     */
    public static String describeRequest(AgentDiscoveryRequest source) {
        try {
            AgentDiscoveryRequest request =
                AgentDiscoveryCanonicalizer.canonicalizeRequest(source);
            AgentReference reference = request.getReference();
            AgentDiscoveryFilter filter = request.getFilter();
            return "namespace=" + safe(request.getNamespaceId()) + ", agent="
                + safe(reference.getAgentName()) + ", version=" + safe(reference.getVersion())
                + ", label=" + safe(reference.getLabel()) + ", protocols="
                + safeList(filter == null ? null : filter.getProtocols())
                + ", protocolVersion="
                + safe(filter == null ? null : filter.getProtocolVersion()) + ", transports="
                + safeList(filter == null ? null : filter.getTransports())
                + ", endpointSources="
                + safeList(filter == null ? null : filter.getEndpointSources())
                + ", metadataSelector="
                + safeSelector(filter == null ? null : filter.getMetadataSelector());
        } catch (RuntimeException e) {
            return "invalidRequest=" + e.getClass().getSimpleName();
        }
    }
    
    /**
     * Render the public shape of a materialized discovery snapshot.
     *
     * @param source complete discovery snapshot
     * @return namespace, Agent, version, protocol and Endpoint counts, and abbreviated digest
     */
    public static String describeResult(AgentDiscoveryResult source) {
        try {
            AgentDiscoveryResult result = AgentDiscoveryCanonicalizer.canonicalizeResult(source);
            int endpointCount = 0;
            List<String> protocols = new ArrayList<String>();
            if (result.getCallInterfaces() != null) {
                for (AgentDiscoveryCallInterface each : result.getCallInterfaces()) {
                    protocols.add(each.getProtocol());
                    if (each.getEndpointSets() != null) {
                        for (EndpointSet endpointSet : each.getEndpointSets()) {
                            if (endpointSet.getEndpoints() != null) {
                                endpointCount += endpointSet.getEndpoints().size();
                            }
                        }
                    }
                }
            }
            return "namespace=" + safe(result.getNamespaceId()) + ", agent="
                + safe(result.getAgentName()) + ", version=" + safe(result.getVersion())
                + ", contentDigest=" + fingerprint(result.getContentDigest()) + ", protocols="
                + safeList(protocols) + ", interfaceCount="
                + (result.getCallInterfaces() == null ? 0 : result.getCallInterfaces().size())
                + ", endpointCount=" + endpointCount;
        } catch (RuntimeException e) {
            return "invalidResult=" + e.getClass().getSimpleName();
        }
    }
    
    /**
     * Render opaque Watch ids as stable tokens.
     *
     * @param values opaque ids
     * @return token list
     */
    public static String tokens(Iterable<String> values) {
        if (values == null) {
            return "[]";
        }
        List<String> result = new ArrayList<String>();
        for (String each : values) {
            result.add(token(each));
        }
        return result.toString();
    }
    
    private static String safeSelector(Map<String, String> selector) {
        if (selector == null || selector.isEmpty()) {
            return "{}";
        }
        List<String> entries = new ArrayList<String>(selector.size());
        for (Map.Entry<String, String> each : selector.entrySet()) {
            entries.add(safe(each.getKey()) + "=#" + token(each.getValue()));
        }
        Collections.sort(entries);
        return entries.toString();
    }
    
    private static String safeList(Iterable<?> values) {
        if (values == null) {
            return "[]";
        }
        List<String> result = new ArrayList<String>();
        for (Object each : values) {
            result.add(safe(each == null ? null : each.toString()));
        }
        return result.toString();
    }
    
    private static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        String result = value.replace('\r', '_').replace('\n', '_');
        return result.length() <= MAX_SAFE_VALUE_LENGTH ? result
            : result.substring(0, MAX_SAFE_VALUE_LENGTH);
    }
}
