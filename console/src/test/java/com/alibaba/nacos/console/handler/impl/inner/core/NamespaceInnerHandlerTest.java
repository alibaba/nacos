/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.inner.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceInnerHandlerTest {
    
    private static final String NAMESPACE_ID = "namespaceId";
    
    private static final String NAMESPACE_NAME = "namespaceName";
    
    private static final String NAMESPACE_DESC = "namespaceDesc";
    
    @Mock
    NamespaceOperationService namespaceOperationService;
    
    NamespaceInnerHandler namespaceInnerHandler;
    
    @BeforeEach
    void setUp() {
        namespaceInnerHandler = new NamespaceInnerHandler(namespaceOperationService);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getNamespaceList() {
        List<Namespace> mockNamespaceList = Collections.singletonList(new Namespace());
        when(namespaceOperationService.getNamespaceList()).thenReturn(mockNamespaceList);
        List<Namespace> actual = namespaceInnerHandler.getNamespaceList();
        assertEquals(mockNamespaceList, actual);
    }
    
    @Test
    void getNamespaceDetail() throws NacosException {
        Namespace mockNamespace = new Namespace();
        when(namespaceOperationService.getNamespace(NAMESPACE_ID)).thenReturn(mockNamespace);
        Namespace actual = namespaceInnerHandler.getNamespaceDetail(NAMESPACE_ID);
        assertEquals(mockNamespace, actual);
    }
    
    @Test
    void createNamespace() throws NacosException {
        when(namespaceOperationService.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC)).thenReturn(true);
        assertTrue(namespaceInnerHandler.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC));
    }
    
    @Test
    void updateNamespace() throws NacosException {
        NamespaceForm namespaceForm = new NamespaceForm();
        namespaceForm.setNamespaceId(NAMESPACE_ID);
        namespaceForm.setNamespaceName(NAMESPACE_NAME);
        namespaceForm.setNamespaceDesc(NAMESPACE_DESC);
        when(namespaceOperationService.editNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC)).thenReturn(true);
        assertTrue(namespaceInnerHandler.updateNamespace(namespaceForm));
    }
    
    @Test
    void deleteNamespace() throws NacosException {
        when(namespaceOperationService.removeNamespace(NAMESPACE_ID)).thenReturn(true);
        assertTrue(namespaceInnerHandler.deleteNamespace(NAMESPACE_ID));
    }
    
    @Test
    void checkNamespaceIdExist() {
        when(namespaceOperationService.namespaceExists(NAMESPACE_ID)).thenReturn(true);
        assertTrue(namespaceInnerHandler.checkNamespaceIdExist(NAMESPACE_ID));
    }
}