/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NamingMetadataOperateServiceTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    @Mock
    private Service service;
    
    private NamingMetadataOperateService namingMetadataOperateService;
    
    @BeforeEach
    void testSetUp() throws Exception {
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        
        namingMetadataOperateService = new NamingMetadataOperateService(protocolManager);
    }
    
    @Test
    void testUpdateServiceMetadata() {
        assertThrows(NacosRuntimeException.class, () -> {
            ServiceMetadata serviceMetadata = new ServiceMetadata();
            namingMetadataOperateService.updateServiceMetadata(service, serviceMetadata);
            
            Mockito.verify(service).getNamespace();
            Mockito.verify(service).getGroup();
            Mockito.verify(service).getName();
        });
    }
    
    @Test
    void testDeleteServiceMetadata() {
        assertThrows(NacosRuntimeException.class, () -> {
            namingMetadataOperateService.deleteServiceMetadata(service);
            
            Mockito.verify(service).getNamespace();
            Mockito.verify(service).getGroup();
            Mockito.verify(service).getName();
        });
    }
    
    @Test
    void testUpdateInstanceMetadata() {
        assertThrows(NacosRuntimeException.class, () -> {
            String metadataId = "metadataId";
            InstanceMetadata instanceMetadata = new InstanceMetadata();
            namingMetadataOperateService.updateInstanceMetadata(service, metadataId,
                instanceMetadata);
            
            Mockito.verify(service).getNamespace();
            Mockito.verify(service).getGroup();
            Mockito.verify(service).getName();
        });
    }
    
    @Test
    void testDeleteInstanceMetadata() {
        assertThrows(NacosRuntimeException.class, () -> {
            String metadataId = "metadataId";
            namingMetadataOperateService.deleteInstanceMetadata(service, metadataId);
            
            Mockito.verify(service).getNamespace();
            Mockito.verify(service).getGroup();
            Mockito.verify(service).getName();
        });
    }
    
    @Test
    void testAddClusterMetadata() {
        assertThrows(NacosRuntimeException.class, () -> {
            String clusterName = "clusterName";
            ClusterMetadata clusterMetadata = new ClusterMetadata();
            namingMetadataOperateService.addClusterMetadata(service, clusterName, clusterMetadata);
            
            Mockito.verify(service).getNamespace();
            Mockito.verify(service).getGroup();
            Mockito.verify(service).getName();
        });
    }
    
    @Test
    void testSubmitMetadataOperationsSuccessfully() throws Exception {
        Mockito.when(cpProtocol.write(any(WriteRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(true).build());
        Service realService = Service.newService("namespace", "group", "service", false);
        
        namingMetadataOperateService.updateServiceMetadata(realService, new ServiceMetadata());
        namingMetadataOperateService.deleteServiceMetadata(realService);
        namingMetadataOperateService.updateInstanceMetadata(realService, "metadataId",
            new InstanceMetadata());
        namingMetadataOperateService.deleteInstanceMetadata(realService, "metadataId");
        namingMetadataOperateService.addClusterMetadata(realService, "cluster",
            new ClusterMetadata());
        
        ArgumentCaptor<WriteRequest> requestCaptor = ArgumentCaptor.forClass(WriteRequest.class);
        verify(cpProtocol, times(5)).write(requestCaptor.capture());
        List<WriteRequest> requests = requestCaptor.getAllValues();
        assertEquals(
            Arrays.asList(Constants.SERVICE_METADATA, Constants.SERVICE_METADATA,
                Constants.INSTANCE_METADATA, Constants.INSTANCE_METADATA,
                Constants.SERVICE_METADATA),
            Arrays.asList(requests.get(0).getGroup(), requests.get(1).getGroup(),
                requests.get(2).getGroup(), requests.get(3).getGroup(),
                requests.get(4).getGroup()));
        assertEquals(
            Arrays.asList(DataOperation.CHANGE.name(), DataOperation.DELETE.name(),
                DataOperation.CHANGE.name(), DataOperation.DELETE.name(),
                DataOperation.ADD.name()),
            Arrays.asList(requests.get(0).getOperation(), requests.get(1).getOperation(),
                requests.get(2).getOperation(), requests.get(3).getOperation(),
                requests.get(4).getOperation()));
    }
    
    @Test
    void testSubmitMetadataOperationWithFailedResponse() throws Exception {
        Mockito.when(cpProtocol.write(any(WriteRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(false).setErrMsg("raft failed").build());
        Service realService = Service.newService("namespace", "group", "service");
        
        NacosRuntimeException actual = assertThrows(NacosRuntimeException.class,
            () -> namingMetadataOperateService.updateServiceMetadata(realService,
                new ServiceMetadata()));
        
        assertTrue(actual.getMessage().contains("do metadata operation failed"));
    }
}
