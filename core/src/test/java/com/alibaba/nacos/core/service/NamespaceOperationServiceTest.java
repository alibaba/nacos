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

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.core.namespace.injector.AbstractNamespaceDetailInjector;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.namespace.model.NamespaceTypeEnum;
import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

/**
 * NamespaceOperationServiceTest.
 *
 * @author dongyafei
 * @date 2022/8/16
 */
@ExtendWith(MockitoExtension.class)
class NamespaceOperationServiceTest {
    
    private static final String TEST_NAMESPACE_ID = "testId";
    
    private static final String TEST_NAMESPACE_NAME = "testName";
    
    private static final String TEST_NAMESPACE_DESC = "testDesc";
    
    private static final String DEFAULT_NAMESPACE_SHOW_NAME = "public";
    
    private static final String DEFAULT_NAMESPACE_DESCRIPTION = "Default Namespace";
    
    private static final int DEFAULT_QUOTA = 200;
    
    private static final String DEFAULT_KP = "1";
    
    private NamespaceOperationService namespaceOperationService;
    
    private MockNamespaceInjector injector;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        injector = new MockNamespaceInjector();
        namespaceOperationService = new NamespaceOperationService(namespacePersistService);
    }
    
    @AfterEach
    void tearDown() {
        injector.doInjector = false;
    }
    
    @Test
    void testGetNamespaceList() {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setTenantId(TEST_NAMESPACE_ID);
        tenantInfo.setTenantName(TEST_NAMESPACE_NAME);
        tenantInfo.setTenantDesc(TEST_NAMESPACE_DESC);
        when(namespacePersistService.findTenantByKp(DEFAULT_KP)).thenReturn(Collections.singletonList(tenantInfo));
        
        List<Namespace> list = namespaceOperationService.getNamespaceList();
        assertEquals(2, list.size());
        Namespace namespaceA = list.get(0);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, namespaceA.getNamespace());
        assertEquals(DEFAULT_NAMESPACE_SHOW_NAME, namespaceA.getNamespaceShowName());
        assertEquals(DEFAULT_NAMESPACE_DESCRIPTION, namespaceA.getNamespaceDesc());
        assertEquals(DEFAULT_QUOTA, namespaceA.getQuota());
        assertEquals(1, namespaceA.getConfigCount());
        
        Namespace namespaceB = list.get(1);
        assertEquals(TEST_NAMESPACE_ID, namespaceB.getNamespace());
        assertEquals(TEST_NAMESPACE_NAME, namespaceB.getNamespaceShowName());
        assertEquals(1, namespaceB.getConfigCount());
    }
    
    @Test
    void testGetNamespace() {
        assertThrows(NacosApiException.class, () -> {
            
            TenantInfo tenantInfo = new TenantInfo();
            tenantInfo.setTenantId(TEST_NAMESPACE_ID);
            tenantInfo.setTenantName(TEST_NAMESPACE_NAME);
            tenantInfo.setTenantDesc(TEST_NAMESPACE_DESC);
            when(namespacePersistService.findTenantByKp(DEFAULT_KP, TEST_NAMESPACE_ID)).thenReturn(tenantInfo);
            when(namespacePersistService.findTenantByKp(DEFAULT_KP, "test_not_exist_id")).thenReturn(null);
            Namespace namespaceAllInfo = new Namespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC,
                    DEFAULT_QUOTA, 1, NamespaceTypeEnum.GLOBAL.getType());
            Namespace namespace = namespaceOperationService.getNamespace(TEST_NAMESPACE_ID);
            assertEquals(namespaceAllInfo.getNamespace(), namespace.getNamespace());
            assertEquals(namespaceAllInfo.getNamespaceShowName(), namespace.getNamespaceShowName());
            assertEquals(namespaceAllInfo.getNamespaceDesc(), namespace.getNamespaceDesc());
            assertEquals(namespaceAllInfo.getQuota(), namespace.getQuota());
            assertEquals(namespaceAllInfo.getConfigCount(), namespace.getConfigCount());
            
            namespaceOperationService.getNamespace("test_not_exist_id");
            
        });
        
    }
    
    @Test
    void testCreateNamespace() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId(anyString())).thenReturn(0);
        namespaceOperationService.createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        verify(namespacePersistService).insertTenantInfoAtomic(eq(DEFAULT_KP), eq(TEST_NAMESPACE_ID),
                eq(TEST_NAMESPACE_NAME), eq(TEST_NAMESPACE_DESC), any(), anyLong());
    }
    
    @Test
    void testCreateNamespaceForDefaultNamespace() throws NacosException {
        assertThrows(NacosApiException.class,
                () -> namespaceOperationService.createNamespace(NamespaceUtil.getNamespaceDefaultId(),
                        TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC));
    }
    
    @Test
    void testEditNamespace() {
        namespaceOperationService.editNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        verify(namespacePersistService).updateTenantNameAtomic(DEFAULT_KP, TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME,
                TEST_NAMESPACE_DESC);
    }
    
    @Test
    void testRemoveNamespace() {
        namespaceOperationService.removeNamespace(TEST_NAMESPACE_ID);
        verify(namespacePersistService).removeTenantInfoAtomic(DEFAULT_KP, TEST_NAMESPACE_ID);
    }

    @Test
    void testNamespaceExistValidationWithException() {

        String namespaceId = "test-namespace";

        // 模拟底层持久层查询时抛出异常（如数据库连接失败）
        when(namespacePersistService.tenantInfoCountByTenantId(namespaceId))
                .thenThrow(new RuntimeException("Database connection lost"));

        // Act
        boolean result = namespaceOperationService.namespaceExists(namespaceId);

        // Assert
        // 根据方法实现，异常被捕获，应返回 false
        assertFalse(result, "namespaceExists should return false when database query fails");
    }

    @Test
    void testNamespaceExistValidationWithDefaultNamespace() {
        // 测试默认命名空间
        String namespaceId = NamespaceUtil.getNamespaceDefaultId();
        // 执行
        boolean result = namespaceOperationService.namespaceExists(namespaceId);
        // 验证
        Assertions.assertTrue(result, "namespaceExists should return true for default namespace");
    }

    @Test
    void testNamespaceExistValidationWithoutDefaultNamespaceButExist() {
        try (MockedStatic<NamespaceUtil> mockedUtil = mockStatic(NamespaceUtil.class)) {

            // 设置 mock 行为
            mockedUtil.when(() -> NamespaceUtil.isDefaultNamespaceId("test-namespace"))
                    .thenReturn(false);

            // mock 其他依赖
            when(namespacePersistService.tenantInfoCountByTenantId("test-namespace")).thenReturn(1);

            // 执行
            boolean result = namespaceOperationService.namespaceExists("test-namespace");

            // 验证
            Assertions.assertTrue(result);
        }
    }

    private static class MockNamespaceInjector extends AbstractNamespaceDetailInjector {
        
        private boolean doInjector = true;
        
        @Override
        public void injectDetail(Namespace namespace) {
            if (doInjector) {
                namespace.setConfigCount(1);
            }
        }
    }
}
