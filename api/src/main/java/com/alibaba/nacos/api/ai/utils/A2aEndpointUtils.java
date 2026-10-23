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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure A2A address conversion and public compatibility metadata rules.
 *
 * @author Nacos
 */
public final class A2aEndpointUtils {
    
    private A2aEndpointUtils() {
    }
    
    /**
     * Convert an isolated exact-Version address to the canonical RAD payload.
     * @param source legacy address
     * @return canonical Endpoint without a version binding
     */
    public static Endpoint toEndpoint(AgentEndpoint source) {
        if (source == null || blank(source.getAddress())) {
            throw new IllegalArgumentException("Legacy A2A Endpoint address must not be empty");
        }
        String scheme = blank(source.getProtocol())
            ? AiConstants.A2a.A2A_ENDPOINT_DEFAULT_PROTOCOL : source.getProtocol();
        if ("http".equalsIgnoreCase(scheme) && source.isSupportTls()) {
            scheme = "https";
        }
        String host = source.getAddress();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = '[' + host + ']';
        }
        String path = blank(source.getPath()) ? "" : source.getPath().startsWith("/")
            ? source.getPath() : '/' + source.getPath();
        String query = blank(source.getQuery()) ? "" : '?' + source.getQuery();
        Endpoint result = new Endpoint();
        result.setUri(scheme.toLowerCase(Locale.ROOT) + "://" + host + ':'
            + source.getPort() + path + query);
        result.setTransport(source.getTransport());
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        if (source.getProtocolVersion() != null && !source.getProtocolVersion().isEmpty()) {
            metadata.put(AiConstants.A2a.ENDPOINT_PROTOCOL_VERSION, source.getProtocolVersion());
        }
        if (source.getTenant() != null) {
            metadata.put(AiConstants.A2a.ENDPOINT_TENANT, source.getTenant());
        }
        result.setMetadata(metadata);
        return EndpointCanonicalizer.canonicalize(result);
    }
    
    /**
     * Project a RAD Endpoint to one A2A interface without changing the Endpoint.
     * @param endpoint runtime Endpoint
     * @param fallbackProtocolVersion target definition protocol version
     * @return legacy and current interface fields
     */
    public static AgentInterface toAgentInterface(Endpoint endpoint,
        String fallbackProtocolVersion) {
        AgentInterface result = new AgentInterface();
        result.setUrl(endpoint.getUri());
        result.setTransport(endpoint.getTransport());
        result.setProtocolBinding(endpoint.getTransport());
        result.setProtocolVersion(protocolVersion(endpoint.getMetadata(), fallbackProtocolVersion));
        result.setTenant(tenant(endpoint.getMetadata(), null));
        return result;
    }
    
    /**
     * Read a public protocol version, falling back only when its key is missing.
     * @param metadata public or internal Naming metadata
     * @param fallback historical or definition value
     * @return effective protocol version
     */
    public static String protocolVersion(Map<String, String> metadata, String fallback) {
        if (metadata != null && metadata.containsKey(AiConstants.A2a.ENDPOINT_PROTOCOL_VERSION)) {
            String result = metadata.get(AiConstants.A2a.ENDPOINT_PROTOCOL_VERSION);
            AgentValidationUtils.validateProtocolVersion(result);
            return result;
        }
        return fallback == null || fallback.isEmpty() ? null : fallback;
    }
    
    /**
     * Read a public tenant, preserving an explicit empty value.
     * @param metadata public or internal Naming metadata
     * @param fallback historical value, never a synthesized tenant
     * @return effective tenant
     */
    public static String tenant(Map<String, String> metadata, String fallback) {
        if (metadata != null && metadata.containsKey(AiConstants.A2a.ENDPOINT_TENANT)) {
            String result = metadata.get(AiConstants.A2a.ENDPOINT_TENANT);
            if (result == null || result.codePointCount(0, result.length()) > 256) {
                throw new IllegalArgumentException("Invalid A2A Endpoint tenant");
            }
            return result;
        }
        return fallback == null || fallback.isEmpty() ? null : fallback;
    }
    
    private static boolean blank(String value) {
        return com.alibaba.nacos.api.utils.StringUtils.isBlank(value);
    }
}
