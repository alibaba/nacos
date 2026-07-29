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

package com.alibaba.nacos.naming.core.v2.client.manager.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.HttpConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.ClientConfig;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class HttpConnectionBasedClientManagerTest {
    
    private static final String CLIENT_ID = "HTTP_CLIENT@@client";
    
    private DistroMapper distroMapper;
    
    private HttpConnectionBasedClientManager clientManager;
    
    @BeforeAll
    static void setUpEnvironment() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @BeforeEach
    void setUp() {
        distroMapper = mock(DistroMapper.class);
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(true);
        clientManager = new HttpConnectionBasedClientManager(distroMapper, false);
    }
    
    @Test
    void testPublicConstructorSchedulesCleaner() {
        assertNotNull(new HttpConnectionBasedClientManager(distroMapper));
    }
    
    @Test
    void testClientLifecycleAndOwnership() {
        ClientAttributes attributes = currentAttributes();
        
        assertTrue(clientManager.clientConnected(CLIENT_ID, attributes));
        assertTrue(clientManager.clientConnected(CLIENT_ID, attributes));
        Client client = clientManager.getClient(CLIENT_ID);
        assertNotNull(client);
        assertTrue(clientManager.contains(CLIENT_ID));
        assertEquals(Collections.singleton(CLIENT_ID), clientManager.allClientId());
        assertTrue(clientManager.isResponsibleClient(client));
        assertFalse(clientManager.isResponsibleClient(mock(Client.class)));
        
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        assertTrue(clientManager.clientConnected(CLIENT_ID, attributes));
        
        assertTrue(clientManager.clientDisconnected(CLIENT_ID));
        assertFalse(clientManager.contains(CLIENT_ID));
        assertTrue(clientManager.clientDisconnected(CLIENT_ID));
    }
    
    @Test
    void testSyncClientLifecycle() {
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        ClientAttributes initial = attributes(1L, 100L, 200L, true);
        assertTrue(clientManager.syncClientConnected(CLIENT_ID, initial));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        assertEquals(100L, client.getLastUpdatedTime());
        
        ClientAttributes update = attributes(2L, 300L, 400L, false);
        assertTrue(clientManager.syncClientConnected(CLIENT_ID, update));
        
        assertSame(client, clientManager.getClient(CLIENT_ID));
        assertEquals(300L, client.getLastUpdatedTime());
        assertEquals(400L, client.getPublisherLastUpdatedTime());
        assertFalse(client.isPublisherHealthy());
    }
    
    @Test
    void testVerifyClient() {
        assertFalse(clientManager.verifyClient(new DistroClientVerifyInfo(CLIENT_ID, 0L)));
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        clientManager.syncClientConnected(CLIENT_ID, attributes(3L, 100L, 200L, true));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        client.setRevision(3L);
        long oldRenewTime = client.getLastRenewTime();
        
        assertTrue(clientManager.verifyClient(new DistroClientVerifyInfo(CLIENT_ID, 0L)));
        assertTrue(clientManager.verifyClient(new DistroClientVerifyInfo(CLIENT_ID, 3L)));
        assertTrue(client.getLastRenewTime() >= oldRenewTime);
        assertFalse(clientManager.verifyClient(new DistroClientVerifyInfo(CLIENT_ID, 4L)));
    }
    
    @Test
    void testRenewClient() {
        assertFalse(clientManager.renewClient(CLIENT_ID));
        clientManager.clientConnected(CLIENT_ID, attributes(0L, 1L, 1L, true));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        
        assertTrue(clientManager.renewClient(CLIENT_ID));
        assertTrue(client.getLastUpdatedTime() > 1L);
        
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        assertFalse(clientManager.renewClient(CLIENT_ID));
    }
    
    @Test
    void testRenewPublisher() {
        assertFalse(clientManager.renewPublisher(CLIENT_ID));
        clientManager.clientConnected(CLIENT_ID, currentAttributes());
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        assertFalse(clientManager.renewPublisher(CLIENT_ID));
        
        Service service = service("publisher");
        InstancePublishInfo instance = instance();
        client.addServiceInstance(service, instance);
        assertTrue(clientManager.renewPublisher(CLIENT_ID));
        
        assertTrue(client.markPublisherUnhealthy());
        long revision = client.getRevision();
        assertTrue(clientManager.renewPublisher(CLIENT_ID));
        assertTrue(client.isPublisherHealthy());
        assertTrue(instance.isHealthy());
        assertEquals(revision + 1, client.getRevision());
        
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        assertFalse(clientManager.renewPublisher(CLIENT_ID));
    }
    
    @Test
    void testDisconnectIfEmpty() {
        assertTrue(clientManager.disconnectIfEmpty(CLIENT_ID));
        clientManager.clientConnected(CLIENT_ID, currentAttributes());
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        Service service = service("state");
        client.addServiceInstance(service, instance());
        assertFalse(clientManager.disconnectIfEmpty(CLIENT_ID));
        
        client.removeServiceInstance(service);
        client.addServiceSubscriber(service, new Subscriber());
        assertFalse(clientManager.disconnectIfEmpty(CLIENT_ID));
        
        client.removeServiceSubscriber(service);
        assertTrue(clientManager.disconnectIfEmpty(CLIENT_ID));
        assertFalse(clientManager.contains(CLIENT_ID));
    }
    
    @Test
    void testCleanerIgnoresMissingAndFreshClients() {
        clientManager.cleanExpiredClients();
        HttpConnectionBasedClientManager manager = spy(clientManager);
        when(manager.allClientId()).thenReturn(Collections.singleton("missing"));
        manager.cleanExpiredClients(System.currentTimeMillis());
        
        long now = System.currentTimeMillis();
        clientManager.clientConnected(CLIENT_ID, attributes(0L, now, now, true));
        clientManager.cleanExpiredClients(now);
        assertTrue(clientManager.contains(CLIENT_ID));
        assertTrue(((HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID))
            .isPublisherHealthy());
    }
    
    @Test
    void testCleanerRemovesExpiredReplica() {
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        clientManager.syncClientConnected(CLIENT_ID, currentAttributes());
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        
        clientManager.cleanExpiredClients(client.getLastRenewTime()
            + ClientConfig.getInstance().getClientExpiredTime() - 1);
        assertTrue(clientManager.contains(CLIENT_ID));
        
        clientManager.cleanExpiredClients(client.getLastRenewTime()
            + ClientConfig.getInstance().getClientExpiredTime() + 1);
        assertFalse(clientManager.contains(CLIENT_ID));
    }
    
    @Test
    void testCleanerRemovesExpiredClient() {
        clientManager.clientConnected(CLIENT_ID, currentAttributes());
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        
        clientManager.cleanExpiredClients(
            Math.max(client.getLastUpdatedTime(), client.getLastRenewTime())
                + ClientConfig.getInstance().getClientExpiredTime() + 1);
        
        assertFalse(clientManager.contains(CLIENT_ID));
    }
    
    @Test
    void testCleanerUsesReplicaVerificationWindowAfterResponsibilityTransfer() {
        long now = System.currentTimeMillis();
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(false);
        clientManager.syncClientConnected(CLIENT_ID,
            attributes(0L, now - ClientConfig.getInstance().getClientExpiredTime() - 1,
                now - Constants.DEFAULT_IP_DELETE_TIMEOUT - 1, true));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        client.addServiceInstance(service("transferred"), instance());
        when(distroMapper.responsible(CLIENT_ID)).thenReturn(true);
        
        clientManager.cleanExpiredClients(now);
        
        assertTrue(clientManager.contains(CLIENT_ID));
        assertTrue(client.getLastRenewTime() >= now);
        assertTrue(client.isPublisherHealthy());
        
        clientManager.cleanExpiredClients(
            client.getLastRenewTime() + Constants.DEFAULT_HEART_BEAT_TIMEOUT + 1);
        assertFalse(client.isPublisherHealthy());
        
        clientManager.cleanExpiredClients(
            client.getLastRenewTime() + Constants.DEFAULT_IP_DELETE_TIMEOUT + 1);
        assertFalse(clientManager.contains(CLIENT_ID));
    }
    
    @Test
    void testCleanerMarksPublisherUnhealthyAndRecovers() {
        long now = System.currentTimeMillis();
        clientManager.clientConnected(CLIENT_ID,
            attributes(0L, now, now - Constants.DEFAULT_HEART_BEAT_TIMEOUT - 1, true));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        InstancePublishInfo instance = instance();
        client.addServiceInstance(service("unhealthy"), instance);
        
        long unhealthyTime = client.getLastRenewTime()
            + Constants.DEFAULT_HEART_BEAT_TIMEOUT + 1;
        clientManager.cleanExpiredClients(unhealthyTime);
        
        assertFalse(client.isPublisherHealthy());
        assertFalse(instance.isHealthy());
        assertEquals(1L, client.getRevision());
        clientManager.cleanExpiredClients(unhealthyTime + 1);
        assertEquals(1L, client.getRevision());
    }
    
    @Test
    void testCleanerExpiresPublisherAndKeepsSubscriberClient() {
        long now = System.currentTimeMillis();
        clientManager.clientConnected(CLIENT_ID,
            attributes(0L, now, now - Constants.DEFAULT_IP_DELETE_TIMEOUT - 1, false));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        Service service = service("expired");
        client.addServiceInstance(service, instance());
        client.addServiceSubscriber(service, new Subscriber());
        
        clientManager.cleanExpiredClients(
            client.getLastRenewTime() + Constants.DEFAULT_IP_DELETE_TIMEOUT + 1);
        
        assertTrue(clientManager.contains(CLIENT_ID));
        assertTrue(client.getAllPublishedService().isEmpty());
        assertTrue(client.isPublisherHealthy());
        assertEquals(0L, client.getPublisherLastUpdatedTime());
        assertEquals(1L, client.getRevision());
    }
    
    @Test
    void testCleanerExpiresPublisherAndRemovesEmptyClient() {
        long now = System.currentTimeMillis();
        clientManager.clientConnected(CLIENT_ID,
            attributes(0L, now, now - Constants.DEFAULT_IP_DELETE_TIMEOUT - 1, true));
        HttpConnectionBasedClient client =
            (HttpConnectionBasedClient) clientManager.getClient(CLIENT_ID);
        Service service = service("expired");
        client.addServiceInstance(service, instance());
        
        clientManager.cleanExpiredClients(
            client.getLastRenewTime() + Constants.DEFAULT_IP_DELETE_TIMEOUT + 1);
        
        assertNull(clientManager.getClient(CLIENT_ID));
        assertTrue(client.getAllPublishedService().isEmpty());
    }
    
    private ClientAttributes currentAttributes() {
        long now = System.currentTimeMillis();
        return attributes(0L, now, now, true);
    }
    
    private ClientAttributes attributes(Object revision, long clientTime, long publisherTime,
        boolean healthy) {
        ClientAttributes result = new ClientAttributes();
        result.addClientAttribute(ClientConstants.REVISION, revision);
        result.addClientAttribute(ClientConstants.HTTP_CLIENT_LAST_UPDATED_TIME, clientTime);
        result.addClientAttribute(ClientConstants.HTTP_PUBLISHER_LAST_UPDATED_TIME, publisherTime);
        result.addClientAttribute(ClientConstants.HTTP_PUBLISHER_HEALTHY, healthy);
        return result;
    }
    
    private Service service(String name) {
        return Service.newService("namespace", "group", name);
    }
    
    private InstancePublishInfo instance() {
        InstancePublishInfo result = new InstancePublishInfo("1.1.1.1", 8080);
        result.setHealthy(true);
        return result;
    }
}
