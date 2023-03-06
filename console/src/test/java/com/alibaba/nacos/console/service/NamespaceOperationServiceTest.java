/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.repository.CommonPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.console.enums.NamespaceTypeEnum;
import com.alibaba.nacos.console.model.Namespace;
import com.alibaba.nacos.console.model.NamespaceAllInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NamespaceOperationServiceTest.
 * @author dongyafei
 * @date 2022/8/16
 */
@RunWith(MockitoJUnitRunner.class)
public class NamespaceOperationServiceTest {
    
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private CommonPersistService commonPersistService;
    
    private static final String TEST_NAMESPACE_ID = "testId";
    
    private static final String TEST_NAMESPACE_NAME = "testName";
    
    private static final String TEST_NAMESPACE_DESC = "testDesc";
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final int DEFAULT_QUOTA = 200;
    
    private static final String DEFAULT_KP = "1";
    
    @Before
    public void setUp() throws Exception {
        namespaceOperationService = new NamespaceOperationService(configInfoPersistService, commonPersistService);
    }
    
    @Test
    public void testGetNamespaceList() {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setTenantId(TEST_NAMESPACE_ID);
        tenantInfo.setTenantName(TEST_NAMESPACE_NAME);
        tenantInfo.setTenantDesc(TEST_NAMESPACE_DESC);
        when(commonPersistService.findTenantByKp(DEFAULT_KP)).thenReturn(Collections.singletonList(tenantInfo));
        when(configInfoPersistService.configInfoCount(anyString())).thenReturn(1);
        
        List<Namespace> list = namespaceOperationService.getNamespaceList();
        assertEquals(2, list.size());
        Namespace namespaceA = list.get(0);
        assertEquals("", namespaceA.getNamespace());
        assertEquals(DEFAULT_NAMESPACE, namespaceA.getNamespaceShowName());
        assertEquals(DEFAULT_QUOTA, namespaceA.getQuota());
        assertEquals(1, namespaceA.getConfigCount());
        
        Namespace namespaceB = list.get(1);
        assertEquals(TEST_NAMESPACE_ID, namespaceB.getNamespace());
        assertEquals(TEST_NAMESPACE_NAME, namespaceB.getNamespaceShowName());
        assertEquals(1, namespaceB.getConfigCount());
    }
    
    @Test(expected = NacosApiException.class)
    public void testGetNamespace() throws NacosException {
    
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setTenantId(TEST_NAMESPACE_ID);
        tenantInfo.setTenantName(TEST_NAMESPACE_NAME);
        tenantInfo.setTenantDesc(TEST_NAMESPACE_DESC);
        when(commonPersistService.findTenantByKp(DEFAULT_KP, TEST_NAMESPACE_ID)).thenReturn(tenantInfo);
        when(commonPersistService.findTenantByKp(DEFAULT_KP, "test_not_exist_id")).thenReturn(null);
        when(configInfoPersistService.configInfoCount(anyString())).thenReturn(1);
        NamespaceAllInfo namespaceAllInfo = new NamespaceAllInfo(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, DEFAULT_QUOTA,
                1, NamespaceTypeEnum.GLOBAL.getType(), TEST_NAMESPACE_DESC);
        NamespaceAllInfo namespace = namespaceOperationService.getNamespace(TEST_NAMESPACE_ID);
        assertEquals(namespaceAllInfo.getNamespace(), namespace.getNamespace());
        assertEquals(namespaceAllInfo.getNamespaceShowName(), namespace.getNamespaceShowName());
        assertEquals(namespaceAllInfo.getNamespaceDesc(), namespace.getNamespaceDesc());
        assertEquals(namespaceAllInfo.getQuota(), namespace.getQuota());
        assertEquals(namespaceAllInfo.getConfigCount(), namespace.getConfigCount());
    
        namespaceOperationService.getNamespace("test_not_exist_id");
        
    }
    
    @Test
    public void testCreateNamespace() throws NacosException {
        when(commonPersistService.tenantInfoCountByTenantId(anyString())).thenReturn(0);
        namespaceOperationService.createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        verify(commonPersistService)
                .insertTenantInfoAtomic(eq(DEFAULT_KP), eq(TEST_NAMESPACE_ID), eq(TEST_NAMESPACE_NAME), eq(TEST_NAMESPACE_DESC),
                        any(), anyLong());
    }
    
    @Test
    public void testEditNamespace() {
        namespaceOperationService.editNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        verify(commonPersistService).updateTenantNameAtomic(DEFAULT_KP, TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
    }
    
    @Test
    public void testRemoveNamespace() {
        namespaceOperationService.removeNamespace(TEST_NAMESPACE_ID);
        verify(commonPersistService).removeTenantInfoAtomic(DEFAULT_KP, TEST_NAMESPACE_ID);
    }
}
