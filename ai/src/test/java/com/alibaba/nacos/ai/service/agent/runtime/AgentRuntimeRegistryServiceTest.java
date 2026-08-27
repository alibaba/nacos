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
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointState;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeRegistryServiceTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final String AGENT_NAME = "demo-agent";
    
    private static final String PROTOCOL = "a2a";
    
    private static final String PUBLISHER_ID = "connection-1";
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private ClientManager clientManager;
    
    private AgentRuntimeRegistryService registryService;
    
    @BeforeEach
    void setUp() {
        AgentRuntimePublicationCapacityGate publicationCapacityGate =
            new AgentRuntimePublicationCapacityGate(clientManager, Integer.MAX_VALUE);
        registryService = new AgentRuntimeRegistryService(serviceStorage,
            clientOperationService, publicationCapacityGate);
    }
    
    @Test
    void testRegisterDelegatesCompleteBatchToNaming() throws NacosException {
        AgentEndpointRegistrationBatch batch = registration("1.0.0",
            "[1.0.0,2.0.0)", Arrays.asList(
                endpoint("https://one.example.com/agent", "json-rpc"),
                endpoint("https://two.example.com/agent", "json-rpc")));
        
        registryService.register(PUBLISHER_ID, batch);
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Instance>> instancesCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(clientOperationService).batchRegisterInstance(eq(expectedService()),
            instancesCaptor.capture(), eq(PUBLISHER_ID));
        List<Instance> instances = instancesCaptor.getValue();
        assertEquals(2, instances.size());
        assertEquals("1.0.0", instances.get(0).getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_KEY));
        assertEquals("[1.0.0,2.0.0)", instances.get(0).getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY));
        assertEquals("one.example.com", instances.get(0).getIp());
        verify(serviceStorage, never()).getData(any());
    }
    
    @Test
    void testLaterRegistrationIsAnotherCompleteReplacement() throws NacosException {
        registryService.register(PUBLISHER_ID, registration("1.0.0", null,
            Arrays.asList(endpoint("https://one.example.com/agent", "json-rpc"),
                endpoint("https://two.example.com/agent", "json-rpc"))));
        registryService.register(PUBLISHER_ID, registration("2.0.0", null,
            Collections.singletonList(
                endpoint("https://three.example.com/agent", "json-rpc"))));
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Instance>> instancesCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(clientOperationService, times(2)).batchRegisterInstance(eq(expectedService()),
            instancesCaptor.capture(), eq(PUBLISHER_ID));
        List<Instance> replacement = instancesCaptor.getAllValues().get(1);
        assertEquals(1, replacement.size());
        assertEquals("three.example.com", replacement.get(0).getIp());
        assertEquals("2.0.0", replacement.get(0).getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_KEY));
        assertEquals("[2.0.0]", replacement.get(0).getMetadata().get(
            Constants.Agent.AGENT_ENDPOINT_VERSION_RANGE_KEY));
    }
    
    @Test
    void testRejectInvalidRegistrationBeforeNamingWrite() {
        AgentEndpointRegistrationBatch invalid =
            registration("1.0.0", null, Collections.<Endpoint>emptyList());
        
        assertThrows(IllegalArgumentException.class,
            () -> registryService.register(PUBLISHER_ID, invalid));
        
        verify(clientOperationService, never()).batchRegisterInstance(any(), any(), any());
    }
    
    @Test
    void testDeregisterPublisherRemovesCompleteNamingPublication() {
        registryService.deregisterPublisher(PUBLISHER_ID, NAMESPACE_ID, AGENT_NAME, PROTOCOL);
        
        verify(clientOperationService).deregisterInstance(eq(expectedService()),
            any(Instance.class), eq(PUBLISHER_ID));
        verify(serviceStorage, never()).getData(any());
    }
    
    @Test
    void testRejectInvalidDeregisterIdentityBeforeNamingWrite() {
        assertThrows(IllegalArgumentException.class,
            () -> registryService.deregisterPublisher(PUBLISHER_ID, null, AGENT_NAME, PROTOCOL));
        assertThrows(IllegalArgumentException.class,
            () -> registryService.deregisterPublisher(PUBLISHER_ID, NAMESPACE_ID, null, PROTOCOL));
        assertThrows(IllegalArgumentException.class,
            () -> registryService.deregisterPublisher(PUBLISHER_ID, NAMESPACE_ID, AGENT_NAME,
                null));
        
        verify(clientOperationService, never()).deregisterInstance(any(), any(), any());
    }
    
    @Test
    void testSnapshotAggregatesBindingsOnlyWhenReading() throws NacosException {
        Endpoint endpoint = endpoint("https://example.com/agent", "json-rpc");
        Instance versionOne = instance(endpoint, "1.0.0", "[1.0.0]", true, false);
        Instance versionTwo = instance(endpoint, "2.0.0", "[2.0.0]", true, true);
        when(serviceStorage.getData(expectedService()))
            .thenReturn(serviceInfo(1234L, versionOne, versionTwo));
        
        RuntimeEndpointSnapshot all =
            registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null);
        RuntimeEndpointSnapshot versionOneSnapshot =
            registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        
        assertEquals(1, all.getItems().size());
        RuntimeEndpointSnapshotItem allItem = all.getItems().get(0);
        assertEquals(2, allItem.getBindings().size());
        assertEquals("1.0.0", allItem.getBindings().get(0).getRuntimeVersion());
        assertEquals("2.0.0", allItem.getBindings().get(1).getRuntimeVersion());
        assertEquals(1234L, allItem.getLastUpdatedTime());
        assertTrue(allItem.getHealthy());
        assertEquals(1, versionOneSnapshot.getItems().size());
        RuntimeEndpointSnapshotItem selected = versionOneSnapshot.getItems().get(0);
        assertEquals(1, selected.getBindings().size());
        assertEquals("1.0.0", selected.getBindings().get(0).getRuntimeVersion());
        assertFalse(selected.getHealthy());
        assertEquals(RuntimeEndpointState.UNHEALTHY, selected.getState());
    }
    
    @Test
    void testSnapshotDeduplicatesBindingsAndPreservesEndpointMetadata() throws NacosException {
        Endpoint endpoint = endpoint("https://example.com/agent", "json-rpc");
        endpoint.setMetadata(Collections.singletonMap("region", "cn-hangzhou"));
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(1234L,
            instance(endpoint, "1.0.0", "[1.0.0]", true, true),
            instance(endpoint, "1.0.0", "[1.0.0]", true, true),
            instance(endpoint, "1.0.0", "[1.0.0,2.0.0]", true, true)));
        
        RuntimeEndpointSnapshot snapshot =
            registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null);
        EndpointSet endpointSet =
            registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        
        assertEquals(1, snapshot.getItems().size());
        RuntimeEndpointSnapshotItem item = snapshot.getItems().get(0);
        assertEquals(2, item.getBindings().size());
        assertEquals("[1.0.0,2.0.0]", item.getBindings().get(0).getVersionRange());
        assertEquals("[1.0.0]", item.getBindings().get(1).getVersionRange());
        assertEquals("cn-hangzhou", item.getEndpoint().getMetadata().get("region"));
        assertEquals("cn-hangzhou",
            endpointSet.getEndpoints().get(0).getMetadata().get("region"));
    }
    
    @Test
    void testRuntimeEndpointSetUsesNamingEnabledAndHealthState() throws NacosException {
        Endpoint disabled = endpoint("https://disabled.example.com/agent", "json-rpc");
        disabled.setPriority(0);
        Endpoint unhealthy = endpoint("https://unhealthy.example.com/agent", "json-rpc");
        unhealthy.setPriority(1);
        Endpoint healthy = endpoint("https://healthy.example.com/agent", "json-rpc");
        healthy.setPriority(2);
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L,
            instance(disabled, "1.0.0", "[1.0.0]", false, true),
            instance(unhealthy, "1.0.0", "[1.0.0]", true, false),
            instance(healthy, "1.0.0", "[1.0.0]", true, true)));
        
        RuntimeEndpointSnapshot snapshot =
            registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        EndpointSet result =
            registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        
        assertEquals(RuntimeEndpointState.DISABLED,
            snapshotItem(snapshot, "https://disabled.example.com:443/agent").getState());
        assertEquals(RuntimeEndpointState.UNHEALTHY,
            snapshotItem(snapshot, "https://unhealthy.example.com:443/agent").getState());
        assertEquals(RuntimeEndpointState.AVAILABLE,
            snapshotItem(snapshot, "https://healthy.example.com:443/agent").getState());
        assertEquals(EndpointSource.RUNTIME, result.getSource());
        assertEquals(2, result.getEndpoints().size());
        assertEquals("https://unhealthy.example.com:443/agent",
            result.getEndpoints().get(0).getUri());
        assertFalse(result.getEndpoints().get(0).getHealthy());
        assertTrue(result.getEndpoints().get(1).getHealthy());
    }
    
    @Test
    void testRuntimeEndpointSetIgnoresDisabledContributionHealth() throws NacosException {
        Endpoint endpoint = endpoint("https://example.com/agent", "json-rpc");
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L,
            instance(endpoint, "1.0.0", "[1.0.0]", false, true),
            instance(endpoint, "1.0.0", "[1.0.0]", true, false)));
        
        RuntimeEndpointSnapshot snapshot =
            registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        EndpointSet endpointSet =
            registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        
        assertTrue(snapshot.getItems().get(0).getEnabled());
        assertTrue(snapshot.getItems().get(0).getHealthy());
        assertEquals(RuntimeEndpointState.AVAILABLE, snapshot.getItems().get(0).getState());
        assertEquals(1, endpointSet.getEndpoints().size());
        assertFalse(endpointSet.getEndpoints().get(0).getHealthy());
    }
    
    @Test
    void testRuntimeEndpointSetOrdersAndProducesContentRevision() throws NacosException {
        Endpoint lowPriority = endpoint("http://low.example.com/agent", "json-rpc");
        lowPriority.setPriority(10);
        Endpoint first = endpoint("http://a.example.com/agent", "json-rpc");
        Endpoint second = endpoint("http://b.example.com/agent", "json-rpc");
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L,
            instance(lowPriority, "1.0.0", "[1.0.0]", true, true),
            instance(second, "1.0.0", "[1.0.0]", true, true),
            instance(first, "1.0.0", "[1.0.0]", true, true)));
        
        EndpointSet result =
            registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "1.0.0");
        EndpointSet empty =
            registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, "2.0.0");
        
        assertEquals("http://a.example.com:80/agent", result.getEndpoints().get(0).getUri());
        assertEquals("http://b.example.com:80/agent", result.getEndpoints().get(1).getUri());
        assertEquals("http://low.example.com:80/agent",
            result.getEndpoints().get(2).getUri());
        assertNotEquals(result.getSourceRevision(), empty.getSourceRevision());
        assertTrue(empty.getEndpoints().isEmpty());
    }
    
    @Test
    void testCurrentRuntimeEndpointSetRefreshesNamingPublisherFacts() throws NacosException {
        Endpoint endpoint = endpoint("https://current.example.com/agent", "json-rpc");
        when(serviceStorage.getPushData(expectedService())).thenReturn(serviceInfo(10L,
            instance(endpoint, "1.0.0", "[1.0.0]", true, true)));
        
        EndpointSet result = registryService.getCurrentRuntimeEndpointSet(NAMESPACE_ID,
            AGENT_NAME, PROTOCOL, Collections.singletonList("1.0.0"));
        
        assertEquals(1, result.getEndpoints().size());
        assertEquals("https://current.example.com:443/agent",
            result.getEndpoints().get(0).getUri());
        verify(serviceStorage).getPushData(expectedService());
        verify(serviceStorage, never()).getData(expectedService());
    }
    
    @Test
    void testRuntimeEndpointSetAggregatesBindingsAcrossSelectedVersions()
        throws NacosException {
        Endpoint versionOne = endpoint("https://v1.example.com/agent", "json-rpc");
        Endpoint versionTwo = endpoint("https://v2.example.com/agent", "json-rpc");
        Endpoint future = endpoint("https://v3.example.com/agent", "json-rpc");
        Endpoint shared = endpoint("https://shared.example.com/agent", "json-rpc");
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L,
            instance(versionOne, "1.0.0", "[1.0.0]", true, true),
            instance(versionTwo, "2.0.0", "[2.0.0]", true, true),
            instance(future, "3.0.0", "[3.0.0]", true, true),
            instance(shared, "1.0.0", "[1.0.0]", true, false),
            instance(shared, "2.0.0", "[2.0.0]", true, true)));
        
        EndpointSet allOnline = registryService.getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME,
            PROTOCOL, Arrays.asList("1.0.0", "2.0.0"));
        EndpointSet latestOnly = registryService.getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME,
            PROTOCOL, Collections.singletonList("2.0.0"));
        
        assertEquals(3, allOnline.getEndpoints().size());
        assertEquals(2, allOnline.getEndpoints().get(0).getBindings().size());
        assertEquals("1.0.0",
            allOnline.getEndpoints().get(0).getBindings().get(0).getRuntimeVersion());
        assertEquals("2.0.0",
            allOnline.getEndpoints().get(0).getBindings().get(1).getRuntimeVersion());
        assertTrue(allOnline.getEndpoints().get(0).getHealthy());
        assertEquals(2, latestOnly.getEndpoints().size());
        assertEquals("2.0.0",
            latestOnly.getEndpoints().get(0).getBindings().get(0).getRuntimeVersion());
        assertNotEquals(allOnline.getSourceRevision(), latestOnly.getSourceRevision());
        assertFalse(allOnline.getEndpoints().stream()
            .anyMatch(endpoint -> endpoint.getUri().contains("v3.example.com")));
    }
    
    @Test
    void testRejectConflictingNamingProjection() {
        Endpoint first = endpoint("https://example.com/one", "json-rpc");
        Endpoint second = endpoint("https://example.com/two", "json-rpc");
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L,
            instance(first, "1.0.0", "[1.0.0]", true, true),
            instance(second, "1.0.0", "[1.0.0]", true, true)));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null));
        
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
        
        NacosApiException discoveryException = assertThrows(NacosApiException.class,
            () -> registryService.getRuntimeEndpointSet(NAMESPACE_ID, AGENT_NAME, PROTOCOL,
                Arrays.asList("1.0.0", "2.0.0")));
        assertEquals(NacosException.CONFLICT, discoveryException.getErrCode());
    }
    
    @Test
    void testRejectConflictingPriorityWeightAndMetadata() {
        Endpoint base = endpoint("https://example.com/agent", "json-rpc");
        Endpoint differentPriority = endpoint("https://example.com/agent", "json-rpc");
        differentPriority.setPriority(1);
        assertConflictingProjection(base, differentPriority);
        
        Endpoint differentWeight = endpoint("https://example.com/agent", "json-rpc");
        differentWeight.setWeight(2D);
        assertConflictingProjection(base, differentWeight);
        
        Endpoint differentMetadata = endpoint("https://example.com/agent", "json-rpc");
        differentMetadata.setMetadata(Collections.singletonMap("region", "cn-hangzhou"));
        assertConflictingProjection(base, differentMetadata);
    }
    
    @Test
    void testRejectOversizedNamingProjection() {
        List<Instance> instances = new ArrayList<Instance>();
        for (int i = 0; i <= 1000; i++) {
            instances.add(instance(
                endpoint("https://host-" + i + ".example.com/agent", "json-rpc"),
                "1.0.0", "[1.0.0]", true, true));
        }
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(instances);
        serviceInfo.setLastRefTime(10L);
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null));
        
        assertEquals(NacosException.OVER_THRESHOLD, exception.getErrCode());
    }
    
    @Test
    void testRejectInvalidNamingProjection() {
        Instance invalid = instance(endpoint("https://example.com/agent", "json-rpc"),
            "1.0.0", "[1.0.0]", true, true);
        invalid.getMetadata().remove(Constants.Agent.AGENT_ENDPOINT_VERSION_KEY);
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L, invalid));
        
        assertThrows(NacosRuntimeException.class,
            () -> registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null));
        
        when(serviceStorage.getData(expectedService())).thenReturn(null);
        assertThrows(NacosRuntimeException.class,
            () -> registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null));
    }
    
    @Test
    void testReturnEmptySnapshotFromEmptyNamingService() throws NacosException {
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(99L));
        
        RuntimeEndpointSnapshot result =
            registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null);
        
        assertTrue(result.getItems().isEmpty());
        assertEquals(NAMESPACE_ID, result.getNamespaceId());
        assertEquals(AGENT_NAME, result.getAgentName());
        assertEquals(PROTOCOL, result.getProtocol());
    }
    
    @Test
    void testRejectNullRuntimeEndpointSetVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, (String) null));
        assertThrows(IllegalArgumentException.class,
            () -> registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, (List<String>) null));
        assertThrows(IllegalArgumentException.class,
            () -> registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, Collections.<String>emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> registryService.getRuntimeEndpointSet(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, Collections.singletonList("latest")));
    }
    
    private AgentEndpointRegistrationBatch registration(String runtimeVersion,
        String versionRange, List<Endpoint> endpoints) {
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setNamespaceId(NAMESPACE_ID);
        result.setAgentName(AGENT_NAME);
        result.setProtocol(PROTOCOL);
        result.setRuntimeVersion(runtimeVersion);
        result.setVersionRange(versionRange);
        result.setEndpoints(endpoints);
        return result;
    }
    
    private Instance instance(Endpoint endpoint, String runtimeVersion, String versionRange,
        boolean enabled, boolean healthy) {
        Instance result =
            AgentRuntimeEndpointMapper.toInstance(endpoint, runtimeVersion, versionRange);
        result.setEnabled(enabled);
        result.setHealthy(healthy);
        return result;
    }
    
    private ServiceInfo serviceInfo(long lastRefTime, Instance... instances) {
        ServiceInfo result = new ServiceInfo();
        result.setHosts(Arrays.asList(instances));
        result.setLastRefTime(lastRefTime);
        return result;
    }
    
    private Endpoint endpoint(String uri, String transport) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport(transport);
        return result;
    }
    
    private Service expectedService() {
        return Service.newService(NAMESPACE_ID, Constants.Agent.AGENT_ENDPOINT_GROUP,
            RadServiceNameComposer.compose(AGENT_NAME, PROTOCOL));
    }
    
    private void assertConflictingProjection(Endpoint first, Endpoint second) {
        when(serviceStorage.getData(expectedService())).thenReturn(serviceInfo(10L,
            instance(first, "1.0.0", "[1.0.0]", true, true),
            instance(second, "1.0.0", "[1.0.0]", true, true)));
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> registryService.getRuntimeEndpointSnapshot(
                NAMESPACE_ID, AGENT_NAME, PROTOCOL, null));
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
    }
    
    private RuntimeEndpointSnapshotItem snapshotItem(RuntimeEndpointSnapshot snapshot,
        String uri) {
        for (RuntimeEndpointSnapshotItem item : snapshot.getItems()) {
            if (uri.equals(item.getEndpoint().getUri())) {
                return item;
            }
        }
        throw new AssertionError("Missing Runtime Endpoint snapshot item: " + uri);
    }
}
