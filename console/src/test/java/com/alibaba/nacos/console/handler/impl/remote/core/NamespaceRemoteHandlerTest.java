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

package com.alibaba.nacos.console.handler.impl.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class NamespaceRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    private static final String NAMESPACE_ID = "namespaceId";
    
    private static final String NAMESPACE_NAME = "namespaceName";
    
    private static final String NAMESPACE_DESC = "namespaceDesc";
    
    NamespaceRemoteHandler namespaceRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        namespaceRemoteHandler = new NamespaceRemoteHandler(clientHolder);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getNamespaceList() throws NacosException {
        List<Namespace> mockNamespaceList = Collections.singletonList(new Namespace());
        when(namingMaintainerService.getNamespaceList()).thenReturn(mockNamespaceList);
        List<Namespace> actual = namespaceRemoteHandler.getNamespaceList();
        assertEquals(mockNamespaceList, actual);
    }
    
    @Test
    void getNamespaceDetail() throws NacosException {
        Namespace mockNamespace = new Namespace();
        when(namingMaintainerService.getNamespace(NAMESPACE_ID)).thenReturn(mockNamespace);
        Namespace actual = namespaceRemoteHandler.getNamespaceDetail(NAMESPACE_ID);
        assertEquals(mockNamespace, actual);
    }
    
    @Test
    void createNamespace() throws NacosException {
        when(namingMaintainerService.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC)).thenReturn(true);
        assertTrue(namespaceRemoteHandler.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC));
    }
    
    @Test
    void updateNamespace() throws NacosException {
        NamespaceForm namespaceForm = new NamespaceForm();
        namespaceForm.setNamespaceId(NAMESPACE_ID);
        namespaceForm.setNamespaceName(NAMESPACE_NAME);
        namespaceForm.setNamespaceDesc(NAMESPACE_DESC);
        when(namingMaintainerService.updateNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC)).thenReturn(true);
        assertTrue(namespaceRemoteHandler.updateNamespace(namespaceForm));
    }
    
    @Test
    void deleteNamespace() throws NacosException {
        when(namingMaintainerService.deleteNamespace(NAMESPACE_ID)).thenReturn(true);
        assertTrue(namespaceRemoteHandler.deleteNamespace(NAMESPACE_ID));
    }
    
    @Test
    void checkNamespaceIdExist() throws NacosException {
        when(namingMaintainerService.checkNamespaceIdExist(NAMESPACE_ID)).thenReturn(true);
        assertTrue(namespaceRemoteHandler.checkNamespaceIdExist(NAMESPACE_ID));
    }
}