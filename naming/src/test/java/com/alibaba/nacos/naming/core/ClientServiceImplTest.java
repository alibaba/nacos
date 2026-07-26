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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ClientServiceImpl} unit tests.
 */
@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {
    
    private static final String NAMESPACE = "namespace";
    
    private static final String GROUP = "group";
    
    private static final String SERVICE_NAME = "service";
    
    @InjectMocks
    private ClientServiceImpl clientService;
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    @Mock
    private DistroMapper distroMapper;
    
    @Test
    void testGetClientList() {
        when(clientManager.allClientId())
            .thenReturn(new LinkedHashSet<>(Arrays.asList("c1", "c2")));
        
        List<String> actual = clientService.getClientList();
        
        assertEquals(Arrays.asList("c1", "c2"), actual);
    }
    
    @Test
    void testGetClientDetailThrowsWhenClientMissing() {
        when(clientManager.getClient("missing")).thenReturn(null);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> clientService.getClientDetail("missing"));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertTrue(exception.getErrMsg().contains("Client id missing not exist."));
    }
    
    @Test
    void testGetClientDetailForIpPortClient() throws NacosApiException {
        Client client = mock(Client.class);
        when(clientManager.getClient("ip-client")).thenReturn(client);
        when(client.getClientId()).thenReturn("ip-client");
        when(client.isEphemeral()).thenReturn(false);
        when(client.getLastUpdatedTime()).thenReturn(123L);
        
        ClientSummaryInfo actual = clientService.getClientDetail("ip-client");
        
        assertEquals("ip-client", actual.getClientId());
        assertEquals(false, actual.isEphemeral());
        assertEquals(123L, actual.getLastUpdatedTime());
        assertEquals("ipPort", actual.getClientType());
        assertNull(actual.getConnectType());
    }
    
    @Test
    void testGetClientDetailForConnectionClient() throws NacosApiException {
        ConnectionBasedClient client = mock(ConnectionBasedClient.class);
        when(clientManager.getClient("connection-client")).thenReturn(client);
        when(client.getClientId()).thenReturn("connection-client");
        when(client.isEphemeral()).thenReturn(true);
        when(client.getLastUpdatedTime()).thenReturn(456L);
        ConnectionMeta meta = new ConnectionMeta("connection-client", "1.1.1.1", "2.2.2.2",
            9848, 8848, "grpc", "3.2.1", "app", Collections.emptyMap());
        Connection connection = mock(Connection.class);
        when(connection.getMetaInfo()).thenReturn(meta);
        when(connectionManager.getConnection("connection-client")).thenReturn(connection);
        
        ClientSummaryInfo actual = clientService.getClientDetail("connection-client");
        
        assertEquals("connection-client", actual.getClientId());
        assertEquals(true, actual.isEphemeral());
        assertEquals(456L, actual.getLastUpdatedTime());
        assertEquals("connection", actual.getClientType());
        assertEquals("grpc", actual.getConnectType());
        assertEquals("app", actual.getAppName());
        assertEquals("3.2.1", actual.getVersion());
        assertEquals("1.1.1.1", actual.getClientIp());
        assertEquals(9848, actual.getClientPort());
    }
    
    @Test
    void testGetPublishedServiceListAdapt() {
        Service singleService = Service.newService(NAMESPACE, GROUP, "single");
        Service batchService = Service.newService(NAMESPACE, GROUP, "batch");
        Client client = mockPublishedClient("client", Arrays.asList(singleService, batchService),
            Map.of(singleService, instance("1.1.1.1", 8848, "cluster-a"), batchService,
                batch(instance("2.2.2.2", 9848, "cluster-b"),
                    instance("3.3.3.3", 9849, "cluster-c"))));
        when(clientManager.getClient("client")).thenReturn(client);
        
        List<ObjectNode> actual = clientService.getPublishedServiceListAdapt("client");
        
        assertEquals(3, actual.size());
        assertPublishedNode(actual.get(0), "single", "1.1.1.1", 8848, "cluster-a");
        assertPublishedNode(actual.get(1), "batch", "2.2.2.2", 9848, "cluster-b");
        assertPublishedNode(actual.get(2), "batch", "3.3.3.3", 9849, "cluster-c");
    }
    
    @Test
    void testGetPublishedServiceList() {
        Service singleService = Service.newService(NAMESPACE, GROUP, "single");
        Service batchService = Service.newService(NAMESPACE, GROUP, "batch");
        Client client = mockPublishedClient("client", Arrays.asList(singleService, batchService),
            Map.of(singleService, instance("1.1.1.1", 8848, "cluster-a"), batchService,
                batch(instance("2.2.2.2", 9848, "cluster-b"))));
        when(clientManager.getClient("client")).thenReturn(client);
        
        List<ClientServiceInfo> actual = clientService.getPublishedServiceList("client");
        
        assertEquals(2, actual.size());
        assertClientServiceInfo(actual.get(0), "single", "1.1.1.1", 8848, "cluster-a");
        assertClientServiceInfo(actual.get(1), "batch", "2.2.2.2", 9848, "cluster-b");
    }
    
    @Test
    void testGetSubscribeServiceListAdapt() {
        Service service = Service.newService(NAMESPACE, GROUP, SERVICE_NAME);
        Subscriber subscriber = subscriber("1.1.1.1:8848", "agent", "app", "1.1.1.1", 8848);
        Client client = mockSubscribeClient(service, subscriber);
        when(clientManager.getClient("client")).thenReturn(client);
        
        List<ObjectNode> actual = clientService.getSubscribeServiceListAdapt("client");
        
        assertEquals(1, actual.size());
        assertEquals(NAMESPACE, actual.get(0).get("namespace").asText());
        assertEquals(GROUP, actual.get(0).get("group").asText());
        assertEquals(SERVICE_NAME, actual.get(0).get("serviceName").asText());
        assertEquals("app", actual.get(0).get("subscriberInfo").get("app").asText());
        assertEquals("agent", actual.get(0).get("subscriberInfo").get("agent").asText());
        assertEquals("1.1.1.1:8848", actual.get(0).get("subscriberInfo").get("addr").asText());
    }
    
    @Test
    void testGetSubscribeServiceList() {
        Service service = Service.newService(NAMESPACE, GROUP, SERVICE_NAME);
        Subscriber subscriber = subscriber("1.1.1.1:8848", "agent", "app", "1.1.1.1", 8848);
        Client client = mockSubscribeClient(service, subscriber);
        when(clientManager.getClient("client")).thenReturn(client);
        
        List<ClientServiceInfo> actual = clientService.getSubscribeServiceList("client");
        
        assertEquals(1, actual.size());
        assertEquals(NAMESPACE, actual.get(0).getNamespaceId());
        assertEquals(GROUP, actual.get(0).getGroupName());
        assertEquals(SERVICE_NAME, actual.get(0).getServiceName());
        assertEquals("1.1.1.1:8848", actual.get(0).getSubscriberInfo().getAddress());
        assertEquals("agent", actual.get(0).getSubscriberInfo().getAgent());
        assertEquals("app", actual.get(0).getSubscriberInfo().getAppName());
    }
    
    @Test
    void testGetPublishedClientListAdaptFiltersByIpAndPort() {
        Service service = Service.newService(NAMESPACE, GROUP, SERVICE_NAME, true);
        Client first = mockPublishedClient(instance("1.1.1.1", 8848, "cluster-a"));
        Client second = mockPublishedClient(instance("4.4.4.4", 8848, "cluster-d"));
        Client third = mockPublishedClient(batch(instance("1.1.1.1", 8848, "cluster-b"),
            instance("1.1.1.1", 8849, "cluster-c")));
        Client fourth = mockPublishedClient(instance("1.1.1.1", 8850, "cluster-e"));
        when(clientServiceIndexesManager.getAllClientsRegisteredService(service))
            .thenReturn(Arrays.asList("c1", "c2", "c3", "c4"));
        when(clientManager.getClient("c1")).thenReturn(first);
        when(clientManager.getClient("c2")).thenReturn(second);
        when(clientManager.getClient("c3")).thenReturn(third);
        when(clientManager.getClient("c4")).thenReturn(fourth);
        when(first.getInstancePublishInfo(service)).thenReturn(instance("1.1.1.1", 8848,
            "cluster-a"));
        when(second.getInstancePublishInfo(service)).thenReturn(instance("4.4.4.4", 8848,
            "cluster-d"));
        when(third.getInstancePublishInfo(service)).thenReturn(batch(instance("1.1.1.1", 8848,
            "cluster-b"), instance("1.1.1.1", 8849, "cluster-c")));
        when(fourth.getInstancePublishInfo(service)).thenReturn(instance("1.1.1.1", 8850,
            "cluster-e"));
        
        List<ObjectNode> actual = clientService.getPublishedClientList(NAMESPACE, GROUP,
            SERVICE_NAME, true, "1.1.1.1", 8848);
        
        assertEquals(2, actual.size());
        assertEquals("c1", actual.get(0).get("clientId").asText());
        assertEquals("cluster-a", actual.get(0).get("cluster").asText());
        assertEquals("c3", actual.get(1).get("clientId").asText());
        assertEquals("cluster-b", actual.get(1).get("cluster").asText());
    }
    
    @Test
    void testGetPublishedClientListFiltersOptionalIpAndPort() {
        Service service = Service.newService(NAMESPACE, GROUP, SERVICE_NAME);
        Client first = mockPublishedClient(instance("1.1.1.1", 8848, "cluster-a"));
        Client second = mockPublishedClient(batch(instance("2.2.2.2", 8848, "cluster-b"),
            instance("3.3.3.3", 9848, "cluster-c")));
        when(clientServiceIndexesManager.getAllClientsRegisteredService(service))
            .thenReturn(Arrays.asList("c1", "c2"));
        when(clientManager.getClient("c1")).thenReturn(first);
        when(clientManager.getClient("c2")).thenReturn(second);
        when(first.getInstancePublishInfo(service)).thenReturn(instance("1.1.1.1", 8848,
            "cluster-a"));
        when(second.getInstancePublishInfo(service)).thenReturn(batch(instance("2.2.2.2", 8848,
            "cluster-b"), instance("3.3.3.3", 9848, "cluster-c")));
        
        List<ClientPublisherInfo> actual = clientService.getPublishedClientList(NAMESPACE, GROUP,
            SERVICE_NAME, null, 8848);
        
        assertEquals(2, actual.size());
        assertClientPublisherInfo(actual.get(0), "c1", "1.1.1.1", 8848, "cluster-a");
        assertClientPublisherInfo(actual.get(1), "c2", "2.2.2.2", 8848, "cluster-b");
        
        List<ClientPublisherInfo> actualByIp = clientService.getPublishedClientList(NAMESPACE,
            GROUP, SERVICE_NAME, "2.2.2.2", null);
        
        assertEquals(1, actualByIp.size());
        assertClientPublisherInfo(actualByIp.get(0), "c2", "2.2.2.2", 8848, "cluster-b");
    }
    
    @Test
    void testGetSubscribeClientListAdaptFiltersByOptionalIpAndPort() {
        Service service = Service.newService(NAMESPACE, GROUP, SERVICE_NAME, true);
        Client first = mockSubscriberClient(service,
            subscriber("1.1.1.1:8848", "agent-a", "app-a", "1.1.1.1", 8848));
        Client second = mockSubscriberClient(service,
            subscriber("2.2.2.2:9848", "agent-b", "app-b", "2.2.2.2", 9848));
        when(clientServiceIndexesManager.getAllClientsSubscribeService(service))
            .thenReturn(Arrays.asList("c1", "c2"));
        when(clientManager.getClient("c1")).thenReturn(first);
        when(clientManager.getClient("c2")).thenReturn(second);
        
        List<ObjectNode> actual = clientService.getSubscribeClientList(NAMESPACE, GROUP,
            SERVICE_NAME, true, "1.1.1.1", null);
        
        assertEquals(1, actual.size());
        assertEquals("c1", actual.get(0).get("clientId").asText());
        assertEquals("1.1.1.1", actual.get(0).get("ip").asText());
        assertEquals(8848, actual.get(0).get("port").asInt());
        
        List<ObjectNode> actualByPort = clientService.getSubscribeClientList(NAMESPACE, GROUP,
            SERVICE_NAME, true, null, 9848);
        
        assertEquals(1, actualByPort.size());
        assertEquals("c2", actualByPort.get(0).get("clientId").asText());
        assertEquals("2.2.2.2", actualByPort.get(0).get("ip").asText());
        assertEquals(9848, actualByPort.get(0).get("port").asInt());
    }
    
    @Test
    void testGetSubscribeClientListFiltersByOptionalIpAndPort() {
        Service service = Service.newService(NAMESPACE, GROUP, SERVICE_NAME);
        Client first = mockSubscriberClient(service,
            subscriber("1.1.1.1:8848", "agent-a", "app-a", "1.1.1.1", 8848));
        Client second = mockSubscriberClient(service,
            subscriber("2.2.2.2:9848", "agent-b", "app-b", "2.2.2.2", 9848));
        when(clientServiceIndexesManager.getAllClientsSubscribeService(service))
            .thenReturn(Arrays.asList("c1", "c2"));
        when(clientManager.getClient("c1")).thenReturn(first);
        when(clientManager.getClient("c2")).thenReturn(second);
        
        List<ClientSubscriberInfo> actual = clientService.getSubscribeClientList(NAMESPACE, GROUP,
            SERVICE_NAME, null, 9848);
        
        assertEquals(1, actual.size());
        assertEquals("c2", actual.get(0).getClientId());
        assertEquals("2.2.2.2:9848", actual.get(0).getAddress());
        assertEquals("agent-b", actual.get(0).getAgent());
        assertEquals("app-b", actual.get(0).getAppName());
        
        List<ClientSubscriberInfo> actualByIp = clientService.getSubscribeClientList(NAMESPACE,
            GROUP, SERVICE_NAME, "1.1.1.1", null);
        
        assertEquals(1, actualByIp.size());
        assertEquals("c1", actualByIp.get(0).getClientId());
        assertEquals("1.1.1.1:8848", actualByIp.get(0).getAddress());
    }
    
    @Test
    void testGetResponsibleServer4Client() {
        when(distroMapper.mapSrv("1.1.1.1:8848")).thenReturn("server-a");
        
        Map<String, Object> actual = clientService.getResponsibleServer4Client("1.1.1.1", "8848");
        
        assertEquals("server-a", actual.get("responsibleServer").toString());
    }
    
    private Client mockPublishedClient(String clientId, List<Service> services,
        Map<Service, InstancePublishInfo> publishInfos) {
        Client client = mock(Client.class);
        when(client.getAllPublishedService()).thenReturn(services);
        publishInfos.forEach((service, publishInfo) -> when(client.getInstancePublishInfo(service))
            .thenReturn(publishInfo));
        return client;
    }
    
    private Client mockPublishedClient(InstancePublishInfo publishInfo) {
        Client client = mock(Client.class);
        return client;
    }
    
    private Client mockSubscribeClient(Service service, Subscriber subscriber) {
        Client client = mock(Client.class);
        when(client.getAllSubscribeService()).thenReturn(Collections.singletonList(service));
        when(client.getSubscriber(service)).thenReturn(subscriber);
        return client;
    }
    
    private Client mockSubscriberClient(Service service, Subscriber subscriber) {
        Client client = mock(Client.class);
        when(client.getSubscriber(service)).thenReturn(subscriber);
        return client;
    }
    
    private InstancePublishInfo instance(String ip, int port, String cluster) {
        InstancePublishInfo result = new InstancePublishInfo(ip, port);
        result.setCluster(cluster);
        return result;
    }
    
    private BatchInstancePublishInfo batch(InstancePublishInfo... publishInfos) {
        BatchInstancePublishInfo result = new BatchInstancePublishInfo();
        result.setInstancePublishInfos(Arrays.asList(publishInfos));
        return result;
    }
    
    private Subscriber subscriber(String address, String agent, String app, String ip, int port) {
        return new Subscriber(address, agent, app, ip, NAMESPACE, SERVICE_NAME, port);
    }
    
    private void assertPublishedNode(ObjectNode actual, String serviceName, String ip, int port,
        String cluster) {
        assertEquals(NAMESPACE, actual.get("namespace").asText());
        assertEquals(GROUP, actual.get("group").asText());
        assertEquals(serviceName, actual.get("serviceName").asText());
        assertEquals(ip, actual.get("registeredInstance").get("ip").asText());
        assertEquals(port, actual.get("registeredInstance").get("port").asInt());
        assertEquals(cluster, actual.get("registeredInstance").get("cluster").asText());
    }
    
    private void assertClientServiceInfo(ClientServiceInfo actual, String serviceName, String ip,
        int port, String cluster) {
        assertEquals(NAMESPACE, actual.getNamespaceId());
        assertEquals(GROUP, actual.getGroupName());
        assertEquals(serviceName, actual.getServiceName());
        assertEquals(ip, actual.getPublisherInfo().getIp());
        assertEquals(port, actual.getPublisherInfo().getPort());
        assertEquals(cluster, actual.getPublisherInfo().getClusterName());
    }
    
    private void assertClientPublisherInfo(ClientPublisherInfo actual, String clientId, String ip,
        int port, String cluster) {
        assertEquals(clientId, actual.getClientId());
        assertEquals(ip, actual.getIp());
        assertEquals(port, actual.getPort());
        assertEquals(cluster, actual.getClusterName());
    }
}
