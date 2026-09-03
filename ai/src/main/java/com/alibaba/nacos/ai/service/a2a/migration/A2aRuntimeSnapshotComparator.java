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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.CanonicalA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeEndpointMapper;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Compares historical exact-Version A2A Runtime state with its canonical RAD projection.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>

 * <p>This comparator exists only for the Nacos 3.0-3.2 Runtime cutover gate.</p>
 *
 * @author Nacos
 */
@Component
public class A2aRuntimeSnapshotComparator {
    
    private final CanonicalA2aEndpointOperationService canonicalService;
    
    public A2aRuntimeSnapshotComparator(
        CanonicalA2aEndpointOperationService canonicalService) {
        this.canonicalService = canonicalService;
    }
    
    /**
     * Compare public Runtime semantics for one exact historical Agent Version.
     *
     * @param historicalInstances historical Version-specific Naming instances
     * @param canonicalInstances canonical RAD Naming instances for the Agent protocol
     * @param version exact historical Agent Version
     * @return {@code true} when the normalized multisets are equal
     */
    public boolean equivalent(Collection<Instance> historicalInstances,
        Collection<Instance> canonicalInstances, String version) {
        if (historicalInstances == null || canonicalInstances == null
            || StringUtils.isBlank(version)) {
            throw new IllegalArgumentException(
                "Historical, canonical, and exact Version snapshots are required");
        }
        List<SnapshotEntry> historical = new ArrayList<SnapshotEntry>(
            historicalInstances.size());
        for (Instance instance : historicalInstances) {
            historical.add(normalizeHistorical(instance, version));
        }
        List<SnapshotEntry> canonical = new ArrayList<SnapshotEntry>();
        for (Instance instance : canonicalInstances) {
            SnapshotEntry entry = normalizeCanonical(instance, version);
            if (entry != null) {
                canonical.add(entry);
            }
        }
        Collections.sort(historical);
        Collections.sort(canonical);
        return historical.equals(canonical);
    }
    
    private SnapshotEntry normalizeHistorical(Instance source, String version) {
        if (source == null || source.getMetadata() == null) {
            throw new IllegalArgumentException(
                "Historical A2A Naming instance and metadata are required");
        }
        Map<String, String> metadata = source.getMetadata();
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress(source.getIp());
        endpoint.setPort(source.getPort());
        endpoint.setPath(required(metadata, Constants.Agent.AGENT_ENDPOINT_PATH_KEY));
        endpoint.setTransport(required(metadata,
            Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY));
        String tls = required(metadata, Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY);
        if (!"true".equals(tls) && !"false".equals(tls)) {
            throw new IllegalArgumentException("Invalid historical A2A supportTls: " + tls);
        }
        endpoint.setSupportTls(Boolean.parseBoolean(tls));
        endpoint.setVersion(version);
        endpoint.setProtocolVersion(metadata.get(
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY));
        endpoint.setTenant(metadata.get(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY));
        endpoint.setProtocol(required(metadata, Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY));
        endpoint.setQuery(metadata.get(Constants.Agent.AGENT_ENDPOINT_QUERY_KEY));
        
        Instance canonical = canonicalService.toCanonicalInstance(endpoint);
        canonical.setEnabled(source.isEnabled());
        canonical.setHealthy(source.isHealthy());
        canonical.setWeight(source.getWeight());
        canonical.getMetadata().putAll(publicMetadata(metadata));
        return snapshot(AgentRuntimeEndpointMapper.fromInstance(canonical, 0L),
            canonical.getMetadata());
    }
    
    private SnapshotEntry normalizeCanonical(Instance source, String version) {
        if (source == null || source.getMetadata() == null) {
            throw new IllegalArgumentException(
                "Canonical RAD Naming instance and metadata are required");
        }
        RuntimeEndpointSnapshotItem item = AgentRuntimeEndpointMapper.fromInstance(source, 0L);
        String runtimeVersion = source.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_KEY);
        String versionRange = source.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY);
        if (!version.equals(runtimeVersion) || !('[' + version + ']').equals(versionRange)) {
            return null;
        }
        return snapshot(item, source.getMetadata());
    }
    
    private SnapshotEntry snapshot(RuntimeEndpointSnapshotItem item,
        Map<String, String> rawMetadata) {
        Endpoint endpoint = item.getEndpoint();
        return new SnapshotEntry(endpoint.getUri(), endpoint.getTransport(),
            endpoint.getPriority(), endpoint.getWeight(), endpoint.getMetadata(),
            valueOrEmpty(rawMetadata.get(
                Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY)),
            valueOrEmpty(rawMetadata.get(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY)),
            item.getEnabled(), item.getHealthy());
    }
    
    private Map<String, String> publicMetadata(Map<String, String> metadata) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(
                    "Historical A2A metadata must contain only non-null strings");
            }
            if (!entry.getKey().startsWith(Constants.Agent.AGENT_ENDPOINT_METADATA_PREFIX)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    private String required(Map<String, String> metadata, String key) {
        String result = metadata.get(key);
        if (result == null) {
            throw new IllegalArgumentException("Missing historical A2A metadata: " + key);
        }
        return result;
    }
    
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
    
    private static final class SnapshotEntry implements Comparable<SnapshotEntry> {
        
        private final String canonicalValue;
        
        private SnapshotEntry(String uri, String transport, Integer priority, Double weight,
            Map<String, String> metadata, String protocolVersion, String tenant,
            Boolean enabled, Boolean healthy) {
            double normalizedWeight = weight == null || weight == 0D ? 0D : weight;
            canonicalValue = Objects.toString(uri, "") + '\u0000'
                + Objects.toString(transport, "") + '\u0000'
                + Objects.toString(priority, "") + '\u0000'
                + Long.toHexString(Double.doubleToLongBits(normalizedWeight))
                + '\u0000' + new TreeMap<String, String>(metadata == null
                    ? Collections.emptyMap() : metadata)
                + '\u0000' + protocolVersion + '\u0000' + tenant + '\u0000' + enabled
                + '\u0000' + healthy;
        }
        
        @Override
        public int compareTo(SnapshotEntry other) {
            return canonicalValue.compareTo(other.canonicalValue);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SnapshotEntry)) {
                return false;
            }
            SnapshotEntry that = (SnapshotEntry) o;
            return canonicalValue.equals(that.canonicalValue);
        }
        
        @Override
        public int hashCode() {
            return canonicalValue.hashCode();
        }
    }
}
