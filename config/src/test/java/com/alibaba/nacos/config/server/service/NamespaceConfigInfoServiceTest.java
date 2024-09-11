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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.service.capacity.TenantCapacityPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.namespace.model.Namespace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class NamespaceConfigInfoServiceTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private TenantCapacityPersistService tenantCapacityPersistService;
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    @BeforeEach
    void setUp() throws Exception {
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        
    }
    
    @AfterEach
    void after() throws Exception {
        propertyUtilMockedStatic.close();
    }
    
    @Test
    public void testInjectDetailNotDefault() {
        
        String namespaceId = "test1234";
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setQuota(1023);
        
        when(tenantCapacityPersistService.getTenantCapacity(namespaceId)).thenReturn(tenantCapacity);
        when(configInfoPersistService.configInfoCount(namespaceId)).thenReturn(101);
        Namespace namespace = new Namespace(namespaceId, "test123ShowName");
        NamespaceConfigInfoService namespaceConfigInfoService = new NamespaceConfigInfoService(configInfoPersistService,
                tenantCapacityPersistService);
        namespaceConfigInfoService.injectDetail(namespace);
        assertEquals(101, namespace.getConfigCount());
        assertEquals(1023, namespace.getQuota());
        
    }
    
    @Test
    public void testInjectDetailDefaultQuota() {
        
        String namespaceId = "test1234";
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setQuota(0);
        when(tenantCapacityPersistService.getTenantCapacity(namespaceId)).thenReturn(tenantCapacity);
        when(configInfoPersistService.configInfoCount(namespaceId)).thenReturn(105);
        
        when(PropertyUtil.getDefaultTenantQuota()).thenReturn(1025);
        Namespace namespace = new Namespace(namespaceId, "test123ShowName");
        NamespaceConfigInfoService namespaceConfigInfoService = new NamespaceConfigInfoService(configInfoPersistService,
                tenantCapacityPersistService);
        namespaceConfigInfoService.injectDetail(namespace);
        
        assertEquals(105, namespace.getConfigCount());
        assertEquals(1025, namespace.getQuota());
    }
    
}
