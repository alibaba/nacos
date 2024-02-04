/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncData;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncDatumSnapshot;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstanceData;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DistroClientDataProcessorTest {
    
    private static final String CLIENT_ID = "11111_1.1.1.1_3306";
    
    private static final String MOCK_TARGET_SERVER = "2.2.2.2:8848";
    
    private Client client;
    
    private DistroData distroData;
    
    private DistroKey distroKey;
    
    private ClientSyncData clientSyncData;
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private DistroProtocol distroProtocol;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private Serializer serializer;
    
    private DistroClientDataProcessor distroClientDataProcessor;
    
    @Before
    public void setUp() throws Exception {
        distroClientDataProcessor = new DistroClientDataProcessor(clientManager, distroProtocol);
        EnvUtil.setIsStandalone(false);
        client = new ConnectionBasedClient(CLIENT_ID, true, 0L);
        when(clientManager.getClient(CLIENT_ID)).thenReturn(client);
        when(clientManager.isResponsibleClient(client)).thenReturn(true);
        distroData = new DistroData();
        distroKey = new DistroKey();
        distroData.setDistroKey(distroKey);
        distroKey.setTargetServer(MOCK_TARGET_SERVER);
        distroKey.setResourceKey(CLIENT_ID);
        when(applicationContext.getBean(Serializer.class)).thenReturn(serializer);
        ApplicationUtils.injectContext(applicationContext);
        clientSyncData = mockClientSyncData();
        when(serializer.deserialize(any(), eq(ClientSyncData.class))).thenReturn(clientSyncData);
    }
    
    private ClientSyncData mockClientSyncData() {
        ClientSyncData result = new ClientSyncData();
        ClientAttributes clientAttributes = new ClientAttributes();
        clientAttributes.addClientAttribute(ClientConstants.REVISION, 1);
        result.setAttributes(clientAttributes);
        result.setClientId(CLIENT_ID);
        result.setNamespaces(Collections.singletonList("ns"));
        result.setGroupNames(Collections.singletonList("group"));
        result.setServiceNames(Collections.singletonList("service"));
        result.setInstancePublishInfos(Collections.singletonList(new InstancePublishInfo("3.3.3.3", 1111)));
        return result;
    }
    
    @After
    public void tearDown() throws Exception {
        NotifyCenter.deregisterSubscriber(distroClientDataProcessor);
    }
    
    @Test
    public void testFinishInitial() {
        assertFalse(distroClientDataProcessor.isFinishInitial());
        distroClientDataProcessor.finishInitial();
        assertTrue(distroClientDataProcessor.isFinishInitial());
    }
    
    @Test
    public void processType() {
        assertEquals(DistroClientDataProcessor.TYPE, distroClientDataProcessor.processType());
    }
    
    @Test
    public void testOnEventForStandalone() {
        EnvUtil.setIsStandalone(true);
        distroClientDataProcessor.onEvent(new ClientEvent.ClientVerifyFailedEvent(CLIENT_ID, MOCK_TARGET_SERVER));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientVerifyFailedEventWithoutClient() {
        when(clientManager.getClient(CLIENT_ID)).thenReturn(null);
        distroClientDataProcessor.onEvent(new ClientEvent.ClientVerifyFailedEvent(CLIENT_ID, MOCK_TARGET_SERVER));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientVerifyFailedEventWithPersistentClient() {
        client = mock(Client.class);
        when(client.isEphemeral()).thenReturn(false);
        when(clientManager.getClient(CLIENT_ID)).thenReturn(client);
        distroClientDataProcessor.onEvent(new ClientEvent.ClientVerifyFailedEvent(CLIENT_ID, MOCK_TARGET_SERVER));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientVerifyFailedEventWithoutResponsible() {
        when(clientManager.isResponsibleClient(client)).thenReturn(false);
        distroClientDataProcessor.onEvent(new ClientEvent.ClientVerifyFailedEvent(CLIENT_ID, MOCK_TARGET_SERVER));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientVerifyFailedEventSuccess() {
        distroClientDataProcessor.onEvent(new ClientEvent.ClientVerifyFailedEvent(CLIENT_ID, MOCK_TARGET_SERVER));
        verify(distroProtocol).syncToTarget(any(), eq(DataOperation.ADD), eq(MOCK_TARGET_SERVER), eq(0L));
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientChangedEventWithoutClient() {
        distroClientDataProcessor.onEvent(new ClientEvent.ClientChangedEvent(null));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientChangedEventWithPersistentClient() {
        client = mock(Client.class);
        when(client.isEphemeral()).thenReturn(false);
        distroClientDataProcessor.onEvent(new ClientEvent.ClientChangedEvent(client));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientChangedEventWithoutResponsible() {
        when(clientManager.isResponsibleClient(client)).thenReturn(false);
        distroClientDataProcessor.onEvent(new ClientEvent.ClientChangedEvent(client));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol, never()).sync(any(), any());
    }
    
    @Test
    public void testOnClientChangedEventSuccess() {
        distroClientDataProcessor.onEvent(new ClientEvent.ClientChangedEvent(client));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol).sync(any(), eq(DataOperation.CHANGE));
    }
    
    @Test
    public void testOnClientDisconnectEventSuccess() {
        distroClientDataProcessor.onEvent(new ClientEvent.ClientDisconnectEvent(client, true));
        verify(distroProtocol, never()).syncToTarget(any(), any(), anyString(), anyLong());
        verify(distroProtocol).sync(any(), eq(DataOperation.DELETE));
    }
    
    @Test
    public void testProcessDataForDeleteClient() {
        distroData.setType(DataOperation.DELETE);
        distroClientDataProcessor.processData(distroData);
        verify(clientManager).clientDisconnected(CLIENT_ID);
    }
    
    @Test
    public void testProcessDataForChangeClient() {
        distroData.setType(DataOperation.CHANGE);
        assertEquals(0L, client.getRevision());
        assertEquals(0, client.getAllPublishedService().size());
        distroClientDataProcessor.processData(distroData);
        verify(clientManager).syncClientConnected(CLIENT_ID, clientSyncData.getAttributes());
        assertEquals(1L, client.getRevision());
        assertEquals(1, client.getAllPublishedService().size());
    }

    @Test
    public void testProcessDataForBatch() {
        // swap tmp
        Serializer mock = Mockito.mock(Serializer.class);
        when(applicationContext.getBean(Serializer.class)).thenReturn(mock);

        // single instance => batch instances => batch instances => single instance
        // single
        ClientSyncData syncData = createSingleForBatchTest(1);
        DistroData data = new DistroData();
        data.setContent(serializer.serialize(syncData));
        data.setType(DataOperation.ADD);
        when(mock.deserialize(any(), eq(ClientSyncData.class))).thenReturn(syncData);
        distroClientDataProcessor.processData(data);
        assertEquals(1L, client.getRevision());
        assertEquals(1, client.getAllPublishedService().size());
        Service service = Service.newService("batchData", "batchData", "batchData");
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        InstancePublishInfo info = client.getInstancePublishInfo(ServiceManager.getInstance().getSingleton(singleton));
        assertEquals(info.getIp(), "127.0.0.1");
        assertEquals(info.getPort(), 8080);

        // batch
        data = new DistroData();
        syncData = createBatchForBatchTest(2);
        data.setContent(serializer.serialize(syncData));
        data.setType(DataOperation.CHANGE);
        when(mock.deserialize(any(), eq(ClientSyncData.class))).thenReturn(syncData);
        distroClientDataProcessor.processData(data);
        assertEquals(2L, client.getRevision());
        assertEquals(1, client.getAllPublishedService().size());
        info = client.getInstancePublishInfo(ServiceManager.getInstance().getSingleton(singleton));
        assertTrue(info instanceof BatchInstancePublishInfo);
        BatchInstancePublishInfo batchInfo = (BatchInstancePublishInfo) info;
        assertEquals(batchInfo.getInstancePublishInfos().size(), 2);
        for (InstancePublishInfo instancePublishInfo : batchInfo.getInstancePublishInfos()) {
            assertEquals(instancePublishInfo.getIp(), "127.0.0.1");
            assertTrue(instancePublishInfo.getPort() == 8080 || instancePublishInfo.getPort() == 8081);
        }

        // batch
        data = new DistroData();
        syncData = createBatchForBatchTest(3);
        data.setContent(serializer.serialize(syncData));
        data.setType(DataOperation.CHANGE);
        when(mock.deserialize(any(), eq(ClientSyncData.class))).thenReturn(syncData);
        distroClientDataProcessor.processData(data);
        assertEquals(3L, client.getRevision());
        assertEquals(1, client.getAllPublishedService().size());
        info = client.getInstancePublishInfo(ServiceManager.getInstance().getSingleton(singleton));
        assertTrue(info instanceof BatchInstancePublishInfo);
        batchInfo = (BatchInstancePublishInfo) info;
        assertEquals(batchInfo.getInstancePublishInfos().size(), 2);
        for (InstancePublishInfo instancePublishInfo : batchInfo.getInstancePublishInfos()) {
            assertEquals(instancePublishInfo.getIp(), "127.0.0.1");
            assertTrue(instancePublishInfo.getPort() == 8080 || instancePublishInfo.getPort() == 8081);
        }

        // single
        syncData = createSingleForBatchTest(4);
        data = new DistroData();
        data.setContent(serializer.serialize(syncData));
        data.setType(DataOperation.ADD);
        when(mock.deserialize(any(), eq(ClientSyncData.class))).thenReturn(syncData);
        distroClientDataProcessor.processData(data);
        assertEquals(4L, client.getRevision());
        assertEquals(1, client.getAllPublishedService().size());
        info = client.getInstancePublishInfo(ServiceManager.getInstance().getSingleton(singleton));
        assertEquals(info.getIp(), "127.0.0.1");
        assertEquals(info.getPort(), 8080);
    }

    private ClientSyncData createSingleForBatchTest(int revision) {
        ClientSyncData syncData = new ClientSyncData();
        syncData.setClientId(CLIENT_ID);
        ClientAttributes clientAttributes = new ClientAttributes();
        clientAttributes.addClientAttribute(ClientConstants.REVISION, revision);
        syncData.setAttributes(clientAttributes);
        syncData.setNamespaces(Collections.singletonList("batchData"));
        syncData.setGroupNames(Collections.singletonList("batchData"));
        syncData.setServiceNames(Collections.singletonList("batchData"));
        syncData.setInstancePublishInfos(Collections.singletonList(new InstancePublishInfo("127.0.0.1", 8080)));
        return syncData;
    }

    private ClientSyncData createBatchForBatchTest(int revision) {
        ClientSyncData syncData = new ClientSyncData();
        syncData.setClientId(CLIENT_ID);
        ClientAttributes clientAttributes = new ClientAttributes();
        clientAttributes.addClientAttribute(ClientConstants.REVISION, revision);
        syncData.setAttributes(clientAttributes);
        syncData.setNamespaces(Collections.emptyList());
        BatchInstancePublishInfo batchInstancePublishInfo = new BatchInstancePublishInfo();
        syncData.setBatchInstanceData(new BatchInstanceData(Collections.singletonList("batchData"),
                Collections.singletonList("batchData"),
                Collections.singletonList("batchData"),
                Collections.singletonList(batchInstancePublishInfo)));
        batchInstancePublishInfo.setInstancePublishInfos(
                Arrays.asList(new InstancePublishInfo("127.0.0.1", 8080),
                        new InstancePublishInfo("127.0.0.1", 8081)));
        return syncData;
    }
    
    @Test
    public void testProcessVerifyData() {
        DistroClientVerifyInfo verifyInfo = new DistroClientVerifyInfo(CLIENT_ID, 0L);
        when(serializer.deserialize(any(), eq(DistroClientVerifyInfo.class))).thenReturn(verifyInfo);
        assertFalse(distroClientDataProcessor.processVerifyData(distroData, MOCK_TARGET_SERVER));
        when(clientManager.verifyClient(verifyInfo)).thenReturn(true);
        assertTrue(distroClientDataProcessor.processVerifyData(distroData, MOCK_TARGET_SERVER));
    }
    
    @Test
    public void testProcessSnapshot() {
        ClientSyncDatumSnapshot snapshot = new ClientSyncDatumSnapshot();
        snapshot.setClientSyncDataList(Collections.singletonList(clientSyncData));
        when(serializer.deserialize(any(), eq(ClientSyncDatumSnapshot.class))).thenReturn(snapshot);
        assertEquals(0L, client.getRevision());
        assertEquals(0, client.getAllPublishedService().size());
        distroClientDataProcessor.processSnapshot(distroData);
        verify(clientManager).syncClientConnected(CLIENT_ID, clientSyncData.getAttributes());
        assertEquals(1L, client.getRevision());
        assertEquals(1, client.getAllPublishedService().size());
    }
    
    @Test
    public void testGetDistroData() {
        DistroData actual = distroClientDataProcessor.getDistroData(distroKey);
        assertEquals(distroKey, actual.getDistroKey());
    }
    
    @Test
    public void testGetDatumSnapshot() {
        when(clientManager.allClientId()).thenReturn(Collections.singletonList(CLIENT_ID));
        DistroData actual = distroClientDataProcessor.getDatumSnapshot();
        assertEquals(DataOperation.SNAPSHOT.name(), actual.getDistroKey().getResourceKey());
        assertEquals(DistroClientDataProcessor.TYPE, actual.getDistroKey().getResourceType());
    }
    
    @Test
    public void testGetVerifyData() {
        client.setRevision(10L);
        when(clientManager.allClientId()).thenReturn(Collections.singletonList(CLIENT_ID));
        List<DistroData> list = distroClientDataProcessor.getVerifyData();
        assertEquals(1, list.size());
        assertEquals(DataOperation.VERIFY, list.iterator().next().getType());
        assertEquals(CLIENT_ID, list.iterator().next().getDistroKey().getResourceKey());
        assertEquals(DistroClientDataProcessor.TYPE, list.iterator().next().getDistroKey().getResourceType());
    }
}