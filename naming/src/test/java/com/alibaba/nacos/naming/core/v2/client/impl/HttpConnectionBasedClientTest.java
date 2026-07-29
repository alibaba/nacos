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

package com.alibaba.nacos.naming.core.v2.client.impl;

import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncData;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.ClientConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpConnectionBasedClientTest {
    
    private static final String CLIENT_ID = "HTTP_CLIENT@@client";
    
    @BeforeAll
    static void setUpEnvironment() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testIdentityAndDefaultLifecycle() {
        HttpConnectionBasedClient client = new HttpConnectionBasedClient(CLIENT_ID, null);
        
        assertEquals(CLIENT_ID, client.getClientId());
        assertEquals(CLIENT_ID, HttpConnectionBasedClient.getInternalClientId("client"));
        assertTrue(HttpConnectionBasedClient.isHttpClientId(CLIENT_ID));
        assertFalse(HttpConnectionBasedClient.isHttpClientId("connection"));
        assertFalse(HttpConnectionBasedClient.isHttpClientId(null));
        assertTrue(client.isEphemeral());
        assertTrue(client.isPublisherHealthy());
        assertEquals(0L, client.getRevision());
        
        long oldClientTime = client.getLastUpdatedTime();
        long oldReplicaTime = client.getLastRenewTime();
        client.renewClient();
        client.renewReplica();
        
        assertTrue(client.getLastUpdatedTime() >= oldClientTime);
        assertTrue(client.getLastRenewTime() >= oldReplicaTime);
        assertTrue(client.isExpire(Math.max(client.getLastUpdatedTime(), client.getLastRenewTime())
            + ClientConfig.getInstance().getClientExpiredTime() + 1));
        assertEquals(1L, client.recalculateRevision());
        
        ClientSyncData syncData = client.generateSyncData();
        assertEquals(ClientConstants.HTTP_CONNECTION_BASED,
            syncData.getAttributes().getClientAttribute(ClientConstants.CONNECTION_TYPE));
        assertEquals(1L,
            syncData.getAttributes().<Long>getClientAttribute(ClientConstants.REVISION));
        assertEquals(client.getLastUpdatedTime(),
            syncData.getAttributes()
                .<Long>getClientAttribute(ClientConstants.HTTP_CLIENT_LAST_UPDATED_TIME));
        assertEquals(client.getPublisherLastUpdatedTime(),
            syncData.getAttributes()
                .<Long>getClientAttribute(ClientConstants.HTTP_PUBLISHER_LAST_UPDATED_TIME));
        assertEquals(Boolean.TRUE,
            syncData.getAttributes()
                .getClientAttribute(ClientConstants.HTTP_PUBLISHER_HEALTHY));
    }
    
    @Test
    void testAttributesAndSynchronization() {
        ClientAttributes attributes = attributes(7, 100L, 200L, false);
        attributes.addClientAttribute("subject", "user");
        HttpConnectionBasedClient client =
            new HttpConnectionBasedClient(CLIENT_ID, attributes);
        
        assertEquals(7L, client.getRevision());
        assertEquals(100L, client.getLastUpdatedTime());
        assertEquals(200L, client.getPublisherLastUpdatedTime());
        assertFalse(client.isPublisherHealthy());
        
        ClientSyncData syncData = client.generateSyncData();
        assertNotSame(attributes.getClientAttributes(),
            syncData.getAttributes().getClientAttributes());
        assertEquals("user", syncData.getAttributes().getClientAttribute("subject"));
        
        long oldRenewTime = client.getLastRenewTime();
        ClientAttributes synchronizedAttributes = attributes(8L, 300L, 400L, true);
        client.synchronizeAttributes(synchronizedAttributes);
        
        assertEquals(300L, client.getLastUpdatedTime());
        assertEquals(400L, client.getPublisherLastUpdatedTime());
        assertTrue(client.isPublisherHealthy());
        assertTrue(client.getLastRenewTime() >= oldRenewTime);
        
        ClientAttributes invalidAttributes = new ClientAttributes();
        invalidAttributes.addClientAttribute(ClientConstants.REVISION, "invalid");
        invalidAttributes.addClientAttribute(ClientConstants.HTTP_CLIENT_LAST_UPDATED_TIME,
            "invalid");
        invalidAttributes.addClientAttribute(ClientConstants.HTTP_PUBLISHER_LAST_UPDATED_TIME,
            "invalid");
        invalidAttributes.addClientAttribute(ClientConstants.HTTP_PUBLISHER_HEALTHY, "invalid");
        HttpConnectionBasedClient defaults =
            new HttpConnectionBasedClient(CLIENT_ID, invalidAttributes);
        assertEquals(0L, defaults.getRevision());
        assertTrue(defaults.isPublisherHealthy());
    }
    
    @Test
    void testPublisherHealthForSingleAndBatchPublications() {
        HttpConnectionBasedClient client =
            new HttpConnectionBasedClient(CLIENT_ID, new ClientAttributes());
        Service singleService = Service.newService("namespace", "group", "single");
        InstancePublishInfo single = instance("1.1.1.1", 8080);
        client.addServiceInstance(singleService, single);
        
        Service batchService = Service.newService("namespace", "group", "batch");
        InstancePublishInfo first = instance("2.2.2.2", 8081);
        InstancePublishInfo second = instance("3.3.3.3", 8082);
        BatchInstancePublishInfo batch = new BatchInstancePublishInfo();
        batch.setHealthy(true);
        batch.setInstancePublishInfos(Arrays.asList(first, second));
        client.addServiceInstance(batchService, batch);
        
        assertTrue(client.markPublisherUnhealthy());
        assertFalse(client.isPublisherHealthy());
        assertFalse(single.isHealthy());
        assertFalse(batch.isHealthy());
        assertFalse(first.isHealthy());
        assertFalse(second.isHealthy());
        assertFalse(client.markPublisherUnhealthy());
        
        long previousPublisherTime = client.getPublisherLastUpdatedTime();
        assertTrue(client.renewPublisher());
        assertTrue(client.isPublisherHealthy());
        assertTrue(client.getPublisherLastUpdatedTime() >= previousPublisherTime);
        assertTrue(single.isHealthy());
        assertTrue(batch.isHealthy());
        assertTrue(first.isHealthy());
        assertTrue(second.isHealthy());
        assertFalse(client.renewPublisher());
        
        client.resetPublisherLiveness();
        assertEquals(0L, client.getPublisherLastUpdatedTime());
        assertTrue(client.isPublisherHealthy());
        
        HttpConnectionBasedClient empty =
            new HttpConnectionBasedClient(CLIENT_ID, new ClientAttributes());
        assertFalse(empty.markPublisherUnhealthy());
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
    
    private InstancePublishInfo instance(String ip, int port) {
        InstancePublishInfo result = new InstancePublishInfo(ip, port);
        result.setHealthy(true);
        return result;
    }
}
