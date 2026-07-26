/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.service.impl;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncData;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class PersistentClientOperationServiceImplTest {
    
    private final String clientId = "1.1.1.1:80#false";
    
    private PersistentClientOperationServiceImpl persistentClientOperationServiceImpl;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private PersistentIpPortClientManager clientManager;
    
    @Mock
    private Service service;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private Instance instance;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    @Mock
    private Serializer serializer;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @BeforeEach
    void setUp() throws Exception {
        when(service.getNamespace()).thenReturn("n");
        when(applicationContext.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(serializer
            .serialize(any(PersistentClientOperationServiceImpl.InstanceStoreRequest.class)))
            .thenReturn(new byte[1]);
        ApplicationUtils.injectContext(applicationContext);
        Field serializerField =
            PersistentClientOperationServiceImpl.class.getDeclaredField("serializer");
        serializerField.setAccessible(true);
        persistentClientOperationServiceImpl =
            new PersistentClientOperationServiceImpl(clientManager);
        serializerField.set(persistentClientOperationServiceImpl, serializer);
        
    }
    
    @AfterEach
    void tearDown() {
        ApplicationUtils.injectContext(null);
        ServiceManager.getInstance().removeSingleton(Service.newService("A", "B", "C"));
        removeSnapshotSingleton("changed");
        removeSnapshotSingleton("added");
        removeSnapshotSingleton("removed");
        removeSnapshotSingleton("dead");
    }
    
    @Test
    void testRegisterPersistentInstance() {
        assertThrows(NacosRuntimeException.class, () -> {
            when(service.isEphemeral()).thenReturn(true);
            persistentClientOperationServiceImpl.registerInstance(service, instance, clientId);
        });
    }
    
    @Test
    void testRegisterAndDeregisterInstance() throws Exception {
        Field clientManagerField =
            PersistentClientOperationServiceImpl.class.getDeclaredField("clientManager");
        clientManagerField.setAccessible(true);
        // Test register instance
        persistentClientOperationServiceImpl.registerInstance(service, instance, clientId);
        verify(cpProtocol).write(any(WriteRequest.class));
        // Test deregister instance
        persistentClientOperationServiceImpl.deregisterInstance(service, instance, clientId);
        verify(cpProtocol, times(2)).write(any(WriteRequest.class));
    }
    
    @Test
    void testDeregisterInstanceWrapsProtocolException() throws Exception {
        Mockito.doThrow(new Exception("write failed")).when(cpProtocol)
            .write(any(WriteRequest.class));
        
        assertThrows(NacosRuntimeException.class,
            () -> persistentClientOperationServiceImpl.deregisterInstance(service, instance,
                clientId));
    }
    
    @Test
    void testRegisterInstanceWrapsProtocolException() throws Exception {
        Mockito.doThrow(new Exception("write failed")).when(cpProtocol)
            .write(any(WriteRequest.class));
        
        assertThrows(NacosRuntimeException.class,
            () -> persistentClientOperationServiceImpl.registerInstance(service, instance,
                clientId));
    }
    
    @Test
    void updateInstance() throws Exception {
        Field clientManagerField =
            PersistentClientOperationServiceImpl.class.getDeclaredField("clientManager");
        clientManagerField.setAccessible(true);
        // Test register instance
        persistentClientOperationServiceImpl.updateInstance(service, instance, clientId);
        verify(cpProtocol).write(any(WriteRequest.class));
    }
    
    @Test
    void testUpdateEphemeralInstanceThrowsException() {
        when(service.isEphemeral()).thenReturn(true);
        
        assertThrows(NacosRuntimeException.class,
            () -> persistentClientOperationServiceImpl.updateInstance(service, instance,
                clientId));
    }
    
    @Test
    void testUpdateInstanceWrapsProtocolException() throws Exception {
        Mockito.doThrow(new Exception("write failed")).when(cpProtocol)
            .write(any(WriteRequest.class));
        
        assertThrows(NacosRuntimeException.class,
            () -> persistentClientOperationServiceImpl.updateInstance(service, instance,
                clientId));
    }
    
    @Test
    void testBatchRegisterInstanceNoop() {
        persistentClientOperationServiceImpl.batchRegisterInstance(service,
            Collections.singletonList(instance), clientId);
    }
    
    @Test
    void testInstanceStoreRequestAccessors() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        
        request.setService(service);
        request.setInstance(instance);
        request.setClientId(clientId);
        
        assertSame(service, request.getService());
        assertSame(instance, request.getInstance());
        assertSame(clientId, request.getClientId());
    }
    
    @Test
    void testSubscribeService() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentClientOperationServiceImpl.subscribeService(service, subscriber, clientId);
        });
    }
    
    @Test
    void testUnsubscribeService() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentClientOperationServiceImpl.unsubscribeService(service, subscriber, clientId);
        });
    }
    
    @Test
    void testOnRequest() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentClientOperationServiceImpl.onRequest(ReadRequest.newBuilder().build());
        });
    }
    
    @Test
    void testOnApply() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId("xxxx");
        request.setInstance(new Instance());
        
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        
        Mockito.when(clientManager.contains(Mockito.anyString())).thenReturn(true);
        
        IpPortBasedClient ipPortBasedClient = Mockito.mock(IpPortBasedClient.class);
        Mockito.when(clientManager.getClient(Mockito.anyString())).thenReturn(ipPortBasedClient);
        
        WriteRequest writeRequest =
            WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build();
        Response response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        assertTrue(ServiceManager.getInstance().containSingleton(service1));
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.DELETE.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        ServiceManager.getInstance().removeSingleton(service1);
        
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.VERIFY.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertFalse(response.getSuccess());
        
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.CHANGE.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        assertFalse(ServiceManager.getInstance().containSingleton(service1));
    }
    
    @Test
    void testOnApplyChange() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId("xxxx");
        request.setInstance(new Instance());
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        Mockito.when(clientManager.contains(Mockito.anyString())).thenReturn(true);
        when(clientManager.getClient(Mockito.anyString())).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getAllPublishedService())
            .thenReturn(Collections.singletonList(service1));
        WriteRequest writeRequest =
            WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build();
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build();
        Response response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.CHANGE.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        assertTrue(ServiceManager.getInstance().containSingleton(service1));
    }
    
    @Test
    void testOnApplyReturnsFailureWhenDeserializeThrowsException() {
        Mockito.when(serializer.deserialize(Mockito.any()))
            .thenThrow(new RuntimeException("deserialize failed"));
        
        Response response = persistentClientOperationServiceImpl.onApply(
            WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build());
        
        assertFalse(response.getSuccess());
    }
    
    @Test
    void testOnApplyRegisterConnectsMissingClient() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId(clientId);
        request.setInstance(new Instance());
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        Mockito.when(clientManager.contains(clientId)).thenReturn(false);
        Mockito.when(clientManager.getClient(clientId)).thenReturn(ipPortBasedClient);
        
        Response response = persistentClientOperationServiceImpl.onApply(
            WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build());
        
        assertTrue(response.getSuccess());
        verify(clientManager).clientConnected(eq(clientId), any(ClientAttributes.class));
    }
    
    @Test
    void testOnApplyDeregisterReturnsWhenClientMissing() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId(clientId);
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        Mockito.when(clientManager.getClient(clientId)).thenReturn(null);
        
        Response response = persistentClientOperationServiceImpl.onApply(
            WriteRequest.newBuilder().setOperation(DataOperation.DELETE.name()).build());
        
        assertTrue(response.getSuccess());
        verify(clientManager, times(0)).clientDisconnected(clientId);
    }
    
    @Test
    void testOnApplyDeregisterDisconnectsEmptyClient() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId(clientId);
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        Mockito.when(clientManager.getClient(clientId)).thenReturn(ipPortBasedClient);
        Mockito.when(ipPortBasedClient.getAllPublishedService())
            .thenReturn(Collections.emptyList());
        
        Response response = persistentClientOperationServiceImpl.onApply(
            WriteRequest.newBuilder().setOperation(DataOperation.DELETE.name()).build());
        
        assertTrue(response.getSuccess());
        verify(clientManager).clientDisconnected(clientId);
    }
    
    @Test
    void testOnApplyDeregisterPublishesRemovedInstanceMetadata() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request =
            new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        InstancePublishInfo removedInstance = instanceInfo("10.0.0.7", 8848);
        request.setService(service1);
        request.setClientId(clientId);
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        Mockito.when(clientManager.getClient(clientId)).thenReturn(ipPortBasedClient);
        Mockito.when(ipPortBasedClient.removeServiceInstance(any(Service.class)))
            .thenReturn(removedInstance);
        Mockito.when(ipPortBasedClient.getAllPublishedService())
            .thenReturn(Collections.singleton(service1));
        
        Response response = persistentClientOperationServiceImpl.onApply(
            WriteRequest.newBuilder().setOperation(DataOperation.DELETE.name()).build());
        
        assertTrue(response.getSuccess());
        verify(clientManager, times(0)).clientDisconnected(clientId);
    }
    
    @Test
    void testPersistentInstanceSnapshotOperationWriteAndReadSnapshot(@TempDir Path snapshotDir) {
        SnapshotOperation snapshotOperation =
            persistentClientOperationServiceImpl.loadSnapshotOperate().iterator().next();
        byte[] snapshotBytes = new byte[] {1, 2, 3};
        IpPortBasedClient dumpClient = Mockito.mock(IpPortBasedClient.class);
        ClientSyncData dumpData = buildSyncData("alive-client", "changed",
            instanceInfo("10.0.0.1", 8848));
        Map<String, IpPortBasedClient> clients = new HashMap<>();
        clients.put("alive-client", dumpClient);
        when(clientManager.showClients()).thenReturn(clients);
        when(dumpClient.generateSyncData()).thenReturn(dumpData);
        when(serializer.serialize(any(ConcurrentHashMap.class))).thenReturn(snapshotBytes);
        Writer writer = new Writer(snapshotDir.toString());
        
        Boolean writeResult =
            ReflectionTestUtils.invokeMethod(snapshotOperation, "writeSnapshot", writer);
        
        assertTrue(writeResult);
        assertTrue(writer.listFiles().containsKey("persistent_instance.zip"));
        
        ConcurrentHashMap<String, ClientSyncData> snapshotData = new ConcurrentHashMap<>();
        InstancePublishInfo changedInfo = instanceInfo("10.0.0.2", 8848);
        InstancePublishInfo addedInfo = instanceInfo("10.0.0.3", 8848);
        snapshotData.put("alive-client",
            buildSyncData("alive-client", new String[] {"changed", "added"},
                new InstancePublishInfo[] {changedInfo, addedInfo}));
        mockSnapshotLoadClients();
        when(serializer.deserialize(any(byte[].class))).thenReturn(snapshotData);
        Reader reader = new Reader(snapshotDir.toString(), writer.listFiles());
        
        boolean readResult = snapshotOperation.onSnapshotLoad(reader);
        
        assertTrue(readResult);
        verify(clientManager).showClients();
        verify(clientManager).removeAndRelease("dead-client");
        verify(clientManager, times(0)).removeAndRelease("missing-client");
        IpPortBasedClient aliveClient =
            (IpPortBasedClient) clientManager.getClient("alive-client");
        verify(aliveClient, times(2)).putServiceInstance(any(Service.class),
            any(InstancePublishInfo.class));
        verify(aliveClient)
            .removeServiceInstance(argThat(service -> "removed".equals(service.getName())));
        verify(aliveClient).getAllPublishedService();
    }
    
    @Test
    void testPersistentInstanceSnapshotOperationAddSyncDataAndTags() {
        SnapshotOperation snapshotOperation =
            persistentClientOperationServiceImpl.loadSnapshotOperate().iterator().next();
        IpPortBasedClient newClient = Mockito.mock(IpPortBasedClient.class);
        when(newClient.getClientId()).thenReturn("new-client");
        ClientSyncData syncData = buildSyncData("new-client", new String[] {"added", "changed"},
            new InstancePublishInfo[] {instanceInfo("10.0.0.5", 8848),
                instanceInfo("10.0.0.6", 8848)});
        Map.Entry<String, ClientSyncData> entry =
            new AbstractMap.SimpleEntry<>("new-client", syncData);
        
        ReflectionTestUtils.invokeMethod(snapshotOperation, "addSyncDataToClient", entry,
            newClient);
        ReflectionTestUtils.invokeMethod(snapshotOperation, "removeDeadClient",
            Collections.singleton("new-client"), Collections.emptyList());
        String saveTag = ReflectionTestUtils.invokeMethod(snapshotOperation, "getSnapshotSaveTag");
        String loadTag = ReflectionTestUtils.invokeMethod(snapshotOperation, "getSnapshotLoadTag");
        
        verify(newClient, times(2)).putServiceInstance(any(Service.class),
            any(InstancePublishInfo.class));
        verify(clientManager).addSyncClient(newClient);
        assertTrue(saveTag.endsWith(".SAVE"));
        assertTrue(loadTag.endsWith(".LOAD"));
    }
    
    private void mockSnapshotLoadClients() {
        IpPortBasedClient aliveClient = Mockito.mock(IpPortBasedClient.class);
        Service changedService = snapshotService("changed");
        Service removedService = snapshotService("removed");
        when(aliveClient.getAllPublishedService())
            .thenReturn(Arrays.asList(changedService, removedService));
        when(aliveClient.getInstancePublishInfo(any(Service.class)))
            .thenReturn(instanceInfo("10.0.0.9", 8848));
        when(aliveClient.getClientId()).thenReturn("alive-client");
        Client deadClient = Mockito.mock(Client.class);
        Service deadService = snapshotService("dead");
        when(deadClient.getAllPublishedService()).thenReturn(Collections.singleton(deadService));
        when(deadClient.getInstancePublishInfo(eq(deadService)))
            .thenReturn(instanceInfo("10.0.0.4", 8848));
        when(deadClient.getClientId()).thenReturn("dead-client");
        when(clientManager.allClientId())
            .thenReturn(Arrays.asList("alive-client", "dead-client", "missing-client"));
        when(clientManager.getClient("alive-client")).thenReturn(aliveClient);
        when(clientManager.getClient("dead-client")).thenReturn(deadClient);
        when(clientManager.getClient("missing-client")).thenReturn(null);
    }
    
    private ClientSyncData buildSyncData(String clientId, String serviceName,
        InstancePublishInfo instancePublishInfo) {
        return buildSyncData(clientId, new String[] {serviceName},
            new InstancePublishInfo[] {instancePublishInfo});
    }
    
    private ClientSyncData buildSyncData(String clientId, String[] serviceNames,
        InstancePublishInfo[] instancePublishInfos) {
        return new ClientSyncData(clientId, Collections.nCopies(serviceNames.length, "snapshot"),
            Collections.nCopies(serviceNames.length, "group"), Arrays.asList(serviceNames),
            Arrays.asList(instancePublishInfos), null);
    }
    
    private InstancePublishInfo instanceInfo(String ip, int port) {
        InstancePublishInfo result = new InstancePublishInfo(ip, port);
        result.setHealthy(true);
        result.setCluster("DEFAULT");
        return result;
    }
    
    private Service snapshotService(String serviceName) {
        return Service.newService("snapshot", "group", serviceName, false);
    }
    
    private void removeSnapshotSingleton(String serviceName) {
        ServiceManager.getInstance().removeSingleton(snapshotService(serviceName));
    }
}
