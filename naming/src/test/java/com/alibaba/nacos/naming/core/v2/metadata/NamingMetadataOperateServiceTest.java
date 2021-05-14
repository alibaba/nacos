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
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NamingMetadataOperateServiceTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    @Mock
    private Service service;
    
    private NamingMetadataOperateService namingMetadataOperateService;
    
    @Before
    public void testSetUp() throws Exception {
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        
        namingMetadataOperateService = new NamingMetadataOperateService(protocolManager);
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testUpdateServiceMetadata() {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        namingMetadataOperateService.updateServiceMetadata(service, serviceMetadata);
        
        Mockito.verify(service).getNamespace();
        Mockito.verify(service).getGroup();
        Mockito.verify(service).getName();
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testDeleteServiceMetadata() {
        namingMetadataOperateService.deleteServiceMetadata(service);
        
        Mockito.verify(service).getNamespace();
        Mockito.verify(service).getGroup();
        Mockito.verify(service).getName();
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testUpdateInstanceMetadata() {
        String metadataId = "metadataId";
        InstanceMetadata instanceMetadata = new InstanceMetadata();
        namingMetadataOperateService.updateInstanceMetadata(service, metadataId, instanceMetadata);
        
        Mockito.verify(service).getNamespace();
        Mockito.verify(service).getGroup();
        Mockito.verify(service).getName();
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testDeleteInstanceMetadata() {
        String metadataId = "metadataId";
        namingMetadataOperateService.deleteInstanceMetadata(service, metadataId);
        
        Mockito.verify(service).getNamespace();
        Mockito.verify(service).getGroup();
        Mockito.verify(service).getName();
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testAddClusterMetadata() {
        String clusterName = "clusterName";
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        namingMetadataOperateService.addClusterMetadata(service, clusterName, clusterMetadata);
        
        Mockito.verify(service).getNamespace();
        Mockito.verify(service).getGroup();
        Mockito.verify(service).getName();
    }
}