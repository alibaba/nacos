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

import java.io.Serializable;
import java.util.Objects;

/**
 * Natural identity of a declared or runtime Endpoint within an Agent protocol group.
 *
 * @author Nacos
 */
public final class EndpointNaturalKey implements Comparable<EndpointNaturalKey>, Serializable {
    
    private static final long serialVersionUID = 8610093873480680781L;
    
    private final String namespaceId;
    
    private final String agentName;
    
    private final String protocol;
    
    private final String normalizedHost;
    
    private final int effectivePort;
    
    private final String transport;
    
    private EndpointNaturalKey(String namespaceId, String agentName, String protocol,
        String normalizedHost,
        int effectivePort, String transport) {
        this.namespaceId = namespaceId;
        this.agentName = agentName;
        this.protocol = protocol;
        this.normalizedHost = normalizedHost;
        this.effectivePort = effectivePort;
        this.transport = transport;
    }
    
    /**
     * Build a natural key from an Endpoint.
     *
     * @param namespaceId effective namespace
     * @param agentName Agent name
     * @param protocol protocol token
     * @param endpoint Endpoint value
     * @return canonical natural key
     * @throws IllegalArgumentException when any key field is invalid
     */
    public static EndpointNaturalKey of(String namespaceId, String agentName, String protocol,
        Endpoint endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("Endpoint must not be null");
        }
        return of(namespaceId, agentName, protocol, endpoint.getUri(), endpoint.getTransport());
    }
    
    /**
     * Build a natural key from the Endpoint fields that participate in identity.
     *
     * @param namespaceId effective namespace
     * @param agentName Agent name
     * @param protocol protocol token
     * @param uri Endpoint URI
     * @param transport transport token
     * @return canonical natural key
     * @throws IllegalArgumentException when any key field is invalid
     */
    public static EndpointNaturalKey of(String namespaceId, String agentName, String protocol,
        String uri,
        String transport) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateProtocol(protocol);
        AgentValidationUtils.validateTransport(transport);
        EndpointCanonicalizer.CanonicalEndpointUri canonicalUri =
            EndpointCanonicalizer.parseUri(uri);
        return new EndpointNaturalKey(namespaceId, agentName, protocol, canonicalUri.getHost(),
            canonicalUri.getPort(),
            transport);
    }
    
    /**
     * Return the effective namespace.
     *
     * @return namespace identifier
     */
    public String getNamespaceId() {
        return namespaceId;
    }
    
    /**
     * Return the original Agent name.
     *
     * @return Agent name
     */
    public String getAgentName() {
        return agentName;
    }
    
    /**
     * Return the protocol token.
     *
     * @return protocol token
     */
    public String getProtocol() {
        return protocol;
    }
    
    /**
     * Return the normalized host without IPv6 brackets.
     *
     * @return normalized host
     */
    public String getNormalizedHost() {
        return normalizedHost;
    }
    
    /**
     * Return the explicit or inferred port.
     *
     * @return effective port
     */
    public int getEffectivePort() {
        return effectivePort;
    }
    
    /**
     * Return the canonical transport token.
     *
     * @return transport token
     */
    public String getTransport() {
        return transport;
    }
    
    @Override
    public int compareTo(EndpointNaturalKey other) {
        Objects.requireNonNull(other, "other");
        int result = namespaceId.compareTo(other.namespaceId);
        if (result != 0) {
            return result;
        }
        result = agentName.compareTo(other.agentName);
        if (result != 0) {
            return result;
        }
        result = protocol.compareTo(other.protocol);
        if (result != 0) {
            return result;
        }
        result = normalizedHost.compareTo(other.normalizedHost);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(effectivePort, other.effectivePort);
        if (result != 0) {
            return result;
        }
        return transport.compareTo(other.transport);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EndpointNaturalKey)) {
            return false;
        }
        EndpointNaturalKey other = (EndpointNaturalKey) obj;
        return effectivePort == other.effectivePort && namespaceId.equals(other.namespaceId)
            && agentName.equals(other.agentName) && protocol.equals(other.protocol)
            && normalizedHost.equals(other.normalizedHost) && transport.equals(other.transport);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, agentName, protocol, normalizedHost, effectivePort,
            transport);
    }
    
    @Override
    public String toString() {
        return namespaceId + '/' + agentName + '/' + protocol + '/' + normalizedHost + ':'
            + effectivePort + '/'
            + transport;
    }
}
