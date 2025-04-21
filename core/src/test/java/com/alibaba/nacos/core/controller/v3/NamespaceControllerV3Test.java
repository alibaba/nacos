/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NamespaceControllerV3Test.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class NamespaceControllerV3Test {
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @InjectMocks
    private NamespaceControllerV3 namespaceControllerV3;
    
    private static final String TEST_NAMESPACE_ID = "test-namespace-id";
    
    private static final String TEST_NAMESPACE_NAME = "test-namespace-name";
    
    private static final String TEST_NAMESPACE_DESC = "test-namespace-desc";
    
    private static final int TEST_QUOTA = 200;
    
    private static final int TEST_CONFIG_COUNT = 0;
    
    @Test
    void testGetNamespaceList() {
        Namespace namespace = createTestNamespace();
        
        List<Namespace> namespaceList = Collections.singletonList(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        Result<List<Namespace>> result = namespaceControllerV3.getNamespaceList();
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ErrorCode.SUCCESS.getCode(), (int) result.getCode());
        Assertions.assertEquals(namespaceList, result.getData());
        verify(namespaceOperationService, times(1)).getNamespaceList();
    }
    
    @Test
    void testGetNamespace() throws NacosException {
        Namespace namespace = createTestNamespace();
        when(namespaceOperationService.getNamespace(TEST_NAMESPACE_ID)).thenReturn(namespace);
        
        Result<Namespace> result = namespaceControllerV3.getNamespace(TEST_NAMESPACE_ID);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ErrorCode.SUCCESS.getCode(), (int) result.getCode());
        Assertions.assertEquals(namespace, result.getData());
        verify(namespaceOperationService, times(1)).getNamespace(TEST_NAMESPACE_ID);
    }
    
    @Test
    void testCreateNamespace() throws Exception {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId(TEST_NAMESPACE_ID);
        form.setNamespaceName(TEST_NAMESPACE_NAME);
        form.setNamespaceDesc(TEST_NAMESPACE_DESC);
        
        when(namespaceOperationService.createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME,
                TEST_NAMESPACE_DESC)).thenReturn(true);
        
        Result<Boolean> result = namespaceControllerV3.createNamespace(form);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ErrorCode.SUCCESS.getCode(), (int) result.getCode());
        Assertions.assertTrue(result.getData());
        verify(namespaceOperationService, times(1)).createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME,
                TEST_NAMESPACE_DESC);
    }
    
    @Test
    void testCreateNamespaceWithInvalidNamespaceId() {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId("invalid@namespace");
        form.setNamespaceName(TEST_NAMESPACE_NAME);
        form.setNamespaceDesc(TEST_NAMESPACE_DESC);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
                () -> namespaceControllerV3.createNamespace(form));
        
        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getErrCode());
        Assertions.assertEquals("namespaceId [invalid@namespace] mismatch the pattern", exception.getErrMsg());
    }
    
    @Test
    void testUpdateNamespace() throws NacosException {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId(TEST_NAMESPACE_ID);
        form.setNamespaceName("updated-name");
        form.setNamespaceDesc("updated-desc");
        
        when(namespaceOperationService.editNamespace(TEST_NAMESPACE_ID, "updated-name", "updated-desc")).thenReturn(
                true);
        
        Result<Boolean> result = namespaceControllerV3.updateNamespace(form);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ErrorCode.SUCCESS.getCode(), (int) result.getCode());
        Assertions.assertTrue(result.getData());
        verify(namespaceOperationService, times(1)).editNamespace(TEST_NAMESPACE_ID, "updated-name", "updated-desc");
    }
    
    @Test
    void testDeleteNamespace() {
        when(namespaceOperationService.removeNamespace(TEST_NAMESPACE_ID)).thenReturn(true);
        
        Result<Boolean> result = namespaceControllerV3.deleteNamespace(TEST_NAMESPACE_ID);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ErrorCode.SUCCESS.getCode(), (int) result.getCode());
        Assertions.assertTrue(result.getData());
        verify(namespaceOperationService, times(1)).removeNamespace(TEST_NAMESPACE_ID);
    }
    
    @Test
    void testCheckNamespaceIdExist() {
        when(namespacePersistService.tenantInfoCountByTenantId(TEST_NAMESPACE_ID)).thenReturn(1);
        
        Result<Integer> result = namespaceControllerV3.checkNamespaceIdExist(TEST_NAMESPACE_ID);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ErrorCode.SUCCESS.getCode(), (int) result.getCode());
        Assertions.assertEquals(1, result.getData().intValue());
        verify(namespacePersistService, times(1)).tenantInfoCountByTenantId(TEST_NAMESPACE_ID);
    }
    
    private Namespace createTestNamespace() {
        Namespace namespace = new Namespace();
        namespace.setNamespace(TEST_NAMESPACE_ID);
        namespace.setNamespaceShowName(TEST_NAMESPACE_NAME);
        namespace.setNamespaceDesc(TEST_NAMESPACE_DESC);
        namespace.setQuota(TEST_QUOTA);
        namespace.setConfigCount(TEST_CONFIG_COUNT);
        return namespace;
    }
}