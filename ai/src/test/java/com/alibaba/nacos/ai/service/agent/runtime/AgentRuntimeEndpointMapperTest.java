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
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointState;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeEndpointMapperTest {
    
    private static final String ENABLE_KEY =
        com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_ENABLE;
    
    private static final String WEIGHT_KEY =
        com.alibaba.nacos.naming.constants.Constants.PUBLISH_INSTANCE_WEIGHT;
    
    @Test
    void testMapEndpointToNamingAndBack() {
        Endpoint endpoint = endpoint("HTTPS://EXAMPLE.COM/a2a?tenant=nacos", "JSON-RPC");
        endpoint.setPriority(3);
        endpoint.setWeight(2.5D);
        endpoint.setHealthy(false);
        endpoint.setMetadata(Collections.singletonMap("region", "cn-hangzhou"));
        
        Instance instance = AgentRuntimeEndpointMapper.toInstance(endpoint, "1.0.0",
            "[1.0.0,1.0.0]");
        
        assertNull(instance.getServiceName());
        assertEquals("example.com", instance.getIp());
        assertEquals(443, instance.getPort());
        assertEquals("JSON-RPC", instance.getClusterName());
        assertEquals(2.5D, instance.getWeight());
        assertTrue(instance.isEnabled());
        assertTrue(instance.isHealthy());
        assertTrue(instance.isEphemeral());
        Map<String, String> metadata = instance.getMetadata();
        assertEquals("/a2a", metadata.get(Constants.Agent.AGENT_ENDPOINT_PATH_KEY));
        assertEquals("tenant=nacos",
            metadata.get(Constants.Agent.AGENT_ENDPOINT_QUERY_KEY));
        assertEquals("JSON-RPC",
            metadata.get(Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY));
        assertEquals("https", metadata.get(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY));
        assertEquals("true", metadata.get(Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY));
        assertEquals("3", metadata.get(Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY));
        assertEquals("1.0.0", metadata.get(Constants.Agent.AGENT_ENDPOINT_VERSION_KEY));
        assertEquals("[1.0.0]",
            metadata.get(Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY));
        assertEquals("cn-hangzhou", metadata.get("region"));
        
        instance.setEnabled(false);
        instance.setHealthy(false);
        RuntimeEndpointSnapshotItem result =
            AgentRuntimeEndpointMapper.fromInstance(instance, 1234L);
        
        assertEndpoint(result.getEndpoint(), "https://example.com:443/a2a?tenant=nacos",
            "JSON-RPC", 3, 2.5D);
        assertEquals("cn-hangzhou", result.getEndpoint().getMetadata().get("region"));
        assertEquals("1.0.0", result.getBindings().get(0).getRuntimeVersion());
        assertEquals("[1.0.0]", result.getBindings().get(0).getVersionRange());
        assertFalse(result.getEnabled());
        assertFalse(result.getHealthy());
        assertEquals(RuntimeEndpointState.DISABLED, result.getState());
        assertEquals(1234L, result.getLastUpdatedTime());
    }
    
    @Test
    void testMapDefaultsAndIpv6() {
        Instance defaultInstance = AgentRuntimeEndpointMapper.toInstance(
            endpoint("http://NACOS.EXAMPLE", "http"), "1.0.0", null);
        
        assertEquals("nacos.example", defaultInstance.getIp());
        assertEquals(80, defaultInstance.getPort());
        assertEquals("", defaultInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_PATH_KEY));
        assertFalse(defaultInstance.getMetadata().containsKey(
            Constants.Agent.AGENT_ENDPOINT_QUERY_KEY));
        assertEquals("0", defaultInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY));
        assertEquals("[1.0.0]", defaultInstance.getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY));
        assertEquals(1D, defaultInstance.getWeight());
        
        Instance ipv6Instance = AgentRuntimeEndpointMapper.toInstance(
            endpoint("wss://[2001:0DB8:0:0:0:0:0:1]/rpc", "websocket"),
            "1.0.0", "[1.0.0]");
        RuntimeEndpointSnapshotItem result =
            AgentRuntimeEndpointMapper.fromInstance(ipv6Instance, 1L);
        
        assertEquals("2001:db8::1", ipv6Instance.getIp());
        assertEquals(443, ipv6Instance.getPort());
        assertEquals("wss://[2001:db8::1]:443/rpc", result.getEndpoint().getUri());
    }
    
    @Test
    void testAcceptLegacyMetadataWithoutExposingIt() {
        Instance instance = validInstance();
        instance.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY, "1.0");
        instance.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY, "public");
        
        RuntimeEndpointSnapshotItem result =
            AgentRuntimeEndpointMapper.fromInstance(instance, 1L);
        
        assertNull(result.getEndpoint().getMetadata());
        assertEquals("https://example.com:443/agent", result.getEndpoint().getUri());
        
        instance.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY, "");
        instance.getMetadata().put(Constants.Agent.AGENT_ENDPOINT_TENANT_KEY, "");
        assertEquals("https://example.com:443/agent",
            AgentRuntimeEndpointMapper.fromInstance(instance, 1L).getEndpoint().getUri());
    }
    
    @Test
    void testMappedSnapshotItemDoesNotShareNamingMetadata() {
        Instance instance = validInstance();
        instance.getMetadata().put("region", "cn-hangzhou");
        
        RuntimeEndpointSnapshotItem result =
            AgentRuntimeEndpointMapper.fromInstance(instance, 1234L);
        result.getEndpoint().getMetadata().put("region", "outside");
        
        assertEquals("cn-hangzhou", instance.getMetadata().get("region"));
        assertEquals(RuntimeEndpointState.AVAILABLE, result.getState());
        assertEquals(1234L, result.getLastUpdatedTime());
    }
    
    @Test
    void testRejectInvalidForwardInput() {
        Endpoint valid = endpoint("http://127.0.0.1:8848/agent", "http");
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.toInstance(null, "1.0.0", null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.toInstance(valid, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.toInstance(valid, "1.0.0", "[2.0.0]"));
        valid.setMetadata(Collections.singletonMap(ENABLE_KEY, "false"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.toInstance(valid, "1.0.0", null));
        valid.setMetadata(Collections.singletonMap(WEIGHT_KEY, "2"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.toInstance(valid, "1.0.0", null));
    }
    
    @Test
    void testRejectOversizedForwardMetadata() {
        Endpoint endpoint = endpoint("http://127.0.0.1:8848/agent", "http");
        Map<String, String> oversized = new LinkedHashMap<String, String>();
        for (int i = 0; i < 4; i++) {
            oversized.put(repeat((char) ('a' + i), 64), repeat('x', 256));
        }
        endpoint.setMetadata(oversized);
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.toInstance(endpoint, "1.0.0", null));
    }
    
    @Test
    void testRejectInvalidNamingIdentityAndMetadataShape() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.fromInstance(null, 1L));
        assertInvalidInstance(value -> value.setMetadata(null));
        assertInvalidInstance(value -> value.setIp(null));
        assertInvalidInstance(value -> value.setIp(repeat('a', 254)));
        assertInvalidInstance(value -> value.setPort(0));
        assertInvalidInstance(value -> value.setPort(65536));
        assertInvalidInstance(value -> value.setClusterName("bad_cluster"));
        assertInvalidInstance(value -> value.getMetadata().put(null, "value"));
        assertInvalidInstance(value -> value.getMetadata().put("value", null));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_METADATA_PREFIX + "unknown__", "value"));
    }
    
    @Test
    void testRejectMissingRequiredMetadata() {
        List<String> requiredKeys = Arrays.asList(Constants.Agent.AGENT_ENDPOINT_PATH_KEY,
            Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY,
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY,
            Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY,
            Constants.Agent.AGENT_ENDPOINT_VERSION_KEY,
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY,
            Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY);
        for (String key : requiredKeys) {
            Instance instance = validInstance();
            instance.getMetadata().remove(key);
            assertThrows(IllegalArgumentException.class,
                () -> AgentRuntimeEndpointMapper.fromInstance(instance, 1L), key);
        }
    }
    
    @Test
    void testRejectConflictingReservedMetadata() {
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_TRANSPORT_KEY, "grpc"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_KEY, "HTTPS"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY, "TRUE"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_SUPPORT_TLS_KEY, "false"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY, "[1.0.0,1.0.0]"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY, "[2.0.0]"));
    }
    
    @Test
    void testRejectInvalidPriorityAndUriProjection() {
        for (String priority : Arrays.asList("", "01", "-1", "2147483648", "99999999999")) {
            assertInvalidInstance(value -> value.getMetadata().put(
                Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY, priority));
        }
        assertInvalidInstance(value -> value.setIp("EXAMPLE.COM"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_QUERY_KEY, "bad query"));
    }
    
    @Test
    void testRejectInvalidLegacyAndCapacityMetadata() {
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_PROTOCOL_VERSION_KEY, "bad version"));
        assertInvalidInstance(value -> value.getMetadata().put(
            Constants.Agent.AGENT_ENDPOINT_TENANT_KEY, repeat('t', 257)));
        assertInvalidInstance(value -> {
            for (int i = 0; i < 4; i++) {
                value.getMetadata().put(repeat((char) ('a' + i), 64),
                    repeat('x', 256));
            }
        });
    }
    
    private void assertInvalidInstance(Consumer<Instance> mutation) {
        Instance instance = validInstance();
        mutation.accept(instance);
        assertThrows(IllegalArgumentException.class,
            () -> AgentRuntimeEndpointMapper.fromInstance(instance, 1L));
    }
    
    private Instance validInstance() {
        return AgentRuntimeEndpointMapper.toInstance(
            endpoint("https://example.com:443/agent", "http"), "1.0.0", "[1.0.0]");
    }
    
    private Endpoint endpoint(String uri, String transport) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport(transport);
        return result;
    }
    
    private void assertEndpoint(Endpoint endpoint, String uri, String transport, int priority,
        double weight) {
        assertEquals(uri, endpoint.getUri());
        assertEquals(transport, endpoint.getTransport());
        assertEquals(priority, endpoint.getPriority());
        assertEquals(weight, endpoint.getWeight());
        assertNull(endpoint.getHealthy());
    }
    
    private String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }
}
