/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.namespace.model.Namespace;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceControllerTest {
    
    @InjectMocks
    private NamespaceController namespaceController;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @BeforeEach
    void setUp() {
    
    }
    
    @Test
    void testGetNamespaces() throws Exception {
        Namespace namespace = new Namespace("", "public");
        when(namespaceOperationService.getNamespaceList()).thenReturn(Collections.singletonList(namespace));
        RestResult<List<Namespace>> actual = namespaceController.getNamespaces();
        assertTrue(actual.ok());
        assertEquals(200, actual.getCode());
        assertEquals(namespace, actual.getData().get(0));
    }
    
    @Test
    void testGetNamespaceByNamespaceId() throws Exception {
        Namespace namespace = new Namespace("", "public", "", 0, 0, 0);
        when(namespaceOperationService.getNamespace("")).thenReturn(namespace);
        assertEquals(namespace, namespaceController.getNamespace(""));
    }
    
    @Test
    void testCreateNamespaceWithCustomId() throws Exception {
        namespaceController.createNamespace("test-Id", "testName", "testDesc");
        verify(namespaceOperationService).createNamespace("test-Id", "testName", "testDesc");
    }
    
    @Test
    void testCreateNamespaceWithIllegalName() {
        assertFalse(namespaceController.createNamespace(null, "test@Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test#Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test$Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test%Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test^Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test&Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test*Name", "testDesc"));
    }
    
    @Test
    void testCreateNamespaceWithNonUniqueId() throws Exception {
        when(namespacePersistService.tenantInfoCountByTenantId("test-Id")).thenReturn(1);
        assertFalse(namespaceController.createNamespace("test-Id", "testNam2", "testDesc"));
    }
    
    @Test
    void testCreateNamespaceWithIllegalCustomId() throws Exception {
        assertFalse(namespaceController.createNamespace("test.Id", "testName", "testDesc"));
        verify(namespaceOperationService, never()).createNamespace("test.Id", "testName", "testDesc");
    }
    
    @Test
    void testCreateNamespaceWithLongCustomId() throws Exception {
        StringBuilder longId = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            longId.append("a");
        }
        assertFalse(namespaceController.createNamespace(longId.toString(), "testName", "testDesc"));
        verify(namespaceOperationService, never()).createNamespace(longId.toString(), "testName", "testDesc");
    }
    
    @Test
    void testCreateNamespaceWithAutoId() throws Exception {
        assertFalse(namespaceController.createNamespace("", "testName", "testDesc"));
        verify(namespaceOperationService).createNamespace(
                matches("[A-Za-z\\d]{8}-[A-Za-z\\d]{4}-[A-Za-z\\d]{4}-[A-Za-z\\d]{4}-[A-Za-z\\d]{12}"), eq("testName"), eq("testDesc"));
    }
    
    @Test
    void testCreateNamespaceFailure() throws NacosException {
        when(namespaceOperationService.createNamespace(anyString(), anyString(), anyString())).thenThrow(new NacosException(500, "test"));
        assertFalse(namespaceController.createNamespace("", "testName", "testDesc"));
    }
    
    @Test
    void testCheckNamespaceIdExist() throws Exception {
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        when(namespacePersistService.tenantInfoCountByTenantId("123")).thenReturn(0);
        assertFalse(namespaceController.checkNamespaceIdExist(""));
        assertTrue(namespaceController.checkNamespaceIdExist("public"));
        assertFalse(namespaceController.checkNamespaceIdExist("123"));
    }
    
    @Test
    void testEditNamespace() {
        namespaceController.editNamespace("test-Id", "testName", "testDesc");
        verify(namespaceOperationService).editNamespace("test-Id", "testName", "testDesc");
    }
    
    @Test
    void testEditNamespaceWithIllegalName() {
        assertFalse(namespaceController.createNamespace(null, "test@Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test#Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test$Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test%Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test^Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test&Name", "testDesc"));
        assertFalse(namespaceController.createNamespace(null, "test*Name", "testDesc"));
    }
    
    @Test
    void deleteConfig() throws Exception {
        namespaceController.deleteNamespace("test-Id");
        verify(namespaceOperationService).removeNamespace("test-Id");
    }
}
