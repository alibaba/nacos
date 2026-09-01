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
import com.alibaba.nacos.ai.utils.AgentEndpointUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aRuntimeSnapshotComparatorTest {
    
    private CanonicalA2aEndpointOperationService canonicalService;
    
    private A2aRuntimeSnapshotComparator comparator;
    
    @BeforeEach
    void setUp() {
        canonicalService = new CanonicalA2aEndpointOperationService(null, null);
        comparator = new A2aRuntimeSnapshotComparator(canonicalService);
    }
    
    @Test
    void shouldCompareCompletePublicRuntimeSemanticsRegardlessOfOrder() throws Exception {
        AgentEndpoint first = endpoint("1.0.0", "2001:db8::1", 8080, true);
        first.setProtocolVersion("0.3");
        first.setTenant("tenant-a");
        first.setQuery("a=b");
        AgentEndpoint second = endpoint("1.0.0", "127.0.0.2", 8081, false);
        Instance oldFirst = legacy(first);
        Instance oldSecond = legacy(second);
        oldFirst.setEnabled(false);
        oldFirst.setHealthy(false);
        oldFirst.setWeight(2.5D);
        oldFirst.getMetadata().put("region", "cn-hz");
        Instance newFirst = canonical(first, oldFirst);
        Instance newSecond = canonical(second, oldSecond);
        
        assertTrue(comparator.equivalent(Arrays.asList(oldFirst, oldSecond),
            Arrays.asList(newSecond, newFirst), "1.0.0"));
        
        newFirst.setHealthy(true);
        assertFalse(comparator.equivalent(Arrays.asList(oldFirst, oldSecond),
            Arrays.asList(newFirst, newSecond), "1.0.0"));
        newFirst.setHealthy(false);
        newFirst.setEnabled(true);
        assertFalse(comparator.equivalent(Arrays.asList(oldFirst, oldSecond),
            Arrays.asList(newFirst, newSecond), "1.0.0"));
        newFirst.setEnabled(false);
        newFirst.setWeight(3D);
        assertFalse(comparator.equivalent(Arrays.asList(oldFirst, oldSecond),
            Arrays.asList(newFirst, newSecond), "1.0.0"));
    }
    
    @Test
    void shouldCompareUriTransportProtocolTenantPriorityAndPublicMetadata() throws Exception {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080, true);
        endpoint.setProtocolVersion("0.3");
        endpoint.setTenant("tenant-a");
        endpoint.setQuery("a=b");
        Instance historical = legacy(endpoint);
        historical.getMetadata().put("region", "cn-hz");
        Instance canonical = canonical(endpoint, historical);
        assertTrue(comparator.equivalent(Collections.singletonList(historical),
            Collections.singletonList(canonical), "1.0.0"));
        
        canonical.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY, "1.0");
        assertFalse(comparator.equivalent(Collections.singletonList(historical),
            Collections.singletonList(canonical), "1.0.0"));
        canonical = canonical(endpoint, historical);
        canonical.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY, "tenant-b");
        assertFalse(comparator.equivalent(Collections.singletonList(historical),
            Collections.singletonList(canonical), "1.0.0"));
        canonical = canonical(endpoint, historical);
        canonical.getMetadata().put("region", "cn-sh");
        assertFalse(comparator.equivalent(Collections.singletonList(historical),
            Collections.singletonList(canonical), "1.0.0"));
        canonical = canonical(endpoint, historical);
        canonical.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY, "1");
        assertFalse(comparator.equivalent(Collections.singletonList(historical),
            Collections.singletonList(canonical), "1.0.0"));
    }
    
    @Test
    void shouldIgnoreOtherCanonicalVersionsButRequireExactProjection() throws Exception {
        AgentEndpoint first = endpoint("1.0.0", "127.0.0.1", 8080, false);
        AgentEndpoint other = endpoint("2.0.0", "127.0.0.2", 8081, false);
        Instance historical = legacy(first);
        Instance exact = canonical(first, historical);
        Instance otherVersion = canonicalService.toCanonicalInstance(other);
        
        assertTrue(comparator.equivalent(Collections.singletonList(historical),
            Arrays.asList(otherVersion, exact), "1.0.0"));
        
        exact.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY,
            "[1.0.0,2.0.0]");
        assertFalse(comparator.equivalent(Collections.singletonList(historical),
            Collections.singletonList(exact), "1.0.0"));
    }
    
    @Test
    void shouldRejectMalformedHistoricalOrCanonicalSnapshots() throws Exception {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080, false);
        Instance initialHistorical = legacy(endpoint);
        Instance canonical = canonical(endpoint, initialHistorical);
        
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(null, Collections.singletonList(canonical), "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(initialHistorical), null,
                "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(initialHistorical),
                Collections.singletonList(canonical), " "));
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(null),
                Collections.singletonList(canonical), "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(initialHistorical),
                Collections.singletonList(null), "1.0.0"));
        
        initialHistorical.setMetadata(null);
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(initialHistorical),
                Collections.singletonList(canonical), "1.0.0"));
        Instance finalHistorical = legacy(endpoint);
        finalHistorical.getMetadata().remove(Constants.Agent.AGENT_ENDPOINT_PATH_KEY);
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(finalHistorical),
                Collections.singletonList(canonical), "1.0.0"));
        Instance invalidTls = legacy(endpoint);
        invalidTls.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY, "yes");
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(invalidTls),
                Collections.singletonList(canonical), "1.0.0"));
        Instance nullMetadataKey = legacy(endpoint);
        nullMetadataKey.getMetadata().put(null, "value");
        assertThrows(IllegalArgumentException.class,
            () -> comparator.equivalent(Collections.singletonList(nullMetadataKey),
                Collections.singletonList(canonical), "1.0.0"));
    }
    
    @Test
    void shouldPreserveDuplicateCardinality() throws Exception {
        AgentEndpoint endpoint = endpoint("1.0.0", "127.0.0.1", 8080, false);
        Instance historical = legacy(endpoint);
        Instance canonical = canonical(endpoint, historical);
        
        assertFalse(comparator.equivalent(Arrays.asList(historical, historical),
            Collections.singletonList(canonical), "1.0.0"));
        assertTrue(comparator.equivalent(Collections.emptyList(), Collections.emptyList(),
            "1.0.0"));
    }
    
    @Test
    void shouldKeepSnapshotEntriesValueBasedAndNormalizeNullWeight() throws Exception {
        Class<?> entryClass = Class.forName(
            A2aRuntimeSnapshotComparator.class.getName() + "$SnapshotEntry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(String.class,
            String.class, Integer.class, Double.class, java.util.Map.class, String.class,
            String.class, Boolean.class, Boolean.class);
        constructor.setAccessible(true);
        Object nullWeight = constructor.newInstance("http://127.0.0.1:8080", "HTTP+JSON",
            null, null, null, "", "", true, true);
        Object zeroWeight = constructor.newInstance("http://127.0.0.1:8080", "HTTP+JSON",
            null, 0D, Collections.emptyMap(), "", "", true, true);
        Object different = constructor.newInstance("http://127.0.0.1:8081", "HTTP+JSON",
            null, 0D, Collections.emptyMap(), "", "", true, true);
        
        assertTrue(nullWeight.equals(nullWeight));
        assertFalse(nullWeight.equals(null));
        assertFalse(nullWeight.equals("other"));
        assertTrue(nullWeight.equals(zeroWeight));
        assertFalse(nullWeight.equals(different));
        assertTrue(nullWeight.hashCode() == zeroWeight.hashCode());
    }
    
    private Instance legacy(AgentEndpoint endpoint) throws Exception {
        Instance result = AgentEndpointUtil.transferToInstance(endpoint);
        result.setMetadata(new HashMap<String, String>(result.getMetadata()));
        return result;
    }
    
    private Instance canonical(AgentEndpoint endpoint, Instance operationalSource) {
        Instance result = canonicalService.toCanonicalInstance(endpoint);
        result.setMetadata(new HashMap<String, String>(result.getMetadata()));
        result.setEnabled(operationalSource.isEnabled());
        result.setHealthy(operationalSource.isHealthy());
        result.setWeight(operationalSource.getWeight());
        for (java.util.Map.Entry<String, String> entry : operationalSource.getMetadata()
            .entrySet()) {
            if (!entry.getKey().startsWith(Constants.Agent.AGENT_ENDPOINT_METADATA_PREFIX)) {
                result.getMetadata().put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    private AgentEndpoint endpoint(String version, String address, int port, boolean tls) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress(address);
        result.setPort(port);
        result.setPath("/rpc");
        result.setSupportTls(tls);
        result.setProtocol("HTTP");
        result.setTransport("HTTP+JSON");
        return result;
    }
}
