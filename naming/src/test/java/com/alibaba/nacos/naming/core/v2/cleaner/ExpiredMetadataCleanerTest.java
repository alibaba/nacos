/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.cleaner;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ExpiredMetadataInfo;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredMetadataCleanerTest {
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 8848;
    
    private static final String CLUSTER = "DEFAULT";
    
    private static final String METADATA_ID = InstancePublishInfo.genMetadataId(IP, PORT, CLUSTER);
    
    private static final Service SERVICE = Service.newService("public", "DEFAULT_GROUP", "test.1");
    
    private ExpiredMetadataCleaner expiredMetadataCleaner;
    
    @Mock
    private NamingMetadataManager metadataManagerMock;
    
    @Mock
    private NamingMetadataOperateService metadataOperateServiceMock;
    
    private Set<ExpiredMetadataInfo> set = new ConcurrentHashSet<>();
    
    @Mock
    private ExpiredMetadataInfo expiredMetadataInfoMock;
    
    @Mock
    private ServiceStorage serviceStorageMock;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new MockEnvironment());
        expiredMetadataCleaner = new ExpiredMetadataCleaner(metadataManagerMock,
            metadataOperateServiceMock, serviceStorageMock);
        
        set.add(expiredMetadataInfoMock);
        
        Mockito.lenient().when(metadataManagerMock.getExpiredMetadataInfos()).thenReturn(set);
        Mockito.lenient().when(expiredMetadataInfoMock.getCreateTime()).thenReturn(0L);
    }
    
    @Test
    void testGetType() {
        assertEquals("expiredMetadata", expiredMetadataCleaner.getType());
    }
    
    @Test
    void testDoClean() {
        when(metadataManagerMock.containServiceMetadata(expiredMetadataInfoMock.getService()))
            .thenReturn(true);
        
        expiredMetadataCleaner.doClean();
        
        verify(metadataManagerMock).getExpiredMetadataInfos();
        verify(metadataOperateServiceMock)
            .deleteServiceMetadata(expiredMetadataInfoMock.getService());
    }
    
    @Test
    void testDoCleanSkipsUnexpiredMetadata() {
        when(expiredMetadataInfoMock.getCreateTime()).thenReturn(System.currentTimeMillis());
        
        expiredMetadataCleaner.doClean();
        
        verify(metadataOperateServiceMock, never()).deleteServiceMetadata(any());
        verify(metadataOperateServiceMock, never()).deleteInstanceMetadata(any(), any());
    }
    
    @Test
    void testDoCleanRemovesInstanceMetadata() {
        when(expiredMetadataInfoMock.getMetadataId()).thenReturn(METADATA_ID);
        when(serviceStorageMock.getPushData(any())).thenReturn(serviceInfoWithHosts());
        when(metadataManagerMock.containInstanceMetadata(expiredMetadataInfoMock.getService(),
            METADATA_ID)).thenReturn(true);
        
        expiredMetadataCleaner.doClean();
        
        verify(metadataOperateServiceMock)
            .deleteInstanceMetadata(expiredMetadataInfoMock.getService(), METADATA_ID);
    }
    
    @Test
    void testDoCleanKeepsInstanceMetadataWhenInstanceStillRegistered() {
        // Arrange: the owning client disconnected, but the same instance was
        // re-registered by another client, so it is still present in the service.
        when(expiredMetadataInfoMock.getService()).thenReturn(SERVICE);
        when(expiredMetadataInfoMock.getMetadataId()).thenReturn(METADATA_ID);
        when(serviceStorageMock.getPushData(SERVICE))
            .thenReturn(serviceInfoWithHosts(instance(IP, PORT, CLUSTER)));
        Mockito.lenient()
            .when(metadataManagerMock.containInstanceMetadata(SERVICE, METADATA_ID))
            .thenReturn(true);
        
        // Act
        expiredMetadataCleaner.doClean();
        
        // Assert
        verify(metadataOperateServiceMock, never()).deleteInstanceMetadata(any(), any());
    }
    
    @Test
    void testDoCleanUntracksExpiredInfoWhenInstanceStillRegistered() {
        when(expiredMetadataInfoMock.getService()).thenReturn(SERVICE);
        when(expiredMetadataInfoMock.getMetadataId()).thenReturn(METADATA_ID);
        when(serviceStorageMock.getPushData(SERVICE))
            .thenReturn(serviceInfoWithHosts(instance(IP, PORT, CLUSTER)));
        assertTrue(set.contains(expiredMetadataInfoMock));
        
        expiredMetadataCleaner.doClean();
        
        assertFalse(set.contains(expiredMetadataInfoMock));
    }
    
    @Test
    void testDoCleanRemovesInstanceMetadataWhenOnlyAnotherInstanceRemains() {
        when(expiredMetadataInfoMock.getService()).thenReturn(SERVICE);
        when(expiredMetadataInfoMock.getMetadataId()).thenReturn(METADATA_ID);
        when(serviceStorageMock.getPushData(SERVICE))
            .thenReturn(serviceInfoWithHosts(instance(IP, PORT + 1, CLUSTER)));
        when(metadataManagerMock.containInstanceMetadata(SERVICE, METADATA_ID)).thenReturn(true);
        
        expiredMetadataCleaner.doClean();
        
        verify(metadataOperateServiceMock).deleteInstanceMetadata(SERVICE, METADATA_ID);
    }
    
    private static ServiceInfo serviceInfoWithHosts(Instance... hosts) {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(hosts.length == 0 ? Collections.emptyList() : List.of(hosts));
        return serviceInfo;
    }
    
    private static Instance instance(String ip, int port, String cluster) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setClusterName(cluster);
        return instance;
    }
}
