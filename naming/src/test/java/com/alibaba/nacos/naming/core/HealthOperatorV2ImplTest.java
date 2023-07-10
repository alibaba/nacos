/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationServiceProxy;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link HealthOperatorV2Impl} unit tests.
 *
 * @author chenglu
 * @date 2021-08-03 22:31
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthOperatorV2ImplTest {
    
    @InjectMocks
    private HealthOperatorV2Impl healthOperatorV2;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ClientManagerDelegate clientManager;
    
    @Mock
    private ClientOperationServiceProxy clientOperationService;
    
    @Test
    public void testUpdateHealthStatusForPersistentInstance() {
        try {
            ServiceMetadata metadata = new ServiceMetadata();
            Map<String, ClusterMetadata> clusterMap = new HashMap<>(2);
            ClusterMetadata cluster = Mockito.mock(ClusterMetadata.class);
            clusterMap.put("C", cluster);
            metadata.setClusters(clusterMap);
            Instance instance = new Instance();
            instance.setIp("1.1.1.1");
            instance.setPort(8080);
            Mockito.when(cluster.getHealthyCheckType()).thenReturn(HealthCheckType.NONE.name());
            Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(metadata));
            
            ConnectionBasedClient client = Mockito.mock(ConnectionBasedClient.class);
            Mockito.when(clientManager.getClient(Mockito.anyString())).thenReturn(client);
            
            InstancePublishInfo instancePublishInfo = new InstancePublishInfo();
            instancePublishInfo.setExtendDatum(new HashMap<>(2));
            Mockito.when(client.getInstancePublishInfo(Mockito.any())).thenReturn(instancePublishInfo);
            
            healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C", "1.1.1.1", 8080, true);
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
}
