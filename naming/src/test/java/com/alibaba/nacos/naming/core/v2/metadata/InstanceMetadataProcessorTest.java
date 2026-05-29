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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InstanceMetadataProcessorTest {
    
    private static final String NAMESPACE = "namespace";
    
    private static final String GROUP = "group";
    
    private static final String SERVICE_NAME = "service";
    
    private static final String INSTANCE_TAG = "1.1.1.1:8848:DEFAULT";
    
    private static final Service SERVICE = Service.newService(NAMESPACE, GROUP, SERVICE_NAME);
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    private InstanceMetadataProcessor instanceMetadataProcessor;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    @BeforeEach
    void setUp() {
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        
        instanceMetadataProcessor =
            new InstanceMetadataProcessor(namingMetadataManager, protocolManager);
    }
    
    @AfterEach
    void tearDown() {
        ServiceManager.getInstance().removeSingleton(SERVICE);
    }
    
    @Test
    void testConstructorRegistersProcessor() {
        ArgumentCaptor<Collection<RequestProcessor4CP>> captor =
            ArgumentCaptor.forClass(Collection.class);
        
        verify(cpProtocol).addRequestProcessors(captor.capture());
        Collection<RequestProcessor4CP> processors = captor.getValue();
        
        assertEquals(1, processors.size());
        assertSame(instanceMetadataProcessor, processors.iterator().next());
    }
    
    @Test
    void testLoadSnapshotOperate() {
        List<SnapshotOperation> snapshotOperations =
            instanceMetadataProcessor.loadSnapshotOperate();
        
        assertNotNull(snapshotOperations);
        assertEquals(1, snapshotOperations.size());
    }
    
    @Test
    void testOnRequest() {
        Response response = instanceMetadataProcessor.onRequest(ReadRequest.getDefaultInstance());
        
        assertNull(response);
    }
    
    @Test
    void testOnApplyAddAndChangeUpdatesInstanceMetadata() {
        MetadataOperation<InstanceMetadata> metadataOperation = buildMetadataOperation();
        
        Response addResponse =
            instanceMetadataProcessor
                .onApply(buildWriteRequest(DataOperation.ADD, metadataOperation));
        Response changeResponse =
            instanceMetadataProcessor
                .onApply(buildWriteRequest(DataOperation.CHANGE, metadataOperation));
        
        Service singleton = ServiceManager.getInstance().getSingleton(SERVICE);
        assertTrue(addResponse.getSuccess());
        assertTrue(changeResponse.getSuccess());
        verify(namingMetadataManager, times(2))
            .updateInstanceMetadata(Mockito.eq(singleton), Mockito.eq(INSTANCE_TAG),
                Mockito.any(InstanceMetadata.class));
    }
    
    @Test
    void testOnApplyDeleteRemovesInstanceMetadata() {
        MetadataOperation<InstanceMetadata> metadataOperation = buildMetadataOperation();
        
        Response response =
            instanceMetadataProcessor
                .onApply(buildWriteRequest(DataOperation.DELETE, metadataOperation));
        
        Service singleton = ServiceManager.getInstance().getSingleton(SERVICE);
        assertTrue(response.getSuccess());
        verify(namingMetadataManager).removeInstanceMetadata(singleton, INSTANCE_TAG);
    }
    
    @Test
    void testOnApplyUnsupportedOperationReturnsFailedResponse() {
        Response response =
            instanceMetadataProcessor
                .onApply(buildWriteRequest(DataOperation.VERIFY, buildMetadataOperation()));
        
        assertFalse(response.getSuccess());
        assertEquals("Unsupported operation VERIFY", response.getErrMsg());
    }
    
    @Test
    void testOnApplyDeserializeFailureReturnsFailedResponse() {
        WriteRequest request = WriteRequest.newBuilder()
            .setOperation(DataOperation.ADD.name()).setData(ByteString.copyFromUtf8("invalid"))
            .build();
        
        Response response = instanceMetadataProcessor.onApply(request);
        
        assertFalse(response.getSuccess());
        assertNotNull(response.getErrMsg());
    }
    
    @Test
    void testGroup() {
        String group = instanceMetadataProcessor.group();
        
        assertEquals(Constants.INSTANCE_METADATA, group);
    }
    
    private MetadataOperation<InstanceMetadata> buildMetadataOperation() {
        MetadataOperation<InstanceMetadata> result = new MetadataOperation<>();
        result.setNamespace(NAMESPACE);
        result.setGroup(GROUP);
        result.setServiceName(SERVICE_NAME);
        result.setTag(INSTANCE_TAG);
        result.setMetadata(new InstanceMetadata());
        return result;
    }
    
    private WriteRequest buildWriteRequest(DataOperation operation,
        MetadataOperation<InstanceMetadata> metadataOperation) {
        return WriteRequest.newBuilder().setOperation(operation.name())
            .setData(ByteString.copyFrom(serializer.serialize(metadataOperation))).build();
    }
}
