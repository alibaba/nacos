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
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteEventListener;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServiceMetadataProcessorTest {
    
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
    
    @Mock
    private DoubleWriteEventListener doubleWriteEventListener;
    
    private ServiceMetadataProcessor serviceMetadataProcessor;
    
    @Before
    public void setUp() throws Exception {
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        ApplicationUtils.injectContext(context);
        when(context.getBean(DoubleWriteEventListener.class)).thenReturn(doubleWriteEventListener);
        
        serviceMetadataProcessor = new ServiceMetadataProcessor(namingMetadataManager, protocolManager, serviceStorage);
    }
    
    @Test
    public void testLoadSnapshotOperate() {
        List<SnapshotOperation> snapshotOperations = serviceMetadataProcessor.loadSnapshotOperate();
        
        Assert.assertNotNull(snapshotOperations);
        Assert.assertEquals(snapshotOperations.size(), 1);
    }
    
    @Test
    public void testOnRequest() {
        Response response = serviceMetadataProcessor.onRequest(ReadRequest.getDefaultInstance());
        
        Assert.assertNull(response);
    }
    
    @Test
    public void testOnApply() throws NoSuchFieldException, IllegalAccessException {
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
        
        Assert.assertTrue(addResponse.getSuccess());
        verify(namingMetadataManager).getServiceMetadata(service);
        verify(namingMetadataManager).updateServiceMetadata(service, serviceMetadata);
        verify(context).getBean(DoubleWriteEventListener.class);
        
        // CHANGE
        operation.set(defaultInstance, "CHANGE");
        Response changeResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        Assert.assertTrue(changeResponse.getSuccess());
        verify(namingMetadataManager, times(2)).getServiceMetadata(service);
        verify(namingMetadataManager).updateServiceMetadata(service, serviceMetadata);
        verify(context, times(2)).getBean(DoubleWriteEventListener.class);
        
        // DELETE
        operation.set(defaultInstance, "DELETE");
        Response deleteResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        Assert.assertTrue(deleteResponse.getSuccess());
        verify(namingMetadataManager).removeServiceMetadata(service);
        verify(serviceStorage).removeData(service);
        
        // VERIFY
        operation.set(defaultInstance, "VERIFY");
        Response otherResponse = serviceMetadataProcessor.onApply(defaultInstance);
        
        Assert.assertFalse(otherResponse.getSuccess());
    }
    
    @Test
    public void testGroup() {
        String group = serviceMetadataProcessor.group();
        
        Assert.assertEquals(group, Constants.SERVICE_METADATA);
    }
}