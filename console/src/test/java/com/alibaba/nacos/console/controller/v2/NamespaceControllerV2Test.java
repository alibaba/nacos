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

package com.alibaba.nacos.console.controller.v2;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.namespace.model.Namespace;
import com.alibaba.nacos.core.namespace.model.NamespaceTypeEnum;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NamespaceControllerV2Test.
 *
 * @author dongyafei
 * @date 2022/8/16
 */
@ExtendWith(MockitoExtension.class)
class NamespaceControllerV2Test {
    
    private static final String TEST_NAMESPACE_ID = "testId";
    
    private static final String TEST_NAMESPACE_NAME = "testName";
    
    private static final String TEST_NAMESPACE_DESC = "testDesc";
    
    private NamespaceControllerV2 namespaceControllerV2;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        namespaceControllerV2 = new NamespaceControllerV2(namespaceOperationService, namespacePersistService);
    }
    
    @Test
    void testGetNamespaceList() {
        Namespace namespace = new Namespace();
        namespace.setNamespace(TEST_NAMESPACE_ID);
        namespace.setNamespaceShowName(TEST_NAMESPACE_NAME);
        namespace.setNamespaceDesc(TEST_NAMESPACE_DESC);
        List<Namespace> namespaceList = Collections.singletonList(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        Result<List<Namespace>> actualResult = namespaceControllerV2.getNamespaceList();
        verify(namespaceOperationService).getNamespaceList();
        assertEquals(ErrorCode.SUCCESS.getCode(), actualResult.getCode());
        
        List<Namespace> actualList = actualResult.getData();
        Namespace actualNamespace = actualList.get(0);
        assertEquals(namespaceList.size(), actualList.size());
        assertEquals(namespace.getNamespace(), actualNamespace.getNamespace());
        assertEquals(namespace.getNamespaceShowName(), actualNamespace.getNamespaceShowName());
        assertEquals(namespace.getNamespaceDesc(), actualNamespace.getNamespaceDesc());
        
    }
    
    @Test
    void testGetNamespace() throws NacosException {
        Namespace namespaceAllInfo = new Namespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC, 200, 1,
                NamespaceTypeEnum.GLOBAL.getType());
        when(namespaceOperationService.getNamespace(TEST_NAMESPACE_ID)).thenReturn(namespaceAllInfo);
        Result<Namespace> result = namespaceControllerV2.getNamespace(TEST_NAMESPACE_ID);
        verify(namespaceOperationService).getNamespace(TEST_NAMESPACE_ID);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        Namespace namespace = result.getData();
        assertEquals(namespaceAllInfo.getNamespace(), namespace.getNamespace());
        assertEquals(namespaceAllInfo.getNamespaceShowName(), namespace.getNamespaceShowName());
        assertEquals(namespaceAllInfo.getNamespaceDesc(), namespace.getNamespaceDesc());
        assertEquals(namespaceAllInfo.getQuota(), namespace.getQuota());
        assertEquals(namespaceAllInfo.getConfigCount(), namespace.getConfigCount());
    }
    
    @Test
    void testCreateNamespace() throws NacosException {
        when(namespaceOperationService.createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC)).thenReturn(true);
        Result<Boolean> result = namespaceControllerV2.createNamespace(
                new NamespaceForm(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC));
        
        verify(namespaceOperationService).createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
    
    @Test
    void testCreateNamespaceWithIllegalName() {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceDesc("testDesc");
        form.setNamespaceName("test@Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test$Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test#Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test%Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test^Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test&Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test*Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
    }
    
    @Test
    void testCreateNamespaceWithNonUniqueId() {
        when(namespacePersistService.tenantInfoCountByTenantId("test-id")).thenReturn(1);
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId("test-id");
        form.setNamespaceDesc("testDesc");
        form.setNamespaceName("testName");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
    }
    
    @Test
    void testEditNamespace() throws NacosException {
        when(namespaceOperationService.editNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC)).thenReturn(true);
        Result<Boolean> result = namespaceControllerV2.editNamespace(
                new NamespaceForm(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC));
        
        verify(namespaceOperationService).editNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
    
    @Test
    void testEditNamespaceWithIllegalName() {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId("test-id");
        form.setNamespaceDesc("testDesc");
        
        form.setNamespaceName("test@Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test#Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test$Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test%Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test^Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test&Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
        
        form.setNamespaceName("test*Name");
        assertThrows(NacosException.class, () -> namespaceControllerV2.createNamespace(form));
    }
    
    @Test
    void testDeleteNamespace() {
        when(namespaceOperationService.removeNamespace(TEST_NAMESPACE_ID)).thenReturn(true);
        Result<Boolean> result = namespaceControllerV2.deleteNamespace(TEST_NAMESPACE_ID);
        
        verify(namespaceOperationService).removeNamespace(TEST_NAMESPACE_ID);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
}
