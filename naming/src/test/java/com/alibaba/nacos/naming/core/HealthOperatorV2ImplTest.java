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
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationServiceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link HealthOperatorV2Impl} unit tests.
 *
 * @author chenglu
 * @date 2021-08-03 22:31
 */
@ExtendWith(MockitoExtension.class)
class HealthOperatorV2ImplTest {
    
    @InjectMocks
    private HealthOperatorV2Impl healthOperatorV2;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ClientManagerDelegate clientManager;
    
    @Mock
    private ClientOperationServiceProxy clientOperationService;
    
    @Test
    void testUpdateHealthStatusForPersistentInstance() throws NacosException {
        ServiceMetadata metadata = new ServiceMetadata();
        Map<String, ClusterMetadata> clusterMap = new HashMap<>(2);
        ClusterMetadata cluster = Mockito.mock(ClusterMetadata.class);
        clusterMap.put("cluster-a", cluster);
        metadata.setClusters(clusterMap);
        when(cluster.getHealthyCheckType()).thenReturn(HealthCheckType.NONE.name());
        when(metadataManager
            .getServiceMetadata(argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(Optional.of(metadata));
        
        ConnectionBasedClient client = Mockito.mock(ConnectionBasedClient.class);
        when(clientManager.getClient(anyString())).thenReturn(client);
        
        InstancePublishInfo instancePublishInfo = new InstancePublishInfo();
        instancePublishInfo.setIp("1.1.1.1");
        instancePublishInfo.setPort(8080);
        instancePublishInfo.setHealthy(false);
        instancePublishInfo.setCluster("cluster-a");
        instancePublishInfo.setExtendDatum(new HashMap<>(2));
        when(client.getInstancePublishInfo(
            argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(instancePublishInfo);
        
        healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C", "cluster-a",
            "1.1.1.1", 8080,
            true);
        
        verify(clientOperationService).registerInstance(
            argThat(service -> isPersistentService(service, "A", "B", "C")),
            argThat(instance -> !instance.isEphemeral() && instance.isHealthy()
                && "1.1.1.1".equals(instance.getIp())
                && instance.getPort() == 8080 && "cluster-a".equals(instance.getClusterName())),
            anyString());
    }
    
    @Test
    void testUpdateHealthStatusThrowsWhenMetadataMissing() {
        when(metadataManager
            .getServiceMetadata(argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(Optional.empty());
        
        assertThrows(NacosException.class,
            () -> healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C",
                "cluster-a", "1.1.1.1", 8080, true));
    }
    
    @Test
    void testUpdateHealthStatusThrowsWhenClusterMissing() {
        ServiceMetadata metadata = new ServiceMetadata();
        when(metadataManager
            .getServiceMetadata(argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(Optional.of(metadata));
        
        assertThrows(NacosException.class,
            () -> healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C",
                "cluster-a", "1.1.1.1", 8080, true));
    }
    
    @Test
    void testUpdateHealthStatusThrowsWhenCheckerStillWorking() {
        ServiceMetadata metadata = new ServiceMetadata();
        Map<String, ClusterMetadata> clusterMap = new HashMap<>(2);
        ClusterMetadata cluster = Mockito.mock(ClusterMetadata.class);
        clusterMap.put("cluster-a", cluster);
        metadata.setClusters(clusterMap);
        when(cluster.getHealthyCheckType()).thenReturn(HealthCheckType.TCP.name());
        when(metadataManager
            .getServiceMetadata(argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(Optional.of(metadata));
        
        assertThrows(NacosException.class,
            () -> healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C",
                "cluster-a", "1.1.1.1", 8080, true));
    }
    
    @Test
    void testUpdateHealthStatusReturnsWhenClientMissing() throws NacosException {
        givenNoneHealthCheckCluster();
        when(clientManager.getClient(anyString())).thenReturn(null);
        
        healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C", "cluster-a",
            "1.1.1.1", 8080, true);
        
        verifyNoInteractions(clientOperationService);
    }
    
    @Test
    void testUpdateHealthStatusReturnsWhenInstanceMissing() throws NacosException {
        givenNoneHealthCheckCluster();
        ConnectionBasedClient client = Mockito.mock(ConnectionBasedClient.class);
        when(clientManager.getClient(anyString())).thenReturn(client);
        when(client.getInstancePublishInfo(
            argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(null);
        
        healthOperatorV2.updateHealthStatusForPersistentInstance("A", "B", "C", "cluster-a",
            "1.1.1.1", 8080, true);
        
        verifyNoInteractions(clientOperationService);
    }
    
    @Test
    void testCheckers() {
        Map<String, AbstractHealthChecker> actual = healthOperatorV2.checkers();
        
        assertFalse(actual.isEmpty());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testCheckersSkipsUnavailableChecker() throws Exception {
        Field extendField = HealthCheckType.class.getDeclaredField("EXTEND");
        extendField.setAccessible(true);
        Map<String, Class<? extends AbstractHealthChecker>> extend =
            (Map<String, Class<? extends AbstractHealthChecker>>) extendField.get(null);
        extend.put(BrokenHealthChecker.TYPE, BrokenHealthChecker.class);
        try {
            Map<String, AbstractHealthChecker> actual = healthOperatorV2.checkers();
            assertFalse(actual.containsKey(BrokenHealthChecker.TYPE));
        } finally {
            extend.remove(BrokenHealthChecker.TYPE);
        }
    }
    
    private void givenNoneHealthCheckCluster() {
        ServiceMetadata metadata = new ServiceMetadata();
        Map<String, ClusterMetadata> clusterMap = new HashMap<>(2);
        ClusterMetadata cluster = Mockito.mock(ClusterMetadata.class);
        clusterMap.put("cluster-a", cluster);
        metadata.setClusters(clusterMap);
        when(cluster.getHealthyCheckType()).thenReturn(HealthCheckType.NONE.name());
        when(metadataManager
            .getServiceMetadata(argThat(service -> isPersistentService(service, "A", "B", "C"))))
            .thenReturn(Optional.of(metadata));
    }
    
    private boolean isPersistentService(Service service, String namespace, String groupName,
        String serviceName) {
        return null != service && namespace.equals(service.getNamespace())
            && groupName.equals(service.getGroup())
            && serviceName.equals(service.getName()) && !service.isEphemeral();
    }
    
    private abstract static class BrokenHealthChecker extends AbstractHealthChecker {
        
        private static final String TYPE = "BROKEN";
        
        private BrokenHealthChecker() {
            super(TYPE);
        }
    }
    
}
