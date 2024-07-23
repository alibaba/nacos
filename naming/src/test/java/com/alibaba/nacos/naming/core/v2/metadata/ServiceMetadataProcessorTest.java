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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServiceMetadataProcessorTest {
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private CPProtocol cpProtocol;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    private ServiceMetadataProcessor serviceMetadataProcessor;
    
    @BeforeEach
    void setUp() throws Exception {
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        ApplicationUtils.injectContext(context);
        
        serviceMetadataProcessor = new ServiceMetadataProcessor(namingMetadataManager, protocolManager, serviceStorage);
    }
    
    @Test
    void testLoadSnapshotOperate() {
        List<SnapshotOperation> snapshotOperations = serviceMetadataProcessor.loadSnapshotOperate();
        
        assertNotNull(snapshotOperations);
        assertEquals(1, snapshotOperations.size());
    }
    
    @Test
    void testOnRequest() {
        Response response = serviceMetadataProcessor.onRequest(ReadRequest.getDefaultInstance());
        
        assertNull(response);
    }
    
    @Test
    void testOnApply() throws NoSuchFieldException, IllegalAccessException {
        WriteRequest defaultInstance = WriteRequest.getDefaultInstance();
        Class<WriteRequest> writeRequestClass = WriteRequest.class;
        Field operation = writeRequestClass.getDeclaredField("operation_");
        operation.setAccessible(true);
        operation.set(defaultInstance, "ADD");
        
        MetadataOperation<ServiceMetadata> metadataOperation = new MetadataOperation<>();
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        metadataOperation.setMetadata(serviceMetadata);
        metadataOperation.setServiceName("nacos");
        metadataOperation.setNamespace("namespace");
        metadataOperation.setGroup("group");
        Serializer aDefault = SerializeFactory.getDefault();
        ByteString bytes = ByteString.copyFrom(aDefault.serialize(metadataOperation));
        
        Field data = writeRequestClass.getDeclaredField("data_");
        data.setAccessible(true);
        data.set(defaultInstance, bytes);
        
        // ADD
        Response addResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        Service service = Service.newService(metadataOperation.getNamespace(), metadataOperation.getGroup(),
                metadataOperation.getServiceName(), metadataOperation.getMetadata().isEphemeral());
        Service singleton = ServiceManager.getInstance().getSingleton(service);
        namingMetadataManager.updateServiceMetadata(singleton, metadataOperation.getMetadata());
        
        assertTrue(addResponse.getSuccess());
        verify(namingMetadataManager).getServiceMetadata(service);
        verify(namingMetadataManager).updateServiceMetadata(service, serviceMetadata);
        
        // CHANGE
        operation.set(defaultInstance, "CHANGE");
        Response changeResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        assertTrue(changeResponse.getSuccess());
        verify(namingMetadataManager, times(2)).getServiceMetadata(service);
        verify(namingMetadataManager).updateServiceMetadata(service, serviceMetadata);
        
        // DELETE
        operation.set(defaultInstance, "DELETE");
        Response deleteResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        assertTrue(deleteResponse.getSuccess());
        verify(namingMetadataManager).removeServiceMetadata(service);
        verify(serviceStorage).removeData(service);
        
        // VERIFY
        operation.set(defaultInstance, "VERIFY");
        Response otherResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        assertFalse(otherResponse.getSuccess());
    }
    
    @Test
    void testGroup() {
        String group = serviceMetadataProcessor.group();
        
        assertEquals(Constants.SERVICE_METADATA, group);
    }
}
